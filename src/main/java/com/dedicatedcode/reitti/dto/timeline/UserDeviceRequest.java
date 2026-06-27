package com.dedicatedcode.reitti.dto.timeline;

public record UserDeviceRequest(String userId, Long deviceId) {
    public static UserDeviceRequest from(String request) {
        try {
            if (request == null || request.equals("null")) {
                return null;
            }
            if (request.contains("_")) {
                String[] parts = request.split("_");
                return new UserDeviceRequest(parts[0], Long.parseLong(parts[1]));
            } else {
                return new UserDeviceRequest(request, null);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid user device request", e);
        }
    }
}
