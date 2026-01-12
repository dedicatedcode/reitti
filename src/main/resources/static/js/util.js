
function getUserTimezone() {
    if (window.userSettings.timezoneOverride) {
        return window.userSettings.timezoneOverride;
    } else {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }
}

function getCurrentLocalDate() {
    const date = new Date();

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
}

function getAsLocalDate(dateInUtc) {
    const date = new Date(dateInUtc);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
}

/**
 * Lightens a hex color by increasing the R, G, and B components.
 * @param {string} col - The hex color code (e.g., "#F06D06" or "F06D06").
 * @param {number} amt - The amount to lighten (positive number, typically 1 to 255).
 * @returns {string} The new lighter hex color code.
 */
function lightenHexColor(col, amt) {
    // Check if the color has the '#' prefix and remove it
    let usePound = false;
    if (col[0] === "#") {
        col = col.slice(1);
        usePound = true;
    }

    // Convert the 6-digit hex color to a base-10 integer
    let num = parseInt(col, 16);

    // Lighten Red component
    let r = (num >> 16) + amt;
    if (r > 255) r = 255; // Cap at max value
    else if (r < 0) r = 0;

    // Lighten Blue component (B is the middle 8 bits after shifting)
    let b = ((num >> 8) & 0x00FF) + amt;
    if (b > 255) b = 255;
    else if (b < 0) b = 0;

    // Lighten Green component (G is the last 8 bits)
    let g = (num & 0x0000FF) + amt;
    if (g > 255) g = 255;
    else if (g < 0) g = 0;

    // Combine and convert back to hex string.
    // The expression (g | (b << 8) | (r << 16)) reconstructs the color integer.
    // .toString(16) converts to hex.
    let newColor = (g | (b << 8) | (r << 16)).toString(16);

    // Pad with leading zeros if necessary (e.g., if a component is very small)
    while (newColor.length < 6) {
        newColor = "0" + newColor;
    }

    // Return the new color with or without the original '#' prefix
    return (usePound ? "#" : "") + newColor;
}

function getBrowserTimeFormat() {
    // Create a test date with hours > 12 to distinguish between 12h and 24h formats
    const testDate = new Date(2023, 0, 1, 15, 0); // 3 PM

    // Format the time using the browser's locale
    const formatter = new Intl.DateTimeFormat(navigator.language, {
        hour: 'numeric',
        minute: 'numeric',
        hour12: false
    });

    const formattedTime = formatter.format(testDate);

    // Check if the formatted time contains "15" (24h format) or "3" (12h format)
    if (formattedTime.includes('15')) {
        return '24h';
    } else {
        return '12h';
    }
}