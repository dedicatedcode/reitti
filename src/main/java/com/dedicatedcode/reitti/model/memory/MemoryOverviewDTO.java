package com.dedicatedcode.reitti.model.memory;

public class MemoryOverviewDTO {
    private final MemoryDTO memory;
    private final String rawLocationUrl;

    public MemoryOverviewDTO(MemoryDTO memory, String rawLocationUrl) {
        this.memory = memory;
        this.rawLocationUrl = rawLocationUrl;
    }

    public MemoryDTO getMemory() {
        return memory;
    }

    public String getRawLocationUrl() {
        return rawLocationUrl;
    }
}
