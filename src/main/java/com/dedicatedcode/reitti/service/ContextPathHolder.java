package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ContextPathHolder {
    private final String contextPath;

    public ContextPathHolder(@Value("${server.servlet.context-path:}") String contextPath) {
        if (contextPath.endsWith("/")) {
            this.contextPath = contextPath.substring(0, contextPath.length() - 1);
        } else {
            this.contextPath = contextPath;
        }
    }

    public String getContextPath() {
        return contextPath;
    }
}
