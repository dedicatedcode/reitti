package com.dedicatedcode.reitti.model.security;

import java.time.Instant;

public record ApiTokenUsage(String token, String name, Instant at, String endpoint, String ip) {
}
