package com.dedicatedcode.reitti.model;

import java.time.LocalDateTime;

public record ApiTokenUsage(String token, String name, LocalDateTime at, String endpoint, String ip) {
}
