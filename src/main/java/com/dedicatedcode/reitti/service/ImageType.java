package com.dedicatedcode.reitti.service;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum ImageType {
    JPEG("image/jpeg", ".jpg"),
    PNG("image/png", ".png"),
    GIF("image/gif", ".gif"),
    WEBP("image/webp", ".webp");

    private static final Map<String, ImageType> BY_CONTENT_TYPE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ImageType::contentType, type -> type));

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        int parameterStart = contentType.indexOf(';');
        String normalized = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
        return Optional.ofNullable(BY_CONTENT_TYPE.get(normalized.trim().toLowerCase()));
    }
}
