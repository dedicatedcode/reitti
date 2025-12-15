package com.dedicatedcode.reitti.service.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final List<String> buffer = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private volatile int maxSize = 1000;
    
    public void setBufferSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        
        synchronized (this) {
            this.maxSize = size;
            // Trim buffer if it's now too large
            while (buffer.size() > maxSize) {
                buffer.remove(0);
            }
        }
    }
    
    public int getBufferSize() {
        return maxSize;
    }
    
    public List<String> getSnapshot() {
        return new ArrayList<>(buffer);
    }
    
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }
    
    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }
    
    @Override
    protected void append(ILoggingEvent event) {
        String formattedMessage = formatLogEvent(event);
        
        synchronized (this) {
            buffer.add(formattedMessage);
            // Remove oldest entries if buffer is full
            while (buffer.size() > maxSize) {
                buffer.remove(0);
            }
        }
        
        // Notify all listeners
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(formattedMessage);
            } catch (Exception e) {
                // Ignore listener errors to prevent logging loops
            }
        }
    }
    
    private String formatLogEvent(ILoggingEvent event) {
        String timestamp = Instant.ofEpochMilli(event.getTimeStamp())
                .atZone(ZoneId.systemDefault())
                .format(FORMATTER);
        
        return String.format("%s [%s] %s - %s%n",
                timestamp,
                event.getLevel(),
                event.getLoggerName(),
                event.getFormattedMessage());
    }
}
