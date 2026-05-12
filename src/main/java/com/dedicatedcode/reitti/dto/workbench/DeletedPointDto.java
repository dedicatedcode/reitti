package com.dedicatedcode.reitti.dto.workbench;

public class DeletedPointDto {
    private Long sourceId;    // original point identifier (from database)

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }
}