class GpsDataManager {
    constructor(userConfig, userSettings, timeZone) {
        this.config = userConfig;
        this.userSettings = userSettings;
        this.color = this._hexToRgb(userConfig.color || '#3388ff');
        this.id = userConfig.id || 'default';
        this.timeZone = timeZone || "UTC";

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
        this.lastLocation = null;
        this.activityWeights = [0];
        this.totalActivity = 0;
    }

    async loadFixed(onProgress) {
        return this.load(0, Number.MAX_SAFE_INTEGER, onProgress)
    }

    async loadFixedWithVisits(visits, onProgress) {
        this.visits = visits.map(p => ({
                id: p.id,
                coordinates: [p.longitudeCentroid, p.latitudeCentroid],
                totalDurationSec: 6000,
                name: p.name,
                activeRanges: []
        }));
        return  this.load(0, Number.MAX_SAFE_INTEGER, onProgress)
    }

    destroy() {
        this.abortController?.abort();
        this.abortController = null;
        this.buffer = null;
        this.cleanedBuffer = null;
        this.snappedBuffer = null;
        this.visits = null;
        this.lastLocation = null;
    }

    async load(startUTC, endUTC, onProgress) {
        if (this.loadingState === 'complete' &&
            startUTC >= this.minTimestamp &&
            endUTC <= this.maxTimestamp) {

            console.log("Range already in memory. Skipping fetch.");
            if (onProgress) onProgress(this.cursor, this.totalExpected, 'complete');
            return;
        }

        // If a controller exists, it means a previous load is still running.
        if (this.abortController) {
            this.abortController.abort();
        }

        this.abortController = new AbortController();
        const { signal } = this.abortController;

        try {
            this._dataCache = {};
            this.cursor = 0;
            this.cleanedCursor = 0;
            this.buffer = new Float32Array(16000 * 6);
            this.cleanedBuffer = new Float32Array(16000 * 6);
            this.snappedBuffer = null;
            this.bounds = null;

            this.loadingState = 'metadata';

            if (onProgress) onProgress(0, 0, this.loadingState);
            const requests = [];
            requests.push(fetch(window.contextPath + this.config.map.metaDataUrl, {signal}))
            if (this.config.map.visitsUrl) {
                requests.push(fetch(window.contextPath + this.config.map.visitsUrl, {signal}))
            }
            if (this.config.mapDataProviders) {
                this.config.mapDataProviders.forEach(provider => {
                    requests.push(provider.load({signal}))
                });
            }
            const responses = await Promise.all(requests);

            const meta = await responses[0].json();
            this.lastLocation = meta.latestLocation;
            if (this.config.map.visitsUrl) {
                const receivedVisits = await responses[1].json();
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
                            startAggregate: startUtcSeconds < startUTC ? 0 :  ((localStartTs % 86400) + 86400) % 86400,
                            endAggregate:  endUtcSeconds > endUTC ? 86400 : ((localEndTs % 86400) + 86400) % 86400,
                            startDayOfWeek: Math.pow(new Date(localStartTs * 1000).getUTCDay(), 2),
                            endDayOfWeek: Math.pow(new Date(localEndTs * 1000).getUTCDay(), 2)
                        });
                    }),
                    originalVisits: p.visits
                }));
            }

            // Update internal range trackers
            this.minTimestamp = startUTC;
            this.maxTimestamp = endUTC;
            this.totalExpected = meta.totalPoints;
            if (this.totalExpected > 0) {
                this.bounds = [meta.minLng, meta.minLat, meta.maxLng, meta.maxLat];
            }
            // Clear old data to make room for the new range
            this.cursor = 0;
            this.cleanedCursor = 0;
            this.snappedBuffer = null;

            this.loadingState = 'streaming';
            await this._streamPoints(onProgress, signal);

            // Security check: If aborted during stream, stop here
            if (signal.aborted) {
                throw new DOMException('Aborted', 'AbortError');
            }

            this.loadingState = 'bundling';
            await this._generateBundledPath(onProgress);
            this._computeActivityWeights();
            this.loadingState = 'complete';
            if (onProgress) onProgress(this.totalExpected, this.totalExpected, 'complete');
            if (this.config.map.visitsUrl) {
                console.log("Load complete:", this.loadingState, 'with bounds:', this.bounds, 'and total points:', this.totalExpected, ' (cleaned:', this.cleanedCursor, ') in range:', startUTC, 'to', endUTC, 'at', this.visits.length, 'visits.');
            } else {
                console.log("Load complete:", this.loadingState, 'with bounds:', this.bounds, 'and total points:', this.totalExpected, ' (cleaned:', this.cleanedCursor, ') in range:', startUTC, 'to', endUTC);
            }

        } catch (error) {
            if (error.name === 'AbortError') {
                console.warn("Previous load cancelled by new selection.");
                if (onProgress) onProgress(0, 0, 'aborted');

                return;
            }
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
        const rangeKey = rangeIndices ? `${rangeIndices.start}-${rangeIndices.count}` : 'full';
        const cacheKey = `${mode}-${isAggregate}-${rangeKey}`;
        const currentPointCount = mode === 'raw' ? this.cursor : this.cleanedCursor;

        if (this._dataCache[cacheKey] && this._dataCache[cacheKey].version === currentPointCount) {
            return this._dataCache[cacheKey].payload;
        }

        let payload;
        const thresholdSec = 300; // 5 minutes.

        if (mode === 'bundled') {
            const stride = 20;
            const strideFloats = 5; // 20 bytes / 4 bytes per float
            const timeIndex = isAggregate ? 4 : 2; // Byte offset 16/4 or 8/4
            const usedLength = this.cleanedCursor * strideFloats;
            const trimmed = this.snappedBuffer.subarray(0, usedLength);

            const { length, startIndices } = this._getBinaryIndices(
                trimmed, this.cleanedCursor, strideFloats, timeIndex, thresholdSec
            );

            payload = {
                length: length,
                startIndices: startIndices,
                attributes: {
                    getPath: { value: trimmed, size: 2, stride: stride, offset: 0 },
                    getTimestamps: { value: trimmed, size: 1, stride: stride, offset: isAggregate ? 16 : 8 }
                }
            };
        } else if (mode === 'cleaned') {
            const stride = 24;
            const strideFloats = 6;
            const timeIndex = isAggregate ? 5 : 3;
            const usedLength = this.cleanedCursor * strideFloats;
            const trimmed = this.cleanedBuffer.subarray(0, usedLength);

            const { length, startIndices } = this._getBinaryIndices(
                trimmed, this.cleanedCursor, strideFloats, timeIndex, thresholdSec
            );

            payload = {
                length: length,
                startIndices: startIndices,
                attributes: {
                    getPath: { value: trimmed, size: 3, stride: stride, offset: 0 },
                    getTimestamps: { value: trimmed, size: 1, stride: stride, offset: isAggregate ? 20 : 12 }
                }
            };
        } else if (mode === 'raw') {
            const stride = 24;
            const strideFloats = 6;
            const timeIndex = isAggregate ? 5 : 3;
            const usedLength = this.cursor * strideFloats;
            const trimmed = this.buffer.subarray(0, usedLength);
            const { length, startIndices } = this._getBinaryIndices(
                trimmed, this.cursor, strideFloats, timeIndex, thresholdSec
            );
            payload = {
                length: length,
                startIndices: startIndices,
                attributes: {
                    getPath: { value: trimmed, size: 3, stride: stride, offset: 0 },
                    getTimestamps: { value: trimmed, size: 1, stride: stride, offset: isAggregate ? 20 : 12 }
                }
            };
        }

        this._dataCache[cacheKey] = {
            version: currentPointCount,
            payload: payload
        };

        return payload;
    }

    _getBinaryIndices(buffer, pointCount, strideFloats, timeIndex, thresholdSec) {
        // If continuous is true, or no points, return single path
        if (this.config.continuous || pointCount === 0) {
            return {
                length: 1,
                startIndices: new Uint32Array([0, pointCount])
            };
        }

        const indices = [0];

        // Scan buffer for time gaps
        for (let i = 0; i < pointCount - 1; i++) {
            const t1 = buffer[i * strideFloats + timeIndex];
            const t2 = buffer[(i + 1) * strideFloats + timeIndex];

            // If the gap exceeds the threshold, break the line at i+1
            if (Math.abs(t2 - t1) > thresholdSec) {
                indices.push(i + 1);
            }
        }

        // Always push the final vertex count as required by deck.gl
        indices.push(pointCount);

        return {
            length: indices.length - 1,
            startIndices: new Uint32Array(indices)
        };
    }

    async _streamPoints(onProgress, signal) {
        if (this.config.map.streamUrl === undefined) {
            const midTimestamp = this.minTimestamp != null && this.maxTimestamp != null
                ? Math.floor((this.minTimestamp + this.maxTimestamp) / 2)
                : Math.floor(Date.now() / 1000);
            const centerLng = this.lastLocation.longitude;
            const centerLat = this.lastLocation.latitude;
            this._addPoint(centerLng, centerLat, 0, midTimestamp, 0);

            this.totalExpected = this.cursor;
            return;
        }
        const response = await fetch(window.contextPath + this.config.map.streamUrl);
        const reader = response.body.getReader();
        let leftover = null;

        while (true) {
            const {done, value} = await reader.read();
            if (done) {
                break;
            }
            if (signal.aborted) {
                await reader.cancel();
                break;
            }
            let combinedValue = value;
            if (leftover) {
                combinedValue = new Uint8Array(leftover.length + value.length);
                combinedValue.set(leftover);
                combinedValue.set(value, leftover.length);
                leftover = null;
            }

            const pointsCount = Math.floor(combinedValue.length / 20);
            if (pointsCount * 16 < combinedValue.length) {
                leftover = combinedValue.slice(pointsCount * 20);
            }

            const floatArray = new Float32Array(combinedValue.buffer, combinedValue.byteOffset, pointsCount * 5);

            for (let i = 0; i < floatArray.length; i += 5) {
                this._addPoint(floatArray[i + 1], floatArray[i], floatArray[i + 2], floatArray[i + 3], floatArray[i + 4]);
            }

            if (onProgress) {
                onProgress(this.cursor, this.totalExpected, this.loadingState);
            }
        }
    }

    _addPoint(lng, lat, alt, tsUtc, offsetSeconds) {
        this._ensureCapacity(this.cursor + 1);
        const timestamp = tsUtc;
        const tsLinear = timestamp;
        const localTs = timestamp + offsetSeconds;
        const tsAggregate = ((localTs % 86400) + 86400) % 86400;
        let dayIndex = new Date(localTs * 1000).getUTCDay(); // Standard: Sun=0, Mon=1...
        if (this.userSettings.weekStartsOnMonday) {
            // Shift Sunday(0) to 6, Monday(1) to 0, etc.
            dayIndex = (dayIndex + 6) % 7;
        }
        // Store as bitmask: 2^0, 2^1, 2^2...
        // Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64
        const dayOfWeek = 1 << dayIndex;
        // Write to Raw Buffer
        this._writeToBuffer(this.buffer, this.cursor++, lng, lat, 0, tsLinear, tsAggregate, dayOfWeek);

        // Write to Cleaned Buffer (Spatial Redundancy Check)
        if (!this._isRedundant(lng, lat, tsUtc)) {
            this._writeToBuffer(this.cleanedBuffer, this.cleanedCursor++, lng, lat, 0, tsLinear, tsAggregate, dayOfWeek);
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
        const dt = Math.abs(ts - this.cleanedBuffer[lastIdx + 3]);

        // 3. Return true only if both time and space are close
        return (dt < 30) && (dx * dx + dy * dy < 8e-10);
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
        const date = new Date(tsUtc * 1000);

        // Create a formatter that outputs the timezone offset name (e.g., "GMT+2" or "UTC")
        // 'shortOffset' gives us a format we can parse: GMT+X or GMT-X
        const formatter = new Intl.DateTimeFormat('en-US', {
            timeZone: this.timeZone,
            timeZoneName: 'shortOffset',
        });

        const parts = formatter.formatToParts(date);
        const offsetString = parts.find(part => part.type === 'timeZoneName').value;

        // The offsetString will be "GMT", "GMT+1", "GMT-05:30", etc.
        return this._parseOffsetToSeconds(offsetString);
    }

    _parseOffsetToSeconds(offsetString) {
        if (offsetString === 'GMT' || offsetString === 'UTC') return 0;

        // Remove "GMT" and split by ":"
        // Matches patterns like +02, -05, +05:30
        const match = offsetString.match(/GMT([+-])(\d{1,2})(?::(\d{2}))?/);
        if (!match) return 0;

        const [_, sign, hours, minutes] = match;
        const totalSeconds = (parseInt(hours) * 3600) + (parseInt(minutes || 0) * 60);

        return sign === '+' ? totalSeconds : -totalSeconds;
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

    _computeActivityWeights() {
        if (!this.visits || this.visits.length === 0) {
            this.activityWeights = [0];
            this.totalActivity = 0;
            return;
        }

        const weights = [0];
        let cumulative = 0;

        // --- DYNAMIC PACING ---
        const visitWeight = 0.05;     // Faster visit "skip"
        const tripWeight = 1;      // Steady cruising speed

        const sortedRanges = this.visits.flatMap(p => p.activeRanges)
            .sort((a, b) => a.start - b.start);

        for (let i = 1; i < this.cleanedCursor; i++) {
            const idx = i * 6;
            const tsCurrent = this.cleanedBuffer[idx + 3];
            const dt = tsCurrent - this.cleanedBuffer[(i - 1) * 6 + 3];

            if (dt <= 0) { weights.push(cumulative); continue; }

            const currentVisit = sortedRanges.find(r => tsCurrent >= r.start && tsCurrent <= r.end);
            let factor = tripWeight;

            if (currentVisit) {
                factor = visitWeight;
            } else {
                // Find the trip boundaries
                const prevVisit = sortedRanges.find(r => r.end < tsCurrent);
                const nextVisit = sortedRanges.find(r => r.start > tsCurrent);
                const tripStart = prevVisit ? prevVisit.end : (tsCurrent - 3600);
                const tripEnd = nextVisit ? nextVisit.start : (tsCurrent + 3600);
                const tripDuration = tripEnd - tripStart;

                const buffer = Math.min(tripDuration * 0.30, 600);

                // Arrival Ramp (Slow down 15% before arrival)
                if (nextVisit && (nextVisit.start - tsCurrent) <= buffer) {
                    const progress = 1 - ((nextVisit.start - tsCurrent) / buffer);
                    const smooth = progress * progress * (3 - 2 * progress);
                    factor = tripWeight - (smooth * (tripWeight - visitWeight));
                }
                // Departure Ramp (Speed up 15% after leaving)
                else if (prevVisit && (tsCurrent - prevVisit.end) <= buffer) {
                    const progress = (tsCurrent - prevVisit.end) / buffer;
                    const smooth = progress * progress * (3 - 2 * progress);
                    factor = visitWeight + (smooth * (tripWeight - visitWeight));
                }
            }

            cumulative += (dt * factor);
            weights.push(cumulative);
        }

        this.activityWeights = weights;
        this.totalActivity = cumulative;
    }

    getDisplayTimestamp(linearProgres, useWarp = false, aggregate = false) {
        if (aggregate || !useWarp || !this.activityWeights || this.activityWeights.length === 0) {
            const totalTime = this.maxTimestamp - this.minTimestamp;
            return this.minTimestamp + (linearProgress * totalTime);
        }

        // 1. Map linear animation progress (0 -> 1) to our custom Activity Space
        const targetWeight = linearProgress * this.totalActivity;

        // 2. Perform a Binary Search to find which data segment contains this weight
        let low = 0;
        let high = this.activityWeights.length - 1;
        let floorIndex = 0;

        while (low <= high) {
            const mid = Math.floor((low + high) / 2);
            if (this.activityWeights[mid] <= targetWeight) {
                floorIndex = mid;
                low = mid + 1; // Look upper
            } else {
                high = mid - 1; // Look lower
            }
        }

        // Boundary check: if we are at the very end of the data
        if (floorIndex >= this.activityWeights.length - 1) {
            return this.cleanedBuffer[(this.activityWeights.length - 1) * 6 + 3];
        }

        // 3. Get the two bounding weights and their corresponding real-world timestamps
        const w0 = this.activityWeights[floorIndex];
        const w1 = this.activityWeights[floorIndex + 1];

        const t0 = this.cleanedBuffer[floorIndex * 6 + 3];
        const t1 = this.cleanedBuffer[(floorIndex + 1) * 6 + 3];

        // 4. Smoothly interpolate between the two timestamps for sub-frame accuracy
        if (w1 === w0) return t0;
        const segmentProgress = (targetWeight - w0) / (w1 - w0);

        return t0 + segmentProgress * (t1 - t0);
    }

    _hexToRgb(hex) {
        const r = parseInt(hex.slice(1, 3), 16), g = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
        return [r, g, b];
    }


}