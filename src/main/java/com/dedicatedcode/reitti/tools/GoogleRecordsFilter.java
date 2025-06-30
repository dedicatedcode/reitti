package com.dedicatedcode.reitti.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GoogleRecordsFilter {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: GoogleRecordsFilter <file-path>");
            System.exit(1);
        }
        
        String filePath = args[0];
        try {
            loadFile(filePath);
        } catch (IOException e) {
            System.err.println("Error loading file: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void loadFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + filePath);
        }
        
        if (!Files.isRegularFile(path)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }
        
        String content = Files.readString(path);
        System.out.println("File loaded successfully. Size: " + content.length() + " characters");
        
        // Process the file content here
        processContent(content);
    }
    
    private static void processContent(String content) {
        // TODO: Implement content processing logic
        System.out.println("Processing content...");
        System.out.println("First 100 characters: " + 
            content.substring(0, Math.min(100, content.length())));
    }
}
