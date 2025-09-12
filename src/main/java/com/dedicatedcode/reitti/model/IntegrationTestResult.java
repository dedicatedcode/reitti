package com.dedicatedcode.reitti.model;

public record IntegrationTestResult(boolean success, String message) {
    public static IntegrationTestResult failed() {
        return new IntegrationTestResult(false, null);
    }

    public static IntegrationTestResult failed(String message) {
        return new  IntegrationTestResult(false, message);
    }

    public static IntegrationTestResult ok() {
        return new IntegrationTestResult(true, null);
    }

}
