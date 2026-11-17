/**
 * Fetches and decodes the binary location point stream served by the
 * /api/v3/locations/stream endpoints.
 *
 * The stream consists of 20-byte little-endian records:
 *   lat (float32), lng (float32), alt (float32),
 *   timestamp (uint32 epoch seconds), timezoneOffsetSeconds (float32)
 */
const LocationStream = {
    async fetchPoints(url, signal) {
        const response = await fetch(url, signal ? {signal} : undefined);
        if (!response.ok) {
            throw new Error(`Location stream request failed with status ${response.status}`);
        }
        if (!response.body) {
            return [];
        }
        const reader = response.body.getReader();
        let leftover = null;
        const points = [];
        while (true) {
            const {done, value} = await reader.read();
            if (done) {
                break;
            }
            let chunk = value;
            if (leftover !== null) {
                chunk = new Uint8Array(leftover.length + value.length);
                chunk.set(leftover);
                chunk.set(value, leftover.length);
                leftover = null;
            }
            const recordCount = Math.floor(chunk.length / 20);
            if (recordCount === 0) {
                leftover = chunk.slice(0);
                continue;
            }
            if (recordCount * 20 < chunk.length) {
                leftover = chunk.slice(recordCount * 20);
            }
            this._decode(chunk, recordCount, points);
        }
        return points;
    },

    _decode(chunk, recordCount, points) {
        // Copy the records into a fresh buffer so float/int views are aligned.
        const buffer = chunk.buffer.slice(chunk.byteOffset, chunk.byteOffset + recordCount * 20);
        const floats = new Float32Array(buffer);
        const timestamps = new Uint32Array(buffer);
        for (let i = 0; i < floats.length; i += 5) {
            points.push({
                lat: floats[i],
                lng: floats[i + 1],
                alt: floats[i + 2],
                timestamp: timestamps[i + 3],
                offsetSeconds: floats[i + 4]
            });
        }
    }
};
