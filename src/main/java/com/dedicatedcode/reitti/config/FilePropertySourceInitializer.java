package com.dedicatedcode.reitti.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FilePropertySourceInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Map<String, Object> properties = new HashMap<>();

        // Iterate through all properties to find ones ending with _FILE
        applicationContext.getEnvironment().getPropertySources().forEach(source -> {
            if (source instanceof MapPropertySource cps) {
                Arrays.stream(cps.getPropertyNames()).forEach(name -> {
                    if (name.endsWith("_FILE")) {
                        String filePath = Objects.requireNonNull(cps.getProperty(name)).toString();
                        try {
                            String content = new String(Files.readAllBytes(Paths.get(filePath))).trim();
                            // Create a new property name without the _FILE suffix
                            String baseName = name.substring(0, name.length() - 5);
                            properties.put(baseName, content);
                        } catch (Exception e) {
                            System.err.println("Error reading file for property " + name + ": " + e.getMessage());
                        }
                    }
                });
            }
        });

        if (!properties.isEmpty()) {
            // Add the resolved properties to the environment with high precedence
            MapPropertySource propertySource = new MapPropertySource("fileProperties", properties);
            applicationContext.getEnvironment().getPropertySources().addFirst(propertySource);
        }
    }
}