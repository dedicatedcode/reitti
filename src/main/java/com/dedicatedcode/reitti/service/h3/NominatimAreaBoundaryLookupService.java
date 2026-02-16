package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.dto.area.AreaBounds;
import com.dedicatedcode.reitti.dto.area.AreaDescription;
import com.dedicatedcode.reitti.repository.h3.AreaJdbcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnExpression(
    "${reitti.h3.area-mapping.enabled:false} && T(org.springframework.util.StringUtils).hasText('${reitti.h3"
        + ".nominatim.base-url:}')")
public class NominatimAreaBoundaryLookupService extends AreaBoundaryLookupBase implements AreaBoundaryLookupService
{
    private static final String VIEW_BOX_TEMPLATE = "&viewbox={minLon},{minLat},{maxLon},{maxLat}";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String baseUrl;

    public NominatimAreaBoundaryLookupService(@Value("${reitti.h3.nominatim.base-url}") String baseUrl,
                                              AreaJdbcService areaJdbcService)
    {
        super(areaJdbcService);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String getName()
    {
        return "Local Nominatim";
    }

    public String getUrlTemplate()
    {
        return baseUrl
            + "/search?{type}={name}&format=jsonv2&polygon_geojson=1&type=administrative&limit=1&namedetails=1"
            + "&bounded=1";
    }

    protected String userAgent()
    {
        return "Reitti/(TODO: version) (Personal Location Tracking Boundary Request)";
    }

    @Override
    public Optional<String> getAreaBoundaryGeoJson(AreaDescription areaDescription, @Nullable AreaBounds areaBounds)
        throws IOException, InterruptedException
    {
        //TODO: maybe also return name list?
        //TODO: handle multiple returns?
        String url = getUrlTemplate()
            .replace("{type}", URLEncoder.encode(areaDescription.type().toString(), StandardCharsets.UTF_8))
            .replace("{name}", URLEncoder.encode(areaDescription.name(), StandardCharsets.UTF_8));
        if (areaBounds != null)
        {
            url += VIEW_BOX_TEMPLATE
                .replace("{minLon}", String.valueOf(areaBounds.minLon()))
                .replace("{minLat}", String.valueOf(areaBounds.minLat()))
                .replace("{maxLon}", String.valueOf(areaBounds.maxLon()))
                .replace("{maxLat}", String.valueOf(areaBounds.maxLat()));
        }

        HttpRequest request =
            HttpRequest.newBuilder().header("User-Agent", userAgent()).uri(URI.create(url)).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
        {
            throw new RuntimeException("HTTP Error: " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());

        // Nominatim returns an array; we check if it's empty
        if (root.isArray() && !root.isEmpty())
        {
            // Get the 'geojson' node from the first search result
            JsonNode geoJsonNode = root.get(0).get("geojson");
            var geoJsonType = geoJsonNode.get("type").asText();
            if (!"MultiPolygon".equals(geoJsonType) && !"Polygon".equals(geoJsonType))
            {
                return Optional.empty();
            }
            return Optional.of(mapper.writeValueAsString(geoJsonNode));
        }

        return Optional.empty();
    }

    @Override
    public Optional<String> getAreaBoundaryGeoJson(AreaDescription areaDescription,
                                                   List<AreaDescription> parentAreaDescriptions)
        throws IOException, InterruptedException
    {
        return getAreaBoundaryGeoJson(areaDescription, this.getGeoFence(parentAreaDescriptions).orElse(null));
    }

    @Override
    public int getOrder()
    {
        return 0;
    }
}

