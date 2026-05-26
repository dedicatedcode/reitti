package com.dedicatedcode.reitti.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record MapLibreStyleDefinition(
    String id,
    String label,
    String mapType,
    String styleInputType,
    JsonNode styleInput,
    Object capabilities) {
}
