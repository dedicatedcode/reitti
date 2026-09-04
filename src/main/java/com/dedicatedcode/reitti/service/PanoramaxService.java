package com.dedicatedcode.reitti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PanoramaxService {

    private static final Logger log = LoggerFactory.getLogger(PanoramaxService.class);
    private static final String CACHE_UPSTREAM_HEADER = "X-Reitti-Upstream-Url";
    private static final String USER_AGENT = "Reitti/1.0 (+https://github.com/dedicatedcode/reitti; contact: reitti@dedicatedcode.com)";
    // Roughly 45 meters on each side of the queried position.
    private static final double SEARCH_BBOX_DELTA = 0.0004;

    public record NearbyPicture(String pictureId,
                                String sequenceId,
                                String datetime,
                                String providerName,
                                String licenseUrl,
                                String licenseName,
                                Double lon,
                                Double lat) {
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String tileCacheUrl;

    public PanoramaxService(RestTemplate restTemplate,
                            ObjectMapper objectMapper,
                            @Value("${reitti.panoramax.base-url:}") String baseUrl,
                            @Value("${reitti.ui.tiles.cache.url:}") String tileCacheUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.tileCacheUrl = tileCacheUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        return StringUtils.hasText(baseUrl);
    }

    public Optional<NearbyPicture> findNearby(double latitude, double longitude) {
        return findNearbyList(latitude, longitude, SEARCH_BBOX_DELTA, 1).stream().findFirst();
    }

    public List<NearbyPicture> findNearbyList(double latitude, double longitude, double deltaDegrees, int limit) {
        if (!isEnabled() || limit <= 0 || deltaDegrees <= 0) {
            return List.of();
        }
        String bbox = (longitude - deltaDegrees) + "," + (latitude - deltaDegrees) + ","
                + (longitude + deltaDegrees) + "," + (latitude + deltaDegrees);
        String upstreamUrl = baseUrl + "/search?bbox=" + bbox + "&limit=" + limit;
        try {
            JsonNode root = fetchJson(upstreamUrl);
            JsonNode features = root.path("features");
            if (!features.isArray()) {
                return List.of();
            }
            List<NearbyPicture> pictures = new ArrayList<>();
            for (JsonNode feature : features) {
                parsePicture(feature).ifPresent(pictures::add);
            }
            return pictures;
        } catch (Exception e) {
            log.warn("Unable to query Panoramax for pictures near [{}, {}]: {}", latitude, longitude, e.getMessage());
            return List.of();
        }
    }

    private Optional<NearbyPicture> parsePicture(JsonNode feature) {
        String pictureId = feature.path("id").asString(null);
        if (!StringUtils.hasText(pictureId)) {
            return Optional.empty();
        }
        String sequenceId = feature.path("collection").asString(null);
        String datetime = feature.path("properties").path("datetime").asString(null);
        String providerName = firstProviderName(feature);
        String licenseUrl = licenseLink(feature, "href");
        String licenseTitle = licenseLink(feature, "title");
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        Double lon = coordinates.isArray() && !coordinates.isEmpty() && coordinates.get(0).isNumber()
                ? coordinates.get(0).asDouble() : null;
        Double lat = coordinates.isArray() && coordinates.size() > 1 && coordinates.get(1).isNumber()
                ? coordinates.get(1).asDouble() : null;
        return Optional.of(new NearbyPicture(pictureId,
                sequenceId,
                datetime,
                providerName,
                licenseUrl,
                licenseName(licenseTitle),
                lon,
                lat));
    }

    private JsonNode fetchJson(String upstreamUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        String requestUrl = upstreamUrl;
        if (StringUtils.hasText(tileCacheUrl)) {
            URI upstreamUri = URI.create(upstreamUrl);
            requestUrl = tileCacheUrl + "/panoramax/search" + upstreamUri.getRawPath()
                    + (upstreamUri.getRawQuery() != null ? "?" + upstreamUri.getRawQuery() : "");
            headers.set(CACHE_UPSTREAM_HEADER, upstreamUrl);
        }

        ResponseEntity<String> response = restTemplate.exchange(requestUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Panoramax query failed with status " + response.getStatusCode());
        }
        return objectMapper.readTree(response.getBody());
    }

    private String firstProviderName(JsonNode feature) {
        JsonNode providers = feature.path("providers");
        if (providers.isArray() && !providers.isEmpty()) {
            return providers.get(0).path("name").asString(null);
        }
        return null;
    }

    private String licenseLink(JsonNode feature, String field) {
        JsonNode links = feature.path("links");
        if (!links.isArray()) {
            return null;
        }
        for (JsonNode link : links) {
            if ("license".equals(link.path("rel").asString(null))) {
                return link.path(field).asString(null);
            }
        }
        return null;
    }

    private String licenseName(String licenseTitle) {
        if (!StringUtils.hasText(licenseTitle)) {
            return null;
        }
        if (licenseTitle.endsWith(")") && licenseTitle.contains("(")) {
            return licenseTitle.substring(licenseTitle.lastIndexOf('(') + 1, licenseTitle.length() - 1);
        }
        return licenseTitle;
    }
}
