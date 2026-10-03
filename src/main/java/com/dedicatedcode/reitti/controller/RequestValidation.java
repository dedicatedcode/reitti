package com.dedicatedcode.reitti.controller;

import java.util.regex.Pattern;

/**
 * Validation helpers for user supplied request parameters that end up in redirects or markup.
 */
public final class RequestValidation {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{3,8}$");

    private RequestValidation() {
    }

    /**
     * Builds a "redirect:" view name for {@code returnUrl} if it is a local, relative path. Anything else
     * (absolute URLs, protocol-relative "//host", "/\host", control characters) falls back to {@code fallback},
     * so the parameter can not be abused as an open redirect.
     */
    public static String safeRedirect(String returnUrl, String fallback) {
        return "redirect:" + (isLocalPath(returnUrl) ? returnUrl : fallback);
    }

    public static boolean isLocalPath(String url) {
        if (url == null || url.isEmpty() || url.charAt(0) != '/') {
            return false;
        }
        if (url.length() > 1 && (url.charAt(1) == '/' || url.charAt(1) == '\\')) {
            return false;
        }
        // browsers treat '\' like '/' and strip tabs/newlines, which would turn "/\t/host" into "//host"
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '\\' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isHexColor(String color) {
        return color != null && HEX_COLOR.matcher(color).matches();
    }
}
