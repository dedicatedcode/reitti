
function getUserTimezone() {
    if (window.userSettings.timezoneOverride) {
        return window.userSettings.timezoneOverride;
    } else {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }
}