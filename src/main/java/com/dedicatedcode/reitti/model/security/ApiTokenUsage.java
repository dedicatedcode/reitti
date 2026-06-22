package com.dedicatedcode.reitti.model.security;

import java.time.Instant;

public record ApiTokenUsage(String token, String name, String device, Instant at, String endpoint, String ip) {
}
