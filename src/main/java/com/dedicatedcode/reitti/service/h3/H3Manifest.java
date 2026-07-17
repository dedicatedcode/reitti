package com.dedicatedcode.reitti.service.h3;

public class H3Manifest {
    private String version;
    private String downloadUrl;
    private String sha256;
    private long sizeBytes;
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}