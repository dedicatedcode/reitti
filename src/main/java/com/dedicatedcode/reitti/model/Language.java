package com.dedicatedcode.reitti.model;

import java.util.Locale;

public enum Language {
    EN(Locale.ENGLISH, "language.english", "\uD83C\uDDFA\uD83C\uDDF8"),
    FI(Locale.of("fi"), "language.finnish", "\uD83C\uDDEB\uD83C\uDDEE"),
    DE(Locale.of("de"), "language.german", "\uD83C\uDDE9\uD83C\uDDEA"),
    NL(Locale.of("nl"), "language.dutch", "\uD83C\uDDF3\uD83C\uDDF1"),
    FR(Locale.FRANCE, "language.french", "\uD83C\uDDEB\uD83C\uDDF7"),
    PL(Locale.of("pl"), "language.polish", "\uD83C\uDDF5\uD83C\uDDF1"),
    RU(Locale.of("ru"), "language.russian", "\uD83C\uDDF7\uD83C\uDDFA"),
    PT_BR(Locale.of("pt", "br"), "language.brazilian_portuguese", "\uD83C\uDDE7\uD83C\uDDF7"),
    ZH_CN(Locale.of("zh", "cn"), "language.chinese", "\uD83C\uDDE8\uD83C\uDDF3");

    private final Locale locale;
    private final String messageKey;
    private final String symbol;

    Language(Locale locale, String messageKey, String symbol) {
        this.locale = locale;
        this.messageKey = messageKey;
        this.symbol = symbol;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getSymbol() {
        return symbol;
    }
}
