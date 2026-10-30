package com.dedicatedcode.reitti.model.notification;

/**
 * How a notification should be presented in the UI.
 */
public enum NotificationPresentation {
    /** Only shown in the in-app inbox. */
    INBOX,
    /** Shown as a non-blocking toast. */
    TOAST,
    /** Shown as a modal dialog. */
    DIALOG
}
