package com.dedicatedcode.reitti.dto;

import java.io.Serializable;

public record MapLibreStyleDefinition(
    Long id,
    String label,
    String mapType,
    String styleInputType,
    String styleUrl,
    Object capabilities) implements Serializable {}
