package com.dedicatedcode.reitti.model;

public enum AvailableLanguages {
    ENGLISH("en", "language.english", "\uD83C\uDDFA\uD83C\uDDF8"),
    FINNISH("fi", "language.finnish", "\uD83C\uDDEB\uD83C\uDDEE"),
    GERMAN("de", "language.german", "\uD83C\uDDE9\uD83C\uDDEA"),
    FRENCH("fr", "language.french", "\uD83C\uDDEB\uD83C\uDDF7"),
    POLISH("pl", "language.polish", "\uD83C\uDDF5\uD83C\uDDF1"),
    RUSSIAN("ru", "language.russian", "\uD83C\uDDF7\uD83C\uDDFA"),
    BRAZILIAN_PORTUGUESE("ptbr", "language.brazilian_portuguese", "\uD83C\uDDE7\uD83C\uDDF7"),
    CHINESE("zh_CN", "language.chinese", "\uD83C\uDDE8\uD83C\uDDF3");

    private final String locale;
    private final String messageKey;
    private final String symbol;

    AvailableLanguages(String locale, String messageKey, String symbol) {
        this.locale = locale;
        this.messageKey = messageKey;
        this.symbol = symbol;
    }

    public String getLocale() {
        return locale;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getSymbol() {
        return symbol;
    }
}
