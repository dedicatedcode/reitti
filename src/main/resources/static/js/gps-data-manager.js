class GpsDataManager {
    constructor(userConfig, timeZone) {
        this.config = userConfig;
        this.color = this._hexToRgb(userConfig.color || '#3388ff');
        this.id = userConfig.id || 'default';

        this.timeZone = timeZone || "UTC";

        // Offset Caching to keep the loop fast
        this._currentOffset = 0;
        this._lastOffsetCheckTs = -Infinity;
        // 1. Buffers
        //Memory Layout for buffer and cleaned buffer is [Lng, Lat, Alt, LinTs, Day, AggTs]
        this.buffer = new Float32Array(16000 * 6);
        this.cleanedBuffer = new Float32Array(16000 * 6);
        this.snappedBuffer = null;
        this.snappedVersion = 0;

        // 2. State
        this.cursor = 0;
        this.cleanedCursor = 0;
        this.minTimestamp = null;
        this.maxTimestamp = null;
        this.totalExpected = 0;
        this.bounds = null;
        this._dataCache = {};
    }

    /**
     * Range-Aware Load
     * @param {number} startUTC - Unix timestamp
     * @param {number} endUTC - Unix timestamp
     * @param {Function} onProgress - Progress callback
     */
    async load(startUTC, endUTC, onProgress) {
        // 1. Check if we already have this data in memory
        this._dataCache = {};
        if (this.loadingState === 'complete' &&
            startUTC >= this.minTimestamp &&
            endUTC <= this.maxTimestamp) {

            console.log("Range already in memory. Skipping fetch.");
            if (onProgress) onProgress(this.cursor, this.totalExpected, 'complete');
            return;
        }

        try {
            this.loadingState = 'metadata';

            if (onProgress) onProgress(this.cursor, this.totalExpected, this.loadingState);
            const [metaRes, visitsRes] = await Promise.all([
                fetch(window.contextPath + this.config.map.metaDataUrl),
                fetch(window.contextPath + this.config.map.visitsUrl)
            ]);

            const meta = await metaRes.json();
            const receivedVisits = await visitsRes.json();
            this.visits = receivedVisits.places.map(p => ({
                id: p.place.id,
                coordinates: [p.lng, p.lat],
                polygon: p.place.polygon,
                totalDurationSec: p.totalDurationMs / 1000,
                name: p.place.name,
                activeRanges: p.visits.map(v => {
                    const startUtcSeconds = Math.floor(new Date(v.startTime).getTime() / 1000);
                    const startOffset = this._getOffsetSeconds(startUtcSeconds);
                    const endUtcSeconds = Math.floor(new Date(v.endTime).getTime() / 1000);
                    const endOffset = this._getOffsetSeconds(endUtcSeconds);
                    const localStartTs = startUtcSeconds + startOffset;
                    const localEndTs = endUtcSeconds + endOffset;
                    return ({
                        start: startUtcSeconds,
                        end: endUtcSeconds,
                        startAggregate: ((localStartTs % 86400) + 86400) % 86400,
                        endAggregate: ((localEndTs % 86400) + 86400) % 86400,
                        startDayOfWeek: Math.pow(new Date(localStartTs * 1000).getUTCDay(), 2),
                        endDayOfWeek: Math.pow(new Date(localEndTs * 1000).getUTCDay(), 2)
                    });
                }).sort((a, b) => a.start - b.start),
                originalVisits: p.visits
            }));
            // Update internal range trackers
            this.minTimestamp = startUTC;
            this.maxTimestamp = endUTC;
            this.totalExpected = meta.totalPoints;
            this.bounds = [meta.minLng, meta.minLat, meta.maxLng, meta.maxLat];
            // Clear old data to make room for the new range
            this.cursor = 0;
            this.cleanedCursor = 0;
            this.snappedBuffer = null;

            this.loadingState = 'streaming';
            await this._streamPoints(onProgress);

            this.loadingState = 'bundling';
            await this._generateBundledPath(onProgress);

            this.loadingState = 'complete';
            if (onProgress) onProgress(this.totalExpected, this.totalExpected, 'complete');
            console.log("Load complete:", this.loadingState, 'with bounds:', this.bounds, 'and total points:', this.totalExpected, 'in range:', startUTC, 'to', endUTC, 'at', this.visits.length, 'places.');

        } catch (error) {
            console.error("Load failed:", error);
            this.loadingState = 'error';
        }
    }

    /**
     * Returns optimized binary attributes for deck.gl layers.
     * @param {string} mode - 'raw', 'cleaned', or 'bundled'
     * @param {boolean} isAggregate - Whether to use 24h clock or linear time
     * @param {Object} rangeIndices - Optional {start, count} from binary search
     */
    getLayerData(mode, isAggregate = false, rangeIndices = null) {
        // 1. Generate a unique cache key based on all inputs
        // We also include the current cursor to invalidate the cache when data is added
        const rangeKey = rangeIndices ? `${rangeIndices.start}-${rangeIndices.count}` : 'full';
        const cacheKey = `${mode}-${isAggregate}-${rangeKey}`;
        const currentPointCount = mode === 'raw' ? this.cursor : this.cleanedCursor;

        // 2. Return cached object if nothing has changed
        if (this._dataCache[cacheKey] && this._dataCache[cacheKey].version === currentPointCount) {
            return this._dataCache[cacheKey].payload;
        }

        const dayOffset = 16;

        let payload;

        if (mode === 'bundled') {
            const stride = 20;
            payload = {
                length: 1,
                startIndices: new Uint32Array([0, this.cleanedCursor]),
                attributes: {
                    getPath: {
                        value: this.snappedBuffer,
                        size: 2,
                        stride: stride,
                        offset: 0
                    },
                    getTimestamps: {
                        value: this.snappedBuffer,
                        size: 1,
                        stride: stride,
                        offset: isAggregate ? 16 : 8
                    },
                    filterValues: {
                        value: this.snappedBuffer,
                        size: 1,
                        stride: stride,
                        offset: 12
                    }
                }
            };
        } else if (mode === 'cleaned') {
            payload = {
                length: 1,
                startIndices: new Uint32Array([0, this.cleanedCursor]),
                attributes: {
                    getPath: {
                        value: this.cleanedBuffer,
                        size: 3,
                        stride: 24,
                        offset: 0
                    },
                    getTimestamps: {
                        value: this.cleanedBuffer,
                        size: 1,
                        stride: 24,
                        offset: isAggregate ? 20 : 12
                    },
                    filterValues: {
                        value: this.cleanedBuffer,
                        size: 1,
                        stride: 24,
                        offset: dayOffset
                    }
                }
            };
        } else if (mode === 'raw') {
            payload = {
                length: 1,
                startIndices: new Uint32Array([0, this.cursor]),
                attributes: {
                    getPath: {
                        value: this.buffer,
                        size: 3,
                        stride: 24,
                        offset: 0
                    },
                    getTimestamps: {
                        value: this.buffer,
                        size: 1,
                        stride: 24,
                        offset: isAggregate ? 20 : 12
                    },
                    filterValues: {
                        value: this.buffer,
                        size: 1,
                        stride: 24,
                        offset: dayOffset
                    }
                }
            };
        }
        // 3. Store in cache
        this._dataCache[cacheKey] = {
            version: currentPointCount,
            payload: payload
        };

        return payload;
    }

    recalculateBundledPath(precisionValue = 0.0005, weight = 0.5) {
        return this._generateBundledPath(null, precisionValue, weight);
    }

    updateSelectionMask(selectedBitmaskSum) {
        const count = this.cursor;
        const buf = this.buffer;
        const mask = this.selectionBuffer;

        for (let i = 0; i < count; i++) {
            const pointBit = buf[i * 6 + 4];
            // Standard bitwise check: Does this point's day exist in the selection?
            mask[i] = (pointBit & selectedBitmaskSum) ? 1.0 : 0.0;
        }
        this.selectionVersion++;
    }

    async _streamPoints(onProgress) {
        const response = await fetch(window.contextPath + this.config.map.streamUrl);
        const reader = response.body.getReader();
        let leftover = null;

        while (true) {
            const {done, value} = await reader.read();
            if (done) break;

            let combinedValue = value;
            if (leftover) {
                combinedValue = new Uint8Array(leftover.length + value.length);
                combinedValue.set(leftover);
                combinedValue.set(value, leftover.length);
                leftover = null;
            }

            const pointsCount = Math.floor(combinedValue.length / 16);
            if (pointsCount * 12 < combinedValue.length) {
                leftover = combinedValue.slice(pointsCount * 16);
            }

            const floatArray = new Float32Array(combinedValue.buffer, combinedValue.byteOffset, pointsCount * 4);

            for (let i = 0; i < floatArray.length; i += 4) {
                this._addPoint(floatArray[i + 1], floatArray[i], floatArray[i + 2], floatArray[i + 3]);
            }

            if (onProgress) {
                onProgress(this.cursor, this.totalExpected, this.loadingState);
            }
        }
    }

    _addPoint(lng, lat, alt, tsUtc) {
        this._ensureCapacity(this.cursor + 1);

        // This handles DST transitions or long-distance travel
        if (Math.abs(tsUtc - this._lastOffsetCheckTs) > 3600) {
            this._currentOffset = this._getOffsetSeconds(tsUtc);
            this._lastOffsetCheckTs = tsUtc;
        }
        const timestamp = tsUtc;
        const tsLinear = timestamp;
        const localTs = timestamp + this._currentOffset;
        const tsAggregate = ((localTs % 86400) + 86400) % 86400;
        const dayOfWeek = Math.pow(new Date(localTs * 1000).getUTCDay(), 2);
        // Write to Raw Buffer
        this._writeToBuffer(this.buffer, this.cursor++, lng, lat, alt, tsLinear, tsAggregate, dayOfWeek);

        // Write to Cleaned Buffer (Spatial Redundancy Check)
        if (!this._isRedundant(lng, lat, tsUtc)) {
            this._writeToBuffer(this.cleanedBuffer, this.cleanedCursor++, lng, lat, alt, tsLinear, tsAggregate, dayOfWeek);
        }
    }

    _writeToBuffer(target, idx, lng, lat, alt, timeLinear, timeAggregate, dayOfWeek) {
        const i = idx * 6;
        target[i] = lng;
        target[i + 1] = lat;
        target[i + 2] = alt;
        target[i + 3] = timeLinear;
        target[i + 4] = dayOfWeek;
        target[i + 5] = timeAggregate;
    }

    _isRedundant(lng, lat, ts) {
        if (this.cleanedCursor === 0) return false;
        const lastIdx = (this.cleanedCursor - 1) * 6;
        const dx = this.cleanedBuffer[lastIdx] - lng;
        const dy = this.cleanedBuffer[lastIdx + 1] - lat;
        const dt = ts - (this.cleanedBuffer[lastIdx + 2] + this.minTimestamp);
        return (dt < 30) && (dx * dx + dy * dy < 4e-10); // ~2m
    }

    _ensureCapacity() {
        if ((this.cursor + 1) * 6 >= this.buffer.length) {
            const newSize = this.buffer.length * 2;
            const nb = new Float32Array(newSize);
            const ncb = new Float32Array(newSize);
            nb.set(this.buffer);
            ncb.set(this.cleanedBuffer);
            this.buffer = nb;
            this.cleanedBuffer = ncb;
        }
    }

    _getOffsetSeconds(tsUtc) {
        // Get the offset for this specific second in the target timezone
        const instant = Temporal.Instant.fromEpochMilliseconds(tsUtc * 1000);
        const zonedDateTime = instant.toZonedDateTimeISO(this.timeZone);
        return zonedDateTime.offsetNanoseconds / 1e9;
    }

    async _generateBundledPath(onProgress, precisionValue = 0.0005, weight = 0.5) {
        this._dataCache = {};
        const precision = 1 / precisionValue;
        const TABLE_SIZE = 4194304;
        const TABLE_MASK = TABLE_SIZE - 1;
        const grid = new Float64Array(TABLE_SIZE * 3);

        this.snappedBuffer = new Float32Array(this.cleanedCursor * 5);

        // Pass 1: Global Grid Accumulation (Unchanged)
        for (let i = 0; i < this.cleanedCursor; i++) {
            const idx = i * 6;
            const x = this.cleanedBuffer[idx], y = this.cleanedBuffer[idx + 1];
            const h = ((Math.floor(x * precision) * 73856093) ^ (Math.floor(y * precision) * 19349663)) & TABLE_MASK;
            grid[h * 3] += x;
            grid[h * 3 + 1] += y;
            grid[h * 3 + 2]++;

            if (i % 500000 === 0) { // Increased interval for better performance
                if (onProgress) onProgress(i, this.cleanedCursor, 'bundling');
                await new Promise(r => setTimeout(r, 0));
            }
        }

        // Pass 2: Centroid Snap + Timestamp Injection
        for (let i = 0; i < this.cleanedCursor; i++) {
            const idx = i * 6; // Source (6-float stride)
            const s = i * 5;   // Destination (4-float stride)

            const x = this.cleanedBuffer[idx];
            const y = this.cleanedBuffer[idx + 1];
            const h = ((Math.floor(x * precision) * 73856093) ^ (Math.floor(y * precision) * 19349663)) & TABLE_MASK;
            const c = grid[h * 3 + 2];

            // Bundled Geometry
            this.snappedBuffer[s] = x * (1 - weight) + (grid[h * 3] / c) * weight;
            this.snappedBuffer[s + 1] = y * (1 - weight) + (grid[h * 3 + 1] / c) * weight;

            // Inject Timestamps (Direct copy from source buffer)
            this.snappedBuffer[s + 2] = this.cleanedBuffer[idx + 3]; // linearTs
            this.snappedBuffer[s + 3] = this.cleanedBuffer[idx + 4]; // aggTs
            this.snappedBuffer[s + 4] = this.cleanedBuffer[idx + 5]; // dayOfWeek (NEW)
        }

        // Pass 3: Laplacian Smoothing (Adjusted for stride 4)
        for (let i = 1; i < this.cleanedCursor - 1; i++) {
            const c = i * 5, p = (i - 1) * 5, n = (i + 1) * 5;
            this.snappedBuffer[c] = (this.snappedBuffer[p] + this.snappedBuffer[c] + this.snappedBuffer[n]) / 3;
            this.snappedBuffer[c + 1] = (this.snappedBuffer[p + 1] + this.snappedBuffer[c + 1] + this.snappedBuffer[n + 1]) / 3;
        }

        this.snappedVersion++;
        if (onProgress) onProgress(this.cleanedCursor, this.cleanedCursor, 'bundling');
    }

    _hexToRgb(hex) {
        const r = parseInt(hex.slice(1, 3), 16), g = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
        return [r, g, b];
    }
}