package com.dedicatedcode.reitti.service.security;

/**
 * Thrown when a user supplied URL or host must not be contacted by the server.
 * The message is safe to show to the user.
 */
public class UnsafeUrlException extends IllegalArgumentException {
    public UnsafeUrlException(String message) {
        super(message);
    }
}
