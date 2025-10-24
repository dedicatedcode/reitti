package com.dedicatedcode.reitti.model.memory;

public class MemoryOverviewDTO {
    private final Memory memory;
    private final String rawLocationUrl;

    public MemoryOverviewDTO(Memory memory, String rawLocationUrl) {
        this.memory = memory;
        this.rawLocationUrl = rawLocationUrl;
    }

    public Memory getMemory() {
        return memory;
    }

    public String getRawLocationUrl() {
        return rawLocationUrl;
    }
}
