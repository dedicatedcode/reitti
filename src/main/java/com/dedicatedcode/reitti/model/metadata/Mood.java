package com.dedicatedcode.reitti.model.metadata;

public enum Mood {
    HAPPY("\uD83D\uDE0A"),
    RELAXED("\uD83D\uDE0C"),
    ADVENTUROUS("\uD83E\uDD20"),
    TIRED("\uD83D\uDE34"),
    STRESSED("\uD83D\uDE2B");

    private final String icon;

    Mood(String icon) {
        this.icon = icon;
    }

    public String getIcon() {
        return icon;
    }

    public static Mood fromString(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return Mood.valueOf(value.toUpperCase());
    }
}