
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