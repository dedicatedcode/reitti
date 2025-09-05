package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.model.geocoding.RemoteGeocodeService;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class DefaultGeocodeServiceManager implements GeocodeServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGeocodeServiceManager.class);

    private final GeocodeServiceJdbcService geocodeServiceJdbcService;
    private final GeocodingResponseJdbcService geocodingResponseJdbcService;
    private final List<GeocodeService> fixedGeocodeServices;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int maxErrors;

    public DefaultGeocodeServiceManager(GeocodeServiceJdbcService geocodeServiceJdbcService,
                                        GeocodingResponseJdbcService geocodingResponseJdbcService,
                                        List<GeocodeService> fixedGeocodeServices,
                                        RestTemplate restTemplate,
                                        ObjectMapper objectMapper,
                                        @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.fixedGeocodeServices = fixedGeocodeServices;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.maxErrors = maxErrors;
    }

    @Transactional
    @Override
    public Optional<GeocodeResult> reverseGeocode(SignificantPlace significantPlace) {
        double latitude = significantPlace.getLatitudeCentroid();
        double longitude = significantPlace.getLongitudeCentroid();
        if (!fixedGeocodeServices.isEmpty()) {
            logger.debug("Fixed geocode-service available, will first try this.");
            Optional<GeocodeResult> geocodeResult = callGeocodeService(fixedGeocodeServices, latitude, longitude, true, significantPlace);
            if (geocodeResult.isPresent()) {
                return geocodeResult;
            }
        }
        List<RemoteGeocodeService> availableServices = geocodeServiceJdbcService.findByEnabledTrueOrderByLastUsedAsc();

        if (availableServices.isEmpty()) {
            logger.warn("No enabled geocoding services available");
            return Optional.empty();
        }
        return callGeocodeService(availableServices, latitude, longitude, false, significantPlace);
    }

    private Optional<GeocodeResult> callGeocodeService(List<? extends GeocodeService> availableServices, double latitude, double longitude, boolean photon, SignificantPlace significantPlace) {
        Collections.shuffle(availableServices);

        for (GeocodeService service : availableServices) {
            try {
                Optional<GeocodeResult> result = performGeocode(service, latitude, longitude, photon, significantPlace);
                if (result.isPresent()) {
                    recordSuccess(service);
                    return result;
                }
            } catch (Exception e) {
                logger.warn("Geocoding failed for service [{}]: [{}]", service.getName(), e.getMessage());
                recordError(service);
            }
        }

        return Optional.empty();
    }

    private Optional<GeocodeResult> performGeocode(GeocodeService service, double latitude, double longitude, boolean photon, SignificantPlace significantPlace) {
        String url = service.getUrlTemplate()
                .replace("{lat}", String.valueOf(latitude))
                .replace("{lng}", String.valueOf(longitude));

        logger.info("Geocoding with service [{}] using URL: [{}]", service.getName(), url);

        try {
            String response = restTemplate.getForObject(url, String.class);
            Optional<GeocodeResult> geocodeResult = photon ? extractPhotonResult(response) : extractGeoCodeResult(response);
            if (geocodeResult.isPresent()) {
                geocodingResponseJdbcService.insert(new GeocodingResponse(
                        significantPlace.getId(),
                        response,
                        service.getName(),
                        Instant.now(),
                        GeocodingResponse.GeocodingStatus.SUCCESS,
                        null
                ));
            } else {
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
            geocodingResponseJdbcService.insert(new GeocodingResponse(
                    significantPlace.getId(),
                    null,
                    service.getName(),
                    Instant.now(),
                    status,
                    e.getMessage()
            ));
            throw new RuntimeException("Failed to call geocoding service: " + e.getMessage(), e);
        }
    }

    private Optional<GeocodeResult> extractPhotonResult(String response) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode features = root.path("features");
        if (features.isArray() && !features.isEmpty()) {
            JsonNode properties = features.get(0).path("properties");
            String name = properties.path("name").asText();
            String city = properties.path("city").asText();
            String street = properties.path("street").asText();
            String district = properties.path("district").asText();
            String housenumber = properties.path("housenumber").asText();
            String postcode = properties.path("postcode").asText();
            String countryCode = properties.path("countrycode").asText().toLowerCase();
            SignificantPlace.PlaceType type = determinPlaceType(properties.path("osm_value").asText());
            return Optional.of(new GeocodeResult(name, street, housenumber, city, postcode, district, countryCode, type));
        }
        return Optional.empty();
    }

    private SignificantPlace.PlaceType determinPlaceType(String osmValue) {
        return switch (osmValue) {
            case "office", "commercial", "industrial", "warehouse", "retail" -> SignificantPlace.PlaceType.WORK;
            case "restaurant", "fast_food", "food_court" -> SignificantPlace.PlaceType.RESTAURANT;
            case "cafe", "bar", "pub" -> SignificantPlace.PlaceType.CAFE;
            case "shop", "supermarket", "mall", "marketplace", "department_store", "convenience" ->
                    SignificantPlace.PlaceType.SHOP;
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
            case "bus_stop", "bus_station", "railway_station", "subway_entrance", "tram_stop" ->
                    SignificantPlace.PlaceType.TRAIN_STATION;
            case "airport", "terminal" -> SignificantPlace.PlaceType.AIRPORT;
            case "hotel", "motel", "guest_house" -> SignificantPlace.PlaceType.HOTEL;
            default -> SignificantPlace.PlaceType.OTHER;
        };
    }
 
    private Optional<GeocodeResult> extractGeoCodeResult(String response) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode features = root.path("features");

        if (features.isArray() && !features.isEmpty()) {
            JsonNode properties = features.get(0).path("properties");

            String label;
            String street;
            String city;
            String district;
            String countryCode;
            String osmTypeValue;

            //try to find elements from address;
            JsonNode address = properties.path("address");
            JsonNode geocoding = properties.path("geocoding");
            if (geocoding.isObject()) {
                label = geocoding.path("name").asText();
                if (label.isBlank()) {
                    label = geocoding.path("label").asText();
                }
                street = geocoding.path("street").asText();
                if (street.isBlank()) {
                    street = geocoding.path("road").asText();
                }
                if (geocoding.has("housenumber")) {
                    street = street + " " + geocoding.path("housenumber").asText();
                }
                city = geocoding.path("city").asText();
                district = geocoding.path("city_district").asText();
                if (district.isBlank()) {
                    district = geocoding.path("district").asText();
                }
                if (district.isBlank()) {
                    district = geocoding.path("locality").asText();
                }
                countryCode = geocoding.path("country_code").asText().toLowerCase();
                osmTypeValue = geocoding.path("osm_value").asText();
                if (osmTypeValue.isBlank()) {
                    osmTypeValue = geocoding.path("category").asText();
                }
            } else if  (address.isMissingNode()) {
                //try to find it directly under the root node
                label = properties.path("formatted").asText("");
                street = properties.path("street").asText("");
                city = properties.path("city").asText("");
                district = properties.path("city_district").asText("");
                countryCode = properties.path("country_code").asText("").toLowerCase();
                osmTypeValue = geocoding.path("osm_value").asText();
                if (osmTypeValue.isBlank()) {
                    osmTypeValue = geocoding.path("category").asText();
                }

            } else {
                //there is an address, find it there
                label = properties.path("name").asText("");
                street = address.path("road").asText("");
                city = address.path("city").asText("");
                district = address.path("city_district").asText("");
                countryCode = address.path("country_code").asText("").toLowerCase();
                osmTypeValue = geocoding.path("osm_value").asText();
                if (osmTypeValue.isBlank()) {
                    osmTypeValue = geocoding.path("category").asText();
                }
            }

            Optional<GeocodeResult> result = createGeoCodeResult(label, street, city, district, countryCode, osmTypeValue);
            if (result.isPresent()) {
                return result;
            }
        }

        if (root.has("name") && root.has("address")) {
            String label = root.get("name").asText();
            String street = root.path("address").path("street").asText();
            if (street.isBlank()) {
                street = root.path("address").path("road").asText();
            }
            if (root.path("address").path("house_number").isTextual()) {
                street = street + " " + root.path("address").path("house_number").asText();
            }
            String city = root.path("address").path("city").asText();
            String district =  root.path("address").path("district").asText();
            if (district.isBlank()) {
                district = root.path("address").path("neighbourhood").asText();
            }
            String countryCode = root.path("address").path("country_code").asText();
            Optional<GeocodeResult> result = createGeoCodeResult(label, street, city, district, countryCode, root.path("osm_value").asText());
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private Optional<GeocodeResult> createGeoCodeResult(String label, String street, String city, String district, String countryCode, String placeTypeValue) {
        if (label.isEmpty() && !street.isEmpty()) {
            label = street;
        }
        if (StringUtils.hasText(label)) {
            return Optional.of(new GeocodeResult(label, street, "", city, "", district, countryCode, determinPlaceType(placeTypeValue)));
        }
        return Optional.empty();
    }

    private void recordSuccess(GeocodeService service) {
        if (service instanceof RemoteGeocodeService) {
            geocodeServiceJdbcService.save(((RemoteGeocodeService) service).withLastUsed(Instant.now()));
        }
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
        if (service instanceof RemoteGeocodeService) {
            RemoteGeocodeService update = ((RemoteGeocodeService) service)
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
}
