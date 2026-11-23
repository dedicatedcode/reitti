package com.dedicatedcode.reitti.service.extension;

public class ExtensionException extends RuntimeException {
    public ExtensionException(String message) {
        super(message);
    }

    public ExtensionException(String message, Throwable cause) {
        super(message, cause);
    }
}
