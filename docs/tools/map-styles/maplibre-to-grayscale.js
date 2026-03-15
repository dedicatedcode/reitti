#!/usr/bin/env node

/**
 * maplibre-to-grayscale.js
 *
 * Converts a colored MapLibre GL style JSON to a grayscale version.
 * Preserves luminance/brightness while removing all hue information.
 *
 * Usage:
 *   node maplibre-to-grayscale.js input.json > output.json
 *   node maplibre-to-grayscale.js input.json -o output.json
 *   cat input.json | node maplibre-to-grayscale.js
 */

const fs = require('fs');
const path = require('path');

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
    const match = str.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/);
    if (match) {
        return [parseFloat(match[1]), parseFloat(match[2]), parseFloat(match[3]), match[4] !== undefined ? parseFloat(match[4]) : 1];
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
            if (t < 1/6) return p + (q - p) * 6 * t;
            if (t < 1/2) return q;
            if (t < 2/3) return p + (q - p) * (2/3 - t) * 6;
            return p;
        };
        const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        const p = 2 * l - q;
        r = hue2rgb(p, q, h + 1/3);
        g = hue2rgb(p, q, h);
        b = hue2rgb(p, q, h - 1/3);
    }
    return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
}

function parseHsl(str) {
    const match = str.match(/^hsla?\(\s*([\d.]+)\s*,\s*([\d.]+)%?\s*,\s*([\d.]+)%?\s*(?:,\s*([\d.]+)\s*)?\)$/);
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

// --- Grayscale Conversion ---

function rgbToGrayscale(r, g, b) {
    // ITU-R BT.601 luma weights (perceptual luminance)
    return Math.round(0.299 * r + 0.587 * g + 0.114 * b);
}

function rgbToHex(r, g, b) {
    const toHex = (n) => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0');
    return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function toGrayscale(color) {
    const rgba = parseColor(color);
    if (!rgba) return color; // Not a color, return as-is

    const [r, g, b, a] = rgba;
    const gray = rgbToGrayscale(r, g, b);

    // Preserve the alpha channel format
    if (color.includes('rgba') || (color.includes('#') && color.length === 9)) {
        const alphaStr = a.toFixed(2).replace(/\.?0+$/, '');
        return `rgba(${gray}, ${gray}, ${gray}, ${alphaStr})`;
    }

    return rgbToHex(gray, gray, gray);
}

// --- Expression/Value Traversal ---

function isColorProperty(key) {
    const colorProps = [
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
        'fill-extrusion-color',
        'sky-color',
        'horizon-color',
        'fog-color',
        'fog-ground-blend',
        'shadow-color',
        'highlight-color',
    ];
    return colorProps.includes(key);
}

function processValue(value, isColorContext = false) {
    if (value === null || value === undefined) return value;

    // Direct string color
    if (typeof value === 'string') {
        const parsed = parseColor(value);
        if (parsed) return toGrayscale(value);
        return value;
    }

    // Array - could be an expression
    if (Array.isArray(value)) {
        if (value.length === 0) return value;

        // MapLibre expression
        const op = value[0];

        if (typeof op === 'string') {
            // Color-producing expressions - convert the output values
            switch (op) {
                case 'rgb':
                case 'rgba':
                case 'hsl':
                case 'hsla': {
                    // These are color constructors - evaluate and convert
                    const r = typeof value[1] === 'number' ? value[1] : 128;
                    const g = typeof value[2] === 'number' ? value[2] : 128;
                    const b = typeof value[3] === 'number' ? value[3] : 128;
                    const gray = rgbToGrayscale(r, g, b);
                    if (op === 'rgba' || op === 'hsla') {
                        return ['rgba', gray, gray, gray, value[4] !== undefined ? value[4] : 1];
                    }
                    return ['rgb', gray, gray, gray];
                }

                case 'to-color':
                    // Process the inner value
                    return [op, processValue(value[1], true)];

                case 'interpolate':
                case 'interpolate-hcl':
                case 'interpolate-lab': {
                    // ['interpolate', ['linear'], ['zoom'], 0, value, 10, value, ...]
                    // or ['interpolate', ['linear'], input, stop1, val1, stop2, val2, ...]
                    return value.map((item, i) => {
                        if (i <= 2) return processValue(item, false); // operator, easing, input
                        if (i % 2 === 1) return item; // stop keys (numbers)
                        return processValue(item, isColorContext); // stop values (possibly colors)
                    });
                }

                case 'step': {
                    // ['step', input, defaultVal, stop1, val1, stop2, val2, ...]
                    return value.map((item, i) => {
                        if (i === 0) return item; // operator
                        if (i === 1) return processValue(item, false); // input
                        if (i === 2) return processValue(item, isColorContext); // default value
                        if (i % 2 === 1) return item; // stop keys
                        return processValue(item, isColorContext); // stop values
                    });
                }

                case 'match': {
                    // ['match', input, label1, output1, label2, output2, ..., fallback]
                    return value.map((item, i) => {
                        if (i === 0) return item; // operator
                        if (i === 1) return processValue(item, false); // input expression
                        if (i === value.length - 1) return processValue(item, isColorContext); // fallback
                        if (i % 2 === 0) return processValue(item, isColorContext); // outputs (even indices after input)
                        return item; // labels (odd indices after input)
                    });
                }

                case 'case': {
                    // ['case', condition1, output1, condition2, output2, ..., fallback]
                    return value.map((item, i) => {
                        if (i === 0) return item; // operator
                        if (i === value.length - 1) return processValue(item, isColorContext); // fallback
                        if (i % 2 === 0) return processValue(item, false); // conditions
                        return processValue(item, isColorContext); // outputs
                    });
                }

                case 'coalesce':
                case 'let':
                case 'var':
                case 'concat':
                case 'format':
                    // Process all arguments
                    return value.map((item, i) => {
                        if (i === 0) return item; // operator
                        return processValue(item, isColorContext);
                    });

                default:
                    // Recursively process all array elements
                    return value.map(item => processValue(item, isColorContext));
            }
        }

        // Not an expression (e.g., a stops array or plain array of colors)
        return value.map(item => processValue(item, isColorContext));
    }

    // Object
    if (typeof value === 'object') {
        const result = {};
        for (const [k, v] of Object.entries(value)) {
            result[k] = processValue(v, isColorContext);
        }
        return result;
    }

    return value;
}

function processLayer(layer) {
    const result = { ...layer };

    // Process paint properties
    if (result.paint && typeof result.paint === 'object') {
        const newPaint = {};
        for (const [key, value] of Object.entries(result.paint)) {
            newPaint[key] = processValue(value, isColorProperty(key));
        }
        result.paint = newPaint;
    }

    // Process layout properties (some might have colors)
    if (result.layout && typeof result.layout === 'object') {
        const newLayout = {};
        for (const [key, value] of Object.entries(result.layout)) {
            newLayout[key] = processValue(value, isColorProperty(key));
        }
        result.layout = newLayout;
    }

    return result;
}

function processStyle(style) {
    const result = { ...style };

    // Update name
    result.name = `${style.name || 'Style'} Grayscale`;

    // Process top-level color properties (fog, sky, light)
    if (result.fog) {
        const newFog = {};
        for (const [key, value] of Object.entries(result.fog)) {
            newFog[key] = processValue(value, isColorProperty(key));
        }
        result.fog = newFog;
    }

    if (result.sky) {
        const newSky = {};
        for (const [key, value] of Object.entries(result.sky)) {
            newSky[key] = processValue(value, isColorProperty(key));
        }
        result.sky = newSky;
    }

    if (result.light) {
        const newLight = {};
        for (const [key, value] of Object.entries(result.light)) {
            newLight[key] = processValue(value, isColorProperty(key));
        }
        result.light = newLight;
    }

    // Process all layers
    if (Array.isArray(result.layers)) {
        result.layers = result.layers.map(processLayer);
    }

    return result;
}

// --- Main ---

function main() {
    let input = '';

    const args = process.argv.slice(2);
    let inputFile = null;
    let outputFile = null;

    // Parse arguments
    for (let i = 0; i < args.length; i++) {
        if (args[i] === '-o' || args[i] === '--output') {
            outputFile = args[++i];
        } else if (args[i] === '-h' || args[i] === '--help') {
            console.log(`
Usage: node maplibre-to-grayscale.js [input.json] [-o output.json]

Converts a colored MapLibre GL style to grayscale.

Options:
  -o, --output FILE   Write output to file (default: stdout)
  -h, --help          Show this help message

Examples:
  node maplibre-to-grayscale.js style.json > grayscale.json
  node maplibre-to-grayscale.js style.json -o grayscale.json
  cat style.json | node maplibre-to-grayscale.js
      `);
            process.exit(0);
        } else if (!inputFile) {
            inputFile = args[i];
        }
    }

    if (inputFile) {
        // Read from file
        try {
            input = fs.readFileSync(path.resolve(inputFile), 'utf8');
        } catch (err) {
            console.error(`Error reading file: ${err.message}`);
            process.exit(1);
        }
    } else {
        // Read from stdin
        const chunks = [];
        process.stdin.setEncoding('utf8');
        process.stdin.on('data', chunk => chunks.push(chunk));
        process.stdin.on('end', () => {
            input = chunks.join('');
            processInput();
        });
        process.stdin.resume();
        return;
    }

    processInput();

    function processInput() {
        let style;
        try {
            style = JSON.parse(input);
        } catch (err) {
            console.error(`Error parsing JSON: ${err.message}`);
            process.exit(1);
        }

        const grayscale = processStyle(style);
        const output = JSON.stringify(grayscale, null, 2);

        if (outputFile) {
            try {
                fs.writeFileSync(path.resolve(outputFile), output, 'utf8');
                console.error(`Written to ${outputFile}`);
            } catch (err) {
                console.error(`Error writing file: ${err.message}`);
                process.exit(1);
            }
        } else {
            process.stdout.write(output);
        }
    }
}

main();