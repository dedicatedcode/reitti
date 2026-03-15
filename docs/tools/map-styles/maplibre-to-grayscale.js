#!/usr/bin/env node

/**
 * maplibre-to-grayscale.js
 *
 * Converts a colored MapLibre GL style to grayscale with adjustable
 * brightness and saturation.
 *
 * Usage:
 *   node maplibre-to-grayscale.js input.json -o output.json
 *   node maplibre-to-grayscale.js input.json -b 120 -s 20 > faded.json
 *
 * Options:
 *   -b, --brightness N   Brightness multiplier (0–200, default: 100)
 *   -s, --saturation N   Saturation retention % (0–100, default: 0)
 *   -o, --output FILE    Write to file instead of stdout
 *   -h, --help           Show help
 *
 * Examples:
 *   # Pure grayscale, slightly brightened
 *   node maplibre-to-grayscale.js colored.json -b 115 -o gray.json
 *
 *   # Faded look — desaturated but not pure gray
 *   node maplibre-to-grayscale.js colored.json -b 110 -s 20 -o faded.json
 *
 *   # Warm muted look
 *   node maplibre-to-grayscale.js colored.json -b 105 -s 30 -o muted.json
 */

const fs = require('fs');
const path = require('path');

// --- CLI Argument Parsing ---

function parseArgs() {
    const args = process.argv.slice(2);
    const opts = {
        input: null,
        output: null,
        brightness: 100,
        saturation: 0,
    };

    for (let i = 0; i < args.length; i++) {
        switch (args[i]) {
            case '-o':
            case '--output':
                opts.output = args[++i];
                break;
            case '-b':
            case '--brightness':
                opts.brightness = parseFloat(args[++i]);
                if (isNaN(opts.brightness) || opts.brightness < 0 || opts.brightness > 200) {
                    console.error('Error: --brightness must be between 0 and 200');
                    process.exit(1);
                }
                break;
            case '-s':
            case '--saturation':
                opts.saturation = parseFloat(args[++i]);
                if (isNaN(opts.saturation) || opts.saturation < 0 || opts.saturation > 100) {
                    console.error('Error: --saturation must be between 0 and 100');
                    process.exit(1);
                }
                break;
            case '-h':
            case '--help':
                console.log(`
maplibre-to-grayscale — Convert MapLibre styles to grayscale/faded

Usage:
  node maplibre-to-grayscale.js [input.json] [options]

Options:
  -b, --brightness N   Brightness 0–200 (default: 100)
                       >100 lightens, <100 darkens
  -s, --saturation N   Saturation 0–100 (default: 0)
                       0 = pure grayscale, 100 = original color
                       15–30 gives a nice faded/toned look
  -o, --output FILE    Write output to file
  -h, --help           Show this help

Examples:
  node maplibre-to-grayscale.js style.json -b 115 > gray.json
  node maplibre-to-grayscale.js style.json -b 110 -s 20 -o faded.json
  cat style.json | node maplibre-to-grayscale.js -b 120 -s 10
        `);
                process.exit(0);
                break;
            default:
                if (!opts.input && !args[i].startsWith('-')) {
                    opts.input = args[i];
                }
                break;
        }
    }

    return opts;
}

// --- Color Parsing ---

function hexToRgb(hex) {
    hex = hex.replace(/^#/, '');
    if (hex.length === 3) {
        hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
    }
    if (hex.length === 4) {
        hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2] + hex[3] + hex[3];
    }
    const num = parseInt(hex, 16);
    if (hex.length === 6) {
        return [(num >> 16) & 255, (num >> 8) & 255, num & 255, 1];
    }
    if (hex.length === 8) {
        return [(num >> 24) & 255, (num >> 16) & 255, (num >> 8) & 255, (num & 255) / 255];
    }
    return null;
}

function parseRgb(str) {
    const match = str.match(
        /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/
    );
    if (match) {
        return [
            parseFloat(match[1]),
            parseFloat(match[2]),
            parseFloat(match[3]),
            match[4] !== undefined ? parseFloat(match[4]) : 1,
        ];
    }
    return null;
}

function hslToRgb(h, s, l) {
    h = h / 360;
    s = s / 100;
    l = l / 100;
    let r, g, b;
    if (s === 0) {
        r = g = b = l;
    } else {
        const hue2rgb = (p, q, t) => {
            if (t < 0) t += 1;
            if (t > 1) t -= 1;
            if (t < 1 / 6) return p + (q - p) * 6 * t;
            if (t < 1 / 2) return q;
            if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
            return p;
        };
        const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        const p = 2 * l - q;
        r = hue2rgb(p, q, h + 1 / 3);
        g = hue2rgb(p, q, h);
        b = hue2rgb(p, q, h - 1 / 3);
    }
    return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
}

function parseHsl(str) {
    const match = str.match(
        /^hsla?\(\s*([\d.]+)\s*,\s*([\d.]+)%?\s*,\s*([\d.]+)%?\s*(?:,\s*([\d.]+)\s*)?\)$/
    );
    if (match) {
        const [r, g, b] = hslToRgb(parseFloat(match[1]), parseFloat(match[2]), parseFloat(match[3]));
        return [r, g, b, match[4] !== undefined ? parseFloat(match[4]) : 1];
    }
    return null;
}

function parseColor(str) {
    if (typeof str !== 'string') return null;
    str = str.trim();
    if (str.startsWith('#')) return hexToRgb(str);
    if (str.startsWith('rgb')) return parseRgb(str);
    if (str.startsWith('hsl')) return parseHsl(str);
    return null;
}

// --- Color Transformation ---

/**
 * Convert a color to grayscale with brightness and saturation control.
 *
 * @param {number} r - Red 0–255
 * @param {number} g - Green 0–255
 * @param {number} b - Blue 0–255
 * @param {number} brightness - Brightness multiplier 0–200 (100 = normal)
 * @param {number} saturation - Saturation retention 0–100 (0 = gray, 100 = original)
 * @returns {[number, number, number]} [r, g, b] transformed
 */
function transformColor(r, g, b, brightness, saturation) {
    // Step 1: Compute luminance (perceptual grayscale)
    const lum = 0.299 * r + 0.587 * g + 0.114 * b;

    // Step 2: Blend between grayscale and original based on saturation
    const sat = saturation / 100;
    let nr = lum + sat * (r - lum);
    let ng = lum + sat * (g - lum);
    let nb = lum + sat * (b - lum);

    // Step 3: Apply brightness
    if (brightness !== 100) {
        const br = brightness / 100;
        if (br <= 1) {
            // Darkening: just multiply
            nr *= br;
            ng *= br;
            nb *= br;
        } else {
            // Lightening: interpolate toward 255
            const factor = br - 1; // 0–1 range for brightness 100–200
            nr = nr + (255 - nr) * factor;
            ng = ng + (255 - ng) * factor;
            nb = nb + (255 - nb) * factor;
        }
    }

    return [
        Math.max(0, Math.min(255, Math.round(nr))),
        Math.max(0, Math.min(255, Math.round(ng))),
        Math.max(0, Math.min(255, Math.round(nb))),
    ];
}

function toOutputFormat(r, g, b, originalColor, alpha) {
    const toHex = (n) => Math.max(0, Math.min(255, n)).toString(16).padStart(2, '0');

    // Preserve original format
    if (typeof originalColor === 'string') {
        if (originalColor.startsWith('rgba') || originalColor.startsWith('hsla')) {
            const aStr = alpha.toFixed(2).replace(/\.?0+$/, '');
            return `rgba(${r}, ${g}, ${b}, ${aStr})`;
        }
        if (originalColor.startsWith('rgb') || originalColor.startsWith('hsl')) {
            return `rgb(${r}, ${g}, ${b})`;
        }
        // Hex — preserve length
        if (originalColor.replace('#', '').length <= 4) {
            return `#${toHex(r)[0]}${toHex(g)[0]}${toHex(b)[0]}`;
        }
        if (alpha < 1) {
            return `#${toHex(r)}${toHex(g)}${toHex(b)}${toHex(Math.round(alpha * 255))}`;
        }
        return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
    }

    return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function processColorValue(value, brightness, saturation) {
    if (typeof value !== 'string') return value;

    const rgba = parseColor(value);
    if (!rgba) return value; // Not a color, return as-is

    const [r, g, b, a] = rgba;
    const [nr, ng, nb] = transformColor(r, g, b, brightness, saturation);

    return toOutputFormat(nr, ng, nb, value, a);
}

// --- Expression/Value Traversal ---

function isColorProperty(key) {
    const colorProps = new Set([
        'background-color',
        'fill-color',
        'fill-outline-color',
        'fill-extrusion-color',
        'line-color',
        'text-color',
        'text-halo-color',
        'icon-color',
        'icon-halo-color',
        'circle-color',
        'circle-stroke-color',
        'sky-color',
        'horizon-color',
        'fog-color',
        'shadow-color',
        'highlight-color',
        'space-color',
        'light-color',
    ]);
    return colorProps.has(key);
}

function processValue(value, brightness, saturation, isColorCtx = false) {
    if (value === null || value === undefined) return value;

    // Direct string — might be a color
    if (typeof value === 'string') {
        if (isColorCtx || parseColor(value)) {
            return processColorValue(value, brightness, saturation);
        }
        return value;
    }

    // Numbers, booleans — pass through
    if (typeof value !== 'object') return value;

    // Array — could be a MapLibre expression
    if (Array.isArray(value)) {
        if (value.length === 0) return value;

        const op = value[0];

        if (typeof op === 'string') {
            switch (op) {
                // Color constructors — convert the output channels
                case 'rgb':
                case 'rgba':
                case 'hsl':
                case 'hsla': {
                    const r = typeof value[1] === 'number' ? value[1] : 128;
                    const g = typeof value[2] === 'number' ? value[2] : 128;
                    const b = typeof value[3] === 'number' ? value[3] : 128;
                    const [nr, ng, nb] = transformColor(r, g, b, brightness, saturation);
                    if (op === 'rgba' || op === 'hsla') {
                        return ['rgba', nr, ng, nb, value[4] !== undefined ? value[4] : 1];
                    }
                    return ['rgb', nr, ng, nb];
                }

                case 'to-color':
                    return [op, processValue(value[1], brightness, saturation, true)];

                // Interpolation expressions
                case 'interpolate':
                case 'interpolate-hcl':
                case 'interpolate-lab': {
                    return value.map((item, i) => {
                        if (i <= 2) return processValue(item, brightness, saturation, false);
                        if (i % 2 === 1) return item; // stop keys
                        return processValue(item, brightness, saturation, isColorCtx); // stop values
                    });
                }

                case 'step': {
                    return value.map((item, i) => {
                        if (i === 0) return item;
                        if (i === 1) return processValue(item, brightness, saturation, false);
                        if (i === 2) return processValue(item, brightness, saturation, isColorCtx);
                        if (i % 2 === 1) return item;
                        return processValue(item, brightness, saturation, isColorCtx);
                    });
                }

                case 'match': {
                    return value.map((item, i) => {
                        if (i === 0) return item;
                        if (i === 1) return processValue(item, brightness, saturation, false);
                        if (i === value.length - 1) return processValue(item, brightness, saturation, isColorCtx);
                        if (i % 2 === 0) return processValue(item, brightness, saturation, isColorCtx);
                        return item;
                    });
                }

                case 'case': {
                    return value.map((item, i) => {
                        if (i === 0) return item;
                        if (i === value.length - 1) return processValue(item, brightness, saturation, isColorCtx);
                        if (i % 2 === 0) return processValue(item, brightness, saturation, false);
                        return processValue(item, brightness, saturation, isColorCtx);
                    });
                }

                case 'coalesce':
                case 'let':
                case 'var':
                case 'concat':
                case 'format':
                    return value.map((item, i) => {
                        if (i === 0) return item;
                        return processValue(item, brightness, saturation, isColorCtx);
                    });

                default:
                    return value.map(item => processValue(item, brightness, saturation, isColorCtx));
            }
        }

        return value.map(item => processValue(item, brightness, saturation, isColorCtx));
    }

    // Object
    const result = {};
    for (const [k, v] of Object.entries(value)) {
        result[k] = processValue(v, brightness, saturation, isColorCtx);
    }
    return result;
}

// --- Style Processing ---

function processLayer(layer, brightness, saturation) {
    const result = { ...layer };

    if (result.paint && typeof result.paint === 'object') {
        const newPaint = {};
        for (const [key, value] of Object.entries(result.paint)) {
            newPaint[key] = processValue(value, brightness, saturation, isColorProperty(key));
        }
        result.paint = newPaint;
    }

    if (result.layout && typeof result.layout === 'object') {
        const newLayout = {};
        for (const [key, value] of Object.entries(result.layout)) {
            newLayout[key] = processValue(value, brightness, saturation, isColorProperty(key));
        }
        result.layout = newLayout;
    }

    return result;
}

function processStyle(style, brightness, saturation) {
    const result = { ...style };

    // Suffix the name
    const suffix = saturation === 0 ? 'Grayscale' : 'Faded';
    result.name = `${style.name || 'Style'} ${suffix}`;

    // Top-level color objects
    for (const key of ['fog', 'sky', 'light']) {
        if (result[key] && typeof result[key] === 'object') {
            const obj = {};
            for (const [k, v] of Object.entries(result[key])) {
                obj[k] = processValue(v, brightness, saturation, isColorProperty(k));
            }
            result[key] = obj;
        }
    }

    // Layers
    if (Array.isArray(result.layers)) {
        result.layers = result.layers.map((l) => processLayer(l, brightness, saturation));
    }

    return result;
}

// --- Main ---

function main() {
    const opts = parseArgs();

    // Read input
    let input;
    if (opts.input) {
        try {
            input = fs.readFileSync(path.resolve(opts.input), 'utf8');
        } catch (err) {
            console.error(`Error reading file: ${err.message}`);
            process.exit(1);
        }
    } else {
        // Read from stdin synchronously
        input = fs.readFileSync(0, 'utf8'); // fd 0 = stdin
    }

    // Parse JSON
    let style;
    try {
        style = JSON.parse(input);
    } catch (err) {
        console.error(`Error parsing JSON: ${err.message}`);
        process.exit(1);
    }

    // Process
    const output = processStyle(style, opts.brightness, opts.saturation);
    const json = JSON.stringify(output, null, 2);

    // Write output
    if (opts.output) {
        try {
            fs.writeFileSync(path.resolve(opts.output), json, 'utf8');
            console.error(`Written to ${opts.output}`);
        } catch (err) {
            console.error(`Error writing file: ${err.message}`);
            process.exit(1);
        }
    } else {
        process.stdout.write(json);
    }
}

main();