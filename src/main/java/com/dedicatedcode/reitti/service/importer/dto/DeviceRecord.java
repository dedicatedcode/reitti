package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceRecord {
    
    @JsonProperty("mac")
    private Long mac;
    
    @JsonProperty("rawRssi")
    private Integer rawRssi;
    
    public Long getMac() {
        return mac;
    }
    
    public void setMac(Long mac) {
        this.mac = mac;
    }
    
    public Integer getRawRssi() {
        return rawRssi;
    }
    
    public void setRawRssi(Integer rawRssi) {
        this.rawRssi = rawRssi;
    }
}
