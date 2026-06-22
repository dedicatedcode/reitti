package com.dedicatedcode.reitti.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;

public record MapLibreStyleDefinition(
    Long id,
    String label,
    String mapType,
    String styleInputType,
    String styleUrl,
    Object capabilities) implements Serializable {}
