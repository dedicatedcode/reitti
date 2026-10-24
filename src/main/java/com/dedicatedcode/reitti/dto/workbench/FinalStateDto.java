package com.dedicatedcode.reitti.dto.workbench;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FinalStateDto {
    @JsonProperty("tStart")
    private long tStart;
    @JsonProperty("tEnd")
    private long tEnd;


    public long gettStart() {
        return tStart;
    }

    public void settStart(long tStart) {
        this.tStart = tStart;
    }

    public long gettEnd() {
        return tEnd;
    }

    public void settEnd(long tEnd) {
        this.tEnd = tEnd;
    }
}