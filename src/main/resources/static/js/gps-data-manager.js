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
        this.buffer = new Float32Array(16000 * 5); // Raw: [x, y, ts, tod, dow]
        this.cleanedBuffer = new Float32Array(16000 * 5); // Filtered: [x, y, ts, tod, dow]
        this.snappedBuffer = null; // Generated via bundling

        // 2. State
        this.cursor = 0;
        this.cleanedCursor = 0;
        this.minTimestamp = null;
        this.maxTimestamp = null;
        this.totalExpected = 0;
        this.bounds = null;
    }

    /**
     * Range-Aware Load
     * @param {number} startUTC - Unix timestamp
     * @param {number} endUTC - Unix timestamp
     * @param {Function} onProgress - Progress callback
     */
    async load(startUTC, endUTC, onProgress) {
        // 1. Check if we already have this data in memory
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
                activeRanges: p.visits.map(v => ({
                    start: Math.floor(new Date(v.startTime).getTime() / 1000) - startUTC,
                    end: Math.floor(new Date(v.endTime).getTime() / 1000) - startUTC
                })).sort((a, b) => a.start - b.start),
                originalVisits: p.visits
            }));
            // Update internal range trackers
            this.minTimestamp = startUTC;
            this.maxTimestamp = endUTC;
            this.totalExpected = meta.totalPoints;

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
        const stride = 20; // 5 floats * 4 bytes = 20 bytes per point

        // Determine which time dimension to use for filtering/animation
        // Offset 8 = Linear TS, Offset 12 = Aggregate (TOD)
        const timeOffset = isAggregate ? 12 : 8;
        const dayOffset = 16; // Offset 16 = Day of Week (0-6)

        // 1. RAW MODE (Used for Heatmap)
        if (mode === 'raw') {
            return {
                length: this.cursor,
                attributes: {
                    getPosition: {
                        value: this.buffer,
                        size: 2,
                        stride: stride,
                        offset: 0
                    },
                    // filterValues expects [Time, Day] for the 2D DataFilterExtension
                    filterValues: {
                        value: this.buffer,
                        size: 2,
                        stride: stride,
                        offset: timeOffset
                    }
                }
            };
        }

        // 2. TRIPS / BUNDLED MODE
        const isBundled = (mode === 'bundled' && this.snappedBuffer);
        const activePosBuffer = isBundled ? this.snappedBuffer : this.cleanedBuffer;

        // Bundled buffer only stores [x, y] (8 bytes per point)
        // Cleaned buffer stores all 5 dimensions (20 bytes per point)
        const posStride = isBundled ? 8 : 20;

        // Calculate start/count based on time-window slicing
        const startIndex = rangeIndices ? rangeIndices.start : 0;
        const renderCount = rangeIndices ? rangeIndices.count : this.cleanedCursor;

        return {
            // We render as a single continuous path for performance
            length: 1,
            startIndices: new Uint32Array([0]),
            attributes: {
                getPath: {
                    value: activePosBuffer,
                    size: 2,
                    stride: posStride,
                    offset: startIndex * posStride
                },
                getTimestamps: {
                    value: this.cleanedBuffer,
                    size: 1,
                    stride: stride,
                    offset: (startIndex * stride) + timeOffset
                },
                // Used by DataFilterExtension to isolate specific days (Monday, etc.)
                filterValues: {
                    value: this.cleanedBuffer,
                    size: 1,
                    stride: stride,
                    offset: (startIndex * stride) + dayOffset
                }
            },
            // Meta-information for the layer
            _count: renderCount
        };
    }

    async _streamPoints(onProgress) {
        const response = await fetch(window.contextPath + this.config.map.streamUrl);
        const reader = response.body.getReader();
        let leftover = null;

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            let combinedValue = value;
            if (leftover) {
                combinedValue = new Uint8Array(leftover.length + value.length);
                combinedValue.set(leftover);
                combinedValue.set(value, leftover.length);
                leftover = null;
            }

            const pointsCount = Math.floor(combinedValue.length / 12);
            if (pointsCount * 12 < combinedValue.length) {
                leftover = combinedValue.slice(pointsCount * 12);
            }

            const floatArray = new Float32Array(combinedValue.buffer, combinedValue.byteOffset, pointsCount * 3);

            for (let i = 0; i < floatArray.length; i += 3) {
                this._addPoint(floatArray[i+1], floatArray[i], floatArray[i+2]);
            }

            if (onProgress) {
                onProgress(this.cursor, this.totalExpected, this.loadingState);
            }
        }
    }

    _addPoint(lng, lat, tsUtc) {
        this._ensureCapacity(this.cursor + 1);

        // This handles DST transitions or long-distance travel
        if (Math.abs(tsUtc - this._lastOffsetCheckTs) > 3600) {
            this._currentOffset = this._getOffsetSeconds(tsUtc);
            this._lastOffsetCheckTs = tsUtc;
        }
        const tsLinear = tsUtc - this.minTimestamp;
        const localTs = tsUtc + this._currentOffset;
        const tsAggregate = ((localTs % 86400) + 86400) % 86400;
        const dayOfWeek = new Date(localTs * 1000).getUTCDay();
        // Write to Raw Buffer
        this._writeToBuffer(this.buffer, this.cursor++, lng, lat, tsLinear, tsAggregate, dayOfWeek);

        // Write to Cleaned Buffer (Spatial Redundancy Check)
        if (!this._isRedundant(lng, lat, tsUtc)) {
            this._writeToBuffer(this.cleanedBuffer, this.cleanedCursor++, lng, lat, tsLinear, tsAggregate, dayOfWeek);
        }
    }

    _writeToBuffer(target, idx, x, y, tl, ta, dw) {
        const i = idx * 5;
        target[i] = x;
        target[i+1] = y;
        target[i+2] = tl;
        target[i+3] = ta;
        target[i+4] = dw; // 5th float
    }

    _isRedundant(lng, lat, ts) {
        if (this.cleanedCursor === 0) return false;
        const lastIdx = (this.cleanedCursor - 1) * 5;
        const dx = this.cleanedBuffer[lastIdx] - lng;
        const dy = this.cleanedBuffer[lastIdx+1] - lat;
        const dt = ts - (this.cleanedBuffer[lastIdx+2] + this.minTimestamp);
        return (dt < 30) && (dx*dx + dy*dy < 4e-10); // ~2m
    }

    _ensureCapacity() {
        if ((this.cursor + 1) * 5 >= this.buffer.length) {
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
        const precision = 1 / precisionValue;
        const TABLE_SIZE = 4194304;
        const TABLE_MASK = TABLE_SIZE - 1;
        const grid = new Float64Array(TABLE_SIZE * 3);
        this.snappedBuffer = new Float32Array(this.cleanedCursor * 2);

        // Pass 1: Global Grid Accumulation
        for (let i = 0; i < this.cleanedCursor; i++) {
            const idx = i * 5;
            const x = this.cleanedBuffer[idx], y = this.cleanedBuffer[idx+1];
            const h = ((Math.floor(x * precision) * 73856093) ^ (Math.floor(y * precision) * 19349663)) & TABLE_MASK;
            grid[h*3] += x; grid[h*3+1] += y; grid[h*3+2]++;
            if (i % 200000 === 0) {
                if (onProgress) {
                    onProgress(i, this.cleanedCursor, 'bundling');
                }
                await new Promise(r => setTimeout(r, 0));
            }
        }

        // Pass 2: Centroid Snap
        for (let i = 0; i < this.cleanedCursor; i++) {
            const idx = i * 5, s = i * 2, x = this.cleanedBuffer[idx], y = this.cleanedBuffer[idx+1];
            const h = ((Math.floor(x * precision) * 73856093) ^ (Math.floor(y * precision) * 19349663)) & TABLE_MASK;
            const c = grid[h*3+2];
            this.snappedBuffer[s] = x * (1-weight) + (grid[h*3]/c) * weight;
            this.snappedBuffer[s+1] = y * (1-weight) + (grid[h*3+1]/c) * weight;
        }

        // Pass 3: Laplacian Smoothing
        for (let i = 1; i < this.cleanedCursor - 1; i++) {
            const c = i*2, p = (i-1)*2, n = (i+1)*2;
            this.snappedBuffer[c] = (this.snappedBuffer[p] + this.snappedBuffer[c] + this.snappedBuffer[n]) / 3;
            this.snappedBuffer[c+1] = (this.snappedBuffer[p+1] + this.snappedBuffer[c+1] + this.snappedBuffer[n+1]) / 3;
        }
        if (onProgress) onProgress(this.cleanedCursor, this.cleanedCursor, 'bundling');
    }

    _hexToRgb(hex) {
        const r = parseInt(hex.slice(1, 3), 16), g = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
        return [r, g, b];
    }
}