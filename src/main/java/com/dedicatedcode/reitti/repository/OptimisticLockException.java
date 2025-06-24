package com.dedicatedcode.reitti.repository;

public class OptimisticLockException extends Exception {
    public OptimisticLockException(String message) {
        super(message);
    }
}
