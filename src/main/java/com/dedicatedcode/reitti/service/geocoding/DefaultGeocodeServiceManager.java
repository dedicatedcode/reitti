package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.repository.GeocodeServiceJdbcService;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Service
public class DefaultGeocodeServiceManager implements GeocodeServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGeocodeServiceManager.class);

    private final GeocodeServiceJdbcService geocodeServiceJdbcService;
    private final GeocodingResponseJdbcService geocodingResponseJdbcService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int maxErrors;

    public DefaultGeocodeServiceManager(GeocodeServiceJdbcService geocodeServiceJdbcService,
                                        GeocodingResponseJdbcService geocodingResponseJdbcService,
                                        RestTemplate restTemplate,
                                        ObjectMapper objectMapper,
                                        @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.maxErrors = maxErrors;
    }

    @Transactional
    @Override
    public Optional<GeocodeResult> reverseGeocode(SignificantPlace significantPlace, boolean recordResponse) {
        double latitude = significantPlace.getLatitudeCentroid();
        double longitude = significantPlace.getLongitudeCentroid();
        List<GeocodeService> availableServices = geocodeServiceJdbcService.findByEnabledTrueOrderByPriority();

        if (availableServices.isEmpty()) {
            logger.warn("No enabled geocoding services available");
            return Optional.empty();
        }
        return callGeocodeService(availableServices, latitude, longitude, significantPlace, recordResponse);
    }

    @Override
    public GeocodeResult test(GeocodeService service, double testLat, double testLng) {
        Optional<GeocodeResult> geocodeResult = performGeocode(service, testLat, testLng, null, false);
        return geocodeResult.orElseThrow(() -> new RuntimeException("Failed to call geocoding service: " + service.getName()));
    }

    private Optional<GeocodeResult> callGeocodeService(List<? extends GeocodeService> availableServices, double latitude, double longitude, SignificantPlace significantPlace, boolean recordResponse) {
        Collections.shuffle(availableServices);

        for (GeocodeService service : availableServices) {
            try {
                Optional<GeocodeResult> result = performGeocode(service, latitude, longitude, significantPlace, recordResponse);
                if (result.isPresent()) {
                    if (recordResponse) {
                        recordSuccess(service);
                    }
                    return result;
                }
            } catch (Exception e) {
                logger.warn("Geocoding failed for service [{}]: [{}]", service.getName(), e.getMessage());
                if (recordResponse) {
                    recordError(service);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<GeocodeResult> performGeocode(GeocodeService service, double latitude, double longitude, SignificantPlace significantPlace, boolean recordResponse) {
        String url = service.getUrlTemplate()
                .replace("{lat}", String.valueOf(latitude))
                .replace("{lng}", String.valueOf(longitude));

        logger.info("Geocoding with service [{}] using URL: [{}]", service.getName(), url);

        try {
            String response = restTemplate.getForObject(url, String.class);
            Optional<GeocodeResult> geocodeResult = extractGeoCodeResult(service.getType(), response);
            if (recordResponse && geocodeResult.isPresent()) {
                geocodingResponseJdbcService.insert(new GeocodingResponse(
                        significantPlace.getId(),
                        response,
                        service.getName(),
                        Instant.now(),
                        GeocodingResponse.GeocodingStatus.SUCCESS,
                        null
                ));
            } else if (recordResponse){
                geocodingResponseJdbcService.insert(new GeocodingResponse(
                        significantPlace.getId(),
                        response,
                        service.getName(),
                        Instant.now(),
                        GeocodingResponse.GeocodingStatus.ZERO_RESULTS,
                        null
                ));
            }
            return geocodeResult;

        } catch (Exception e) {
            logger.error("Failed to call geocoding service [{}]: [{}]", service.getName(), e.getMessage());
            GeocodingResponse.GeocodingStatus status = determineErrorStatus(e);
            if (recordResponse) {
                geocodingResponseJdbcService.insert(new GeocodingResponse(
                        significantPlace.getId(),
                        null,
                        service.getName(),
                        Instant.now(),
                        status,
                        e.getMessage()
                ));
            }
            throw new RuntimeException("Failed to call geocoding service: " + e.getMessage(), e);
        }
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

    private Optional<GeocodeResult> extractGeoCodeResult(GeocoderType type, String response) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(response);
        return switch (type) {
            case PAIKKA -> extractPaikkaResult(root);
            case NOMINATIM -> extractNominatimResult(root);
            case GEO_APIFY -> extractGeoApifyResult(root);
            case PHOTON -> extractPhotonResult(root);
            case GEOCODE_JSON -> extractGeocodeJsonResult(root);
        };
    }

    private Optional<GeocodeResult> extractPaikkaResult(JsonNode root) {
        JsonNode resultsNode = root.path("results");
        if (!resultsNode.isArray() || resultsNode.isEmpty()) return Optional.empty();

        List<JsonNode> resultList = new ArrayList<>();
        resultsNode.forEach(resultList::add);

        JsonNode best = resultList.stream()
                .min(Comparator.comparingInt((JsonNode n) -> getPaikkaTypePriority(n.path("type").asText()))
                             .thenComparing((JsonNode n) -> !n.path("display_name").asText().isEmpty(), Comparator.reverseOrder())
                             .thenComparingDouble(n -> n.path("distance_km").asDouble()))
                .orElse(null);

        if (best == null) return Optional.empty();

        String label = best.path("display_name").asText();
        if (label.isBlank()) label = best.path("names").path("default").asText();

        JsonNode addr = best.path("address");
        String street = addr.path("street").asText("");
        String houseNumber = addr.path("house_number").asText("");
        String postcode = addr.path("postcode").asText("");
        String city = addr.path("city").asText("");

        String district = "";
        String countryCode = "";
        for (JsonNode level : best.path("hierarchy")) {
            if (level.path("level").asInt() == 10) district = level.path("name").asText();
            if (level.path("level").asInt() == 2) countryCode = level.path("country_code").asText();
        }

        return createGeoCodeResult(
                label,
                street.trim(),
                houseNumber,
                postcode,
                city,
                district,
                countryCode,
                best.path("type").asText(),
                best.path("subtype").asText()
        );
    }

    private Optional<GeocodeResult> extractNominatimResult(JsonNode root) {
        if (!root.isArray() || root.isEmpty()) return Optional.empty();

        List<JsonNode> resultList = new ArrayList<>();
        root.forEach(resultList::add);

        JsonNode best = resultList.stream()
                .max(Comparator.comparingDouble((JsonNode n) -> n.path("importance").asDouble()))
                .orElse(root.get(0));

        JsonNode addr = best.path("address");
        String label = best.path("display_name").asText();
        String street = addr.path("road").asText("");
        String city = addr.path("city").asText(addr.path("town").asText(addr.path("village").asText("")));
        String district = addr.path("suburb").asText(addr.path("neighbourhood").asText(""));
        String countryCode = addr.path("country_code").asText();

        return createGeoCodeResult(label, street.trim(), addr.path("house_number").asText("").trim(), addr.path("post_code").asText("").trim(), city, district, countryCode, best.path("type").asText(), null);
    }

    private Optional<GeocodeResult> extractGeoApifyResult(JsonNode root) {
        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) return Optional.empty();

        // Geoapify provides a "rank" object (match_type, confidence)
        List<JsonNode> featureList = new ArrayList<>();
        features.forEach(featureList::add);

        JsonNode best = featureList.stream()
                .max(Comparator.comparingDouble((JsonNode n) -> n.path("properties").path("rank").path("confidence").asDouble()))
                .orElse(features.get(0));

        JsonNode props = best.path("properties");
        return createGeoCodeResult(
                props.path("formatted").asText(),
                props.path("street").asText(""),
                props.path("housenumber").asText(""),
                props.path("postcode").asText(""),
                props.path("city").asText(),
                props.path("district").asText(),
                props.path("country_code").asText(),
                props.path("category").asText(), null
        );
    }

    private Optional<GeocodeResult> extractPhotonResult(JsonNode root) {
        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) return Optional.empty();

        JsonNode props = features.get(0).path("properties");
        String street = props.path("street").asText("") + " " + props.path("housenumber").asText("").trim();

        return createGeoCodeResult(
                props.path("name").asText(props.path("street").asText()),
                street.trim(),
                props.path("city").asText(),
                props.path("district").asText(),
                props.path("countrycode").asText(),
                props.path("osm_value").asText(), null, "", ""
        );
    }

    private Optional<GeocodeResult> extractGeocodeJsonResult(JsonNode root) {
        JsonNode feature = root.path("features").path(0);
        if (feature.isMissingNode()) return Optional.empty();

        JsonNode geocoding = feature.path("properties").path("geocoding");
        String label = geocoding.path("label").asText(geocoding.path("name").asText());
        String street = geocoding.path("street").asText("") + " " + geocoding.path("housenumber").asText("").trim();

        return createGeoCodeResult(
                label,
                street.trim(),
                geocoding.path("city").asText(),
                geocoding.path("district").asText(),
                geocoding.path("country_code").asText(),
                geocoding.path("type").asText(), null, "", ""
        );
    }

    private int getPaikkaTypePriority(String type) {
        return switch (type) {
            case "building" -> 1;
            case "tourism" -> 2;
            case "place" -> 3;
            case "amenity" -> 4;
            default -> 10;
        };
    }

    private Optional<GeocodeResult> createGeoCodeResult(String label, String street, String houseNumber, String postcode, String city, String district, String countryCode, String placeTypeValue, String subtypeValue) {
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
                    countryCode,
                    determinPlaceType(placeTypeValue, subtypeValue))
            );
        }
        return Optional.empty();
    }

    private void recordSuccess(GeocodeService service) {
        geocodeServiceJdbcService.save(service.withLastUsed(Instant.now()));
    }

    private GeocodingResponse.GeocodingStatus determineErrorStatus(Exception e) {
        String message = e.getMessage().toLowerCase();
        
        if (message.contains("rate limit") || message.contains("too many requests") || 
            message.contains("429") || message.contains("quota exceeded")) {
            return GeocodingResponse.GeocodingStatus.RATE_LIMITED;
        }
        
        if (message.contains("invalid") || message.contains("bad request") || 
            message.contains("400") || message.contains("malformed")) {
            return GeocodingResponse.GeocodingStatus.INVALID_REQUEST;
        }
        
        return GeocodingResponse.GeocodingStatus.ERROR;
    }

    private void recordError(GeocodeService service) {
            GeocodeService update = service
                    .withIncrementedErrorCount()
                    .withLastError(Instant.now());

            if (update.getErrorCount() >= maxErrors) {
                update = update.withEnabled(false);
                logger.warn("Geocoding service [{}] disabled due to too many errors ([{}]/[{}])",
                        update.getName(), update.getErrorCount(), maxErrors);
            }

            geocodeServiceJdbcService.save(update);
    }
}
