package com.dedicatedcode.reitti.model.metadata;

public enum Mood {
    HAPPY,
    RELAXED,
    ADVENTUROUS,
    TIRED,
    STRESSED;
    
    public static Mood fromString(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Mood.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // Fallback for invalid values
        }
    }
}