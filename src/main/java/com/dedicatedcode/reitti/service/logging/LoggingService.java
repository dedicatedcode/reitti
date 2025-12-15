package com.dedicatedcode.reitti.service.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.dedicatedcode.reitti.config.LoggingProperties;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LoggingService {
    
    private final LoggingProperties loggingProperties;
    private final InMemoryLogAppender logAppender;
    
    @Autowired
    public LoggingService(LoggingProperties loggingProperties, InMemoryLogAppender logAppender) {
        this.loggingProperties = loggingProperties;
        this.logAppender = logAppender;
        
        // Initialize the appender with the configured buffer size
        logAppender.setBufferSize(loggingProperties.getBufferSize());
        
        // Add the appender to the root logger
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender.setContext(context);
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }
    
    public void setLoggerLevel(String loggerName, String level) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);
        
        Level logbackLevel = Level.valueOf(level.toUpperCase());
        logger.setLevel(logbackLevel);
    }
    
    public void setBufferSize(int size) {
        if (size > loggingProperties.getMaxBufferSize()) {
            throw new IllegalArgumentException("Buffer size cannot exceed " + loggingProperties.getMaxBufferSize());
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        
        logAppender.setBufferSize(size);
    }
    
    public int getCurrentBufferSize() {
        return logAppender.getBufferSize();
    }
    
    public int getMaxBufferSize() {
        return loggingProperties.getMaxBufferSize();
    }
    
    public List<String> getLogSnapshot() {
        return logAppender.getSnapshot();
    }
    
    public InMemoryLogAppender getLogAppender() {
        return logAppender;
    }

    public String getCurrentLogLevel() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Level level = rootLogger.getLevel();
        return level != null ? level.toString() : "INFO";
    }
}
