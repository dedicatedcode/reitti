package com.dedicatedcode.reitti.service.notification;

import com.dedicatedcode.reitti.service.ContextPathHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NotificationUrlBuilder {

    private final String advertiseUri;
    private final String contextPath;

    public NotificationUrlBuilder(@Value("${reitti.server.advertise-uri:}") String advertiseUri,
                                  ContextPathHolder contextPathHolder) {
        this.advertiseUri = advertiseUri;
        this.contextPath = contextPathHolder.getContextPath();
    }

    public String absolute(String actionUrl) {
        if (!StringUtils.hasText(actionUrl)) {
            return null;
        }
        if (actionUrl.startsWith("http://") || actionUrl.startsWith("https://")) {
            return actionUrl;
        }
        if (!StringUtils.hasText(advertiseUri)) {
            return null;
        }
        String base = advertiseUri.endsWith("/")
                ? advertiseUri.substring(0, advertiseUri.length() - 1)
                : advertiseUri;
        String path = actionUrl.startsWith("/") ? actionUrl : "/" + actionUrl;
        return base + contextPath + path;
    }
}
