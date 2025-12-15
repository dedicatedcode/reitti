package com.dedicatedcode.reitti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "reitti.logging")
public class LoggingProperties {
    
    private int bufferSize = 1000;
    private int maxBufferSize = 10000;
    
    public int getBufferSize() {
        return bufferSize;
    }
    
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
    
    public int getMaxBufferSize() {
        return maxBufferSize;
    }
    
    public void setMaxBufferSize(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }
}
