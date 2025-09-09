package com.dedicatedcode.reitti.model.geo;

import java.io.Serializable;
import java.time.ZoneId;
import java.util.Objects;

public class SignificantPlace implements Serializable {
    
    private final Long id;
    private final String name;
    private final String address;
    private final String countryCode;
    private final Double latitudeCentroid;
    private final Double longitudeCentroid;
    private final PlaceType type;
    private final ZoneId timezone;
    private final boolean geocoded;
    private final Long version;

    public static SignificantPlace create(Double latitude, Double longitude) {
        return new SignificantPlace(null, null, null, null, latitude, longitude, PlaceType.OTHER, ZoneId.systemDefault(), false, 1L);
    }

    public SignificantPlace(Long id,
                            String name,
                            String address,
                            String countryCode,
                            Double latitudeCentroid,
                            Double longitudeCentroid,
                            PlaceType type, ZoneId timezone,
                            boolean geocoded,
                            Long version) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.countryCode = countryCode;
        this.latitudeCentroid = latitudeCentroid;
        this.longitudeCentroid = longitudeCentroid;
        this.type = type;
        this.timezone = timezone;
        this.geocoded = geocoded;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public Double getLatitudeCentroid() {
        return latitudeCentroid;
    }

    public Double getLongitudeCentroid() {
        return longitudeCentroid;
    }

    public PlaceType getType() {
        return type;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public boolean isGeocoded() {
        return geocoded; 
    }

    public Long getVersion() {
        return version;
    }
    
    // Wither methods
    public SignificantPlace withGeocoded(boolean geocoded) {
        return new SignificantPlace(this.id, this.name, this.address, this.countryCode, this.latitudeCentroid, this.longitudeCentroid, this.type, timezone, geocoded, this.version);
    }

    public SignificantPlace withName(String name) {
        return new SignificantPlace(this.id, name, this.address, this.countryCode, this.latitudeCentroid, this.longitudeCentroid, this.type, timezone, this.geocoded, this.version);
    }

    public SignificantPlace withAddress(String address) {
        return new SignificantPlace(this.id, this.name, address, this.countryCode, this.latitudeCentroid, this.longitudeCentroid, this.type, timezone, this.geocoded, this.version);
    }

    public SignificantPlace withCountryCode(String countryCode) {
        return new SignificantPlace(this.id, this.name, this.address, countryCode, this.latitudeCentroid, this.longitudeCentroid, this.type, timezone, this.geocoded, this.version);
    }

    public SignificantPlace withType(PlaceType type) {
        return new SignificantPlace(this.id, this.name, this.address, this.countryCode, this.latitudeCentroid, this.longitudeCentroid, type, timezone, this.geocoded, this.version);
    }

    public SignificantPlace withId(Long id) {
        return new SignificantPlace(id, this.name, address, this.countryCode, this.latitudeCentroid, this.longitudeCentroid, this.type, timezone, this.geocoded, this.version);
    }

    public SignificantPlace withTimezone(ZoneId timezone) {
        return new SignificantPlace(this.id, this.name, address, this.countryCode, this.latitudeCentroid, this.longitudeCentroid, this.type, timezone, this.geocoded, this.version);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SignificantPlace that = (SignificantPlace) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "SignificantPlace{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }


    public enum PlaceType {
        RESTAURANT("lni-restaurant", "place.type.restaurant"),
        PARK("lni-trees", "place.type.park"),
        SHOP("lni-shopping-basket", "place.type.shop"),
        HOME("lni-home", "place.type.home"),
        WORK("lni-briefcase", "place.type.work"),
        HOSPITAL("lni-hospital", "place.type.hospital"),
        SCHOOL("lni-graduation", "place.type.school"),
        AIRPORT("lni-airplane", "place.type.airport"),
        TRAIN_STATION("lni-train", "place.type.train_station"),
        GAS_STATION("lni-fuel", "place.type.gas_station"),
        HOTEL("lni-bed", "place.type.hotel"),
        BANK("lni-bank", "place.type.bank"),
        PHARMACY("lni-first-aid", "place.type.pharmacy"),
        GYM("lni-dumbbell", "place.type.gym"),
        LIBRARY("lni-library", "place.type.library"),
        CHURCH("lni-church", "place.type.church"),
        CINEMA("lni-camera", "place.type.cinema"),
        CAFE("lni-coffee-cup", "place.type.cafe"),
        OTHER("lni-map-marker", "place.type.other");

        private final String iconClass;
        private final String messageKey;

        PlaceType(String iconClass, String messageKey) {
            this.iconClass = iconClass;
            this.messageKey = messageKey;
        }

        public String getIconClass() {
            return iconClass;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }


}
