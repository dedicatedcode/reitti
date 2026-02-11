package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.dto.area.AreaDescription;
import com.dedicatedcode.reitti.dto.area.AreaType;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.service.h3.AreaReverseLookupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnExpression(
        "T(org.springframework.util.StringUtils).hasText('${reitti.geocoding.photon.base-url:}')"
)
public class PhotonGeocodeService implements GeocodeService, AreaReverseLookupService
{

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final String baseUrl;

    public PhotonGeocodeService(@Value("${reitti.geocoding.photon.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
    @Override
    public String getName() {
        return "Photon";
    }

    @Override
    public String getUrlTemplate() {
        return baseUrl + "/reverse?lon={lng}&lat={lat}&limit=10&layer=house&layer=locality&radius=0.03";
    }

    @Override
    public List<AreaDescription> getAreaHierarchy(GeoPoint point) throws IOException, InterruptedException
    {
        String query = String.format("?lat=%f&lon=%f", point.latitude(), point.longitude());

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/reverse" + query)).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
        {
            throw new RuntimeException("HTTP Error: " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());

        Map<String, String> areas = new java.util.HashMap<>();

        JsonNode features = root.get("features");

        if (features == null || !features.isArray() || features.isEmpty())
        {
            return List.of();
        }

        JsonNode properties = features.get(0).get("properties");

        if (properties == null)
        {
            return List.of();
        }

        if (properties.has("country"))
        {
            areas.put("country", properties.get("country").asText());
        }
        if (properties.has("state"))
        {
            areas.put("state", properties.get("state").asText());
        }
        if (properties.has("county"))
        {
            areas.put("county", properties.get("county").asText());
        }
        if (properties.has("city"))
        {
            areas.put("city", properties.get("city").asText());
        }
        if (properties.has("town"))
        {
            areas.put("city", properties.get("town").asText());
        }
        if (properties.has("village"))
        {
            areas.put("city", properties.get("village").asText());
        }

        return areas
            .entrySet()
            .stream()
            .map(b -> new AreaDescription(AreaType
                .fromString(b.getKey())
                .orElseThrow(() -> new IllegalArgumentException("Invalid area type: " + b.getKey())), b.getValue()))
            .sorted(Comparator.comparing(AreaDescription::type))
            .collect(Collectors.toList());
    }
}
