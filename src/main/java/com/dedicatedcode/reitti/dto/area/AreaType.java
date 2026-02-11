package com.dedicatedcode.reitti.dto.area;

import java.util.Optional;

/**
 * Area type.
 * <p>
 * This is hierarchical for now: bigger boundary to smaller boundary.
 * </p>
 * The name should be equal (with same case) as the one in the database for mapping.
 */
public enum AreaType
{
    // Declaration in order: bigger boundary to smaller boundary
    COUNTRY("country"),
    STATE("state"),
    COUNTY("county"),
    CITY("city");

    private final String name;

    AreaType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Optional<AreaType> fromString(String text) {
        for (AreaType b : AreaType.values()) {
            if (b.name.equalsIgnoreCase(text)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }
}
