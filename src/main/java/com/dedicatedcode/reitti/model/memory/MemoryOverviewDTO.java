package com.dedicatedcode.reitti.model.memory;

public class MemoryOverviewDTO {
    private final MemoryDTO memory;
    private final String streamUrl;
    private final String metaDataUrl;

    public MemoryOverviewDTO(MemoryDTO memory, String streamUrl, String metaDataUrl) {
        this.memory = memory;
        this.streamUrl = streamUrl;
        this.metaDataUrl = metaDataUrl;
    }

    public MemoryDTO getMemory() {
        return memory;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public String getMetaDataUrl() {
        return metaDataUrl;
    }
}
