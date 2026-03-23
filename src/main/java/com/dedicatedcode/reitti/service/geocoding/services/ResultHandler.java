package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import tools.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.Optional;

public interface ResultHandler {
    boolean canHandle(GeocoderType type);
    Optional<GeocodeResult> handle(JsonNode root);

    default Optional<GeocodeResult> createGeoCodeResult(String label, String street, String houseNumber, String postcode, String city, String district, String countryCode, String placeTypeValue, String subtypeValue) {
        if (label.isEmpty() && !street.isEmpty()) {
            label = street;
        }
        if (StringUtils.hasText(label)) {
            return Optional.of(new GeocodeResult(
                    label,
                    street,
                    houseNumber,
                    city,
                    postcode,
                    district,
                    countryCode != null ? countryCode.toLowerCase() : null,
                    determinPlaceType(placeTypeValue, subtypeValue))
            );
        }
        return Optional.empty();
    }

    private SignificantPlace.PlaceType determinPlaceType(String osmValue, String subtype) {
        String valueToCheck = (subtype != null && !subtype.isBlank()) ? subtype : osmValue;

        return switch (valueToCheck) {
            case "office", "commercial", "industrial", "warehouse", "retail" -> SignificantPlace.PlaceType.WORK;
            case "restaurant", "fast_food", "food_court" -> SignificantPlace.PlaceType.RESTAURANT;
            case "cafe", "bar", "pub" -> SignificantPlace.PlaceType.CAFE;
            case "shop", "supermarket", "mall", "marketplace", "department_store", "convenience" -> SignificantPlace.PlaceType.SHOP;
            case "hospital", "clinic", "doctors", "dentist", "veterinary" -> SignificantPlace.PlaceType.HOSPITAL;
            case "pharmacy" -> SignificantPlace.PlaceType.PHARMACY;
            case "school", "university", "college", "kindergarten" -> SignificantPlace.PlaceType.SCHOOL;
            case "library" -> SignificantPlace.PlaceType.LIBRARY;
            case "gym", "fitness_centre", "sports_centre", "swimming_pool", "stadium" -> SignificantPlace.PlaceType.GYM;
            case "cinema", "theatre" -> SignificantPlace.PlaceType.CINEMA;
            case "park", "garden", "nature_reserve", "beach", "playground" -> SignificantPlace.PlaceType.PARK;
            case "fuel", "charging_station" -> SignificantPlace.PlaceType.GAS_STATION;
            case "bank", "atm", "bureau_de_change" -> SignificantPlace.PlaceType.BANK;
            case "place_of_worship", "church", "mosque", "synagogue", "temple" -> SignificantPlace.PlaceType.CHURCH;
            case "bus_stop", "bus_station", "railway_station", "subway_entrance", "tram_stop" -> SignificantPlace.PlaceType.TRAIN_STATION;
            case "airport", "terminal" -> SignificantPlace.PlaceType.AIRPORT;
            case "hotel", "motel", "guest_house" -> SignificantPlace.PlaceType.HOTEL;
            default -> SignificantPlace.PlaceType.OTHER;
        };
    }
}
