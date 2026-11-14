package com.dedicatedcode.reitti.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CoverageContinent {
    EUROPE("EU", -1L, "coverage.continent.europe"),
    ASIA("AS", -2L, "coverage.continent.asia"),
    AFRICA("AF", -3L, "coverage.continent.africa"),
    NORTH_AMERICA("NA", -4L, "coverage.continent.north-america"),
    SOUTH_AMERICA("SA", -5L, "coverage.continent.south-america"),
    OCEANIA("OC", -6L, "coverage.continent.oceania"),
    ANTARCTICA("AN", -7L, "coverage.continent.antarctica"),
    OTHER("XX", -99L, "coverage.continent.other");

    private static final Map<String, CoverageContinent> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(CoverageContinent::code, Function.identity()));

    private static final Map<Long, CoverageContinent> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(CoverageContinent::id, Function.identity()));

    private final String code;
    private final long id;
    private final String nameKey;

    CoverageContinent(String code, long id, String nameKey) {
        this.code = code;
        this.id = id;
        this.nameKey = nameKey;
    }

    public String code() {
        return code;
    }

    public long id() {
        return id;
    }

    public String nameKey() {
        return nameKey;
    }

    public static CoverageContinent byCode(String code) {
        return BY_CODE.getOrDefault(code, OTHER);
    }

    public static CoverageContinent byCodeOrNull(String code) {
        return BY_CODE.get(code);
    }

    public static CoverageContinent byId(long id) {
        return BY_ID.get(id);
    }
}
