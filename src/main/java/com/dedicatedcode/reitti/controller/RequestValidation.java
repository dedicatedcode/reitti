package com.dedicatedcode.reitti.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Validation helpers for user supplied request parameters that end up in redirects or markup.
 */
public final class RequestValidation {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{3,8}$");

    private RequestValidation() {
    }

    /**
     * Builds a "redirect:" view name for {@code returnUrl} if it points to this application, either as a local path
     * or as an absolute URL with the origin of the current request (the UI passes window.location.href).
     * Anything else falls back to {@code fallback}, so the parameter can not be abused as an open redirect.
     */
    public static String safeRedirect(String returnUrl, String fallback) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        boolean allowed = isLocalPath(returnUrl) || isSameOrigin(request, returnUrl);
        return "redirect:" + (allowed ? returnUrl : fallback);
    }

    static boolean isLocalPath(String url) {
        if (url == null || url.isEmpty() || url.charAt(0) != '/') {
            return false;
        }
        if (url.length() > 1 && (url.charAt(1) == '/' || url.charAt(1) == '\\')) {
            return false;
        }
        // browsers treat '\' like '/' and strip tabs/newlines, which would turn "/\t/host" into "//host"
        return url.chars().noneMatch(c -> c == '\\' || Character.isWhitespace(c) || Character.isISOControl(c));
    }

    private static boolean isSameOrigin(HttpServletRequest request, String url) {
        if (url == null) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        return scheme.equalsIgnoreCase(request.getScheme())
                && uri.getRawUserInfo() == null
                && request.getServerName().equalsIgnoreCase(uri.getHost())
                && effectivePort(uri) == request.getServerPort();
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    public static boolean isHexColor(String color) {
        return color != null && HEX_COLOR.matcher(color).matches();
    }
}
