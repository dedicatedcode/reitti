package com.dedicatedcode.reitti.dto.workbench;

public class PatchDto {
    private int seq;
    private long tStart;      // epoch milliseconds
    private long tEnd;        // epoch milliseconds
    private String deviceId;  // nullable – null for default device, non‑null for specific device IDs

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String toString() {
        return "PatchDto{" +
                "seq=" + seq +
                ", tStart=" + tStart +
                ", tEnd=" + tEnd +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}