package com.dedicatedcode.reitti.service.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.dedicatedcode.reitti.config.LoggingProperties;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

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
    
    public List<LoggerInfo> getAllConfiguredLoggers() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        List<LoggerInfo> loggers = new ArrayList<>();
        
        // Add root logger
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Level rootLevel = rootLogger.getLevel();
        if (rootLevel != null) {
            loggers.add(new LoggerInfo("ROOT", rootLevel.toString()));
        }
        
        // Add all other configured loggers
        for (Logger logger : context.getLoggerList()) {
            if (logger.getLevel() != null && !Logger.ROOT_LOGGER_NAME.equals(logger.getName())) {
                loggers.add(new LoggerInfo(logger.getName(), logger.getLevel().toString()));
            }
        }
        
        return loggers.stream()
                .sorted((a, b) -> {
                    // ROOT logger first, then alphabetically
                    if ("ROOT".equals(a.getName())) return -1;
                    if ("ROOT".equals(b.getName())) return 1;
                    return a.getName().compareTo(b.getName());
                })
                .collect(Collectors.toList());
    }
    
    public void removeLogger(String loggerName) {
        if ("ROOT".equals(loggerName)) {
            throw new IllegalArgumentException("Cannot remove ROOT logger");
        }
        
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(loggerName);
        logger.setLevel(null); // Reset to inherit from parent
    }
    
    public static class LoggerInfo {
        private final String name;
        private final String level;
        
        public LoggerInfo(String name, String level) {
            this.name = name;
            this.level = level;
        }
        
        public String getName() {
            return name;
        }
        
        public String getLevel() {
            return level;
        }
    }
}
