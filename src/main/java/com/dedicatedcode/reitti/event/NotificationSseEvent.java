package com.dedicatedcode.reitti.event;

import com.dedicatedcode.reitti.model.notification.NotificationPresentation;

import java.io.Serializable;

/**
 * Payload pushed over the SSE connection when the number of unread notifications of a user
 * changes. The browser uses it to show/refresh the notification indicator without polling.
 * <p>
 * For newly created notifications, {@code typeKey} and {@code presentation} are populated so the
 * presentation script knows that a dialog/toast is waiting. Read/clear broadcasts leave them null.
 */
public class NotificationSseEvent implements Serializable {
    private final String type = SSEType.NOTIFICATION.name();
    private final long unreadCount;
    private final boolean showBadge;
    private final String title;
    private final String typeKey;
    private final NotificationPresentation presentation;

    public NotificationSseEvent(long unreadCount, boolean showBadge, String title) {
        this(unreadCount, showBadge, title, null, null);
    }

    public NotificationSseEvent(long unreadCount,
                                boolean showBadge,
                                String title,
                                String typeKey,
                                NotificationPresentation presentation) {
        this.unreadCount = unreadCount;
        this.showBadge = showBadge;
        this.title = title;
        this.typeKey = typeKey;
        this.presentation = presentation;
    }

    public String getType() {
        return type;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public boolean isShowBadge() {
        return showBadge;
    }

    public String getTitle() {
        return title;
    }

    public String getTypeKey() {
        return typeKey;
    }

    public NotificationPresentation getPresentation() {
        return presentation;
    }
}
