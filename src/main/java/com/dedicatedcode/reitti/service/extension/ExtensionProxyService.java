package com.dedicatedcode.reitti.service.extension;

import com.dedicatedcode.reitti.model.extension.ExtensionLayer;
import com.dedicatedcode.reitti.model.extension.ExtensionManifest;
import com.dedicatedcode.reitti.model.extension.ExtensionRegistration;
import com.dedicatedcode.reitti.repository.MapExtensionJdbcService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ExtensionProxyService {
    private final MapExtensionJdbcService repository;
    private final ExtensionManifestService manifestService;
    private final ExtensionHttpClient httpClient;
    private final ExtensionCredentialService credentialService;
    private final ExtensionDataCache cache;
    private final ConcurrentHashMap<String, CompletableFuture<ProxiedResource>> singleFlight = new ConcurrentHashMap<>();

    public ExtensionProxyService(MapExtensionJdbcService repository, ExtensionManifestService manifestService,
                                 ExtensionHttpClient httpClient, ExtensionCredentialService credentialService,
                                 ExtensionDataCache cache) {
        this.repository = repository;
        this.manifestService = manifestService;
        this.httpClient = httpClient;
        this.credentialService = credentialService;
        this.cache = cache;
    }

    public ProxiedResource data(long registrationId, String layerId, String bbox, String zoom, String width, String height, String date) {
        ExtensionRegistration registration = enabledRegistration(registrationId);
        ExtensionManifest manifest = manifestService.parseStored(registration.manifestJson());
        ExtensionLayer layer = manifest.layers().stream().filter(candidate -> candidate.id().equals(layerId)).findFirst()
                .orElseThrow(ExtensionCatalogService.ExtensionNotFoundException::new);
        boolean viewportEnabled = layer.type().equals("polling") && !layer.pollTrigger().equals("interval");
        Viewport viewport = viewportEnabled ? Viewport.parse(bbox, zoom, width, height) : Viewport.parse(null, null, null, null);
        String normalizedDate = normalizeDate(date);
        String query = viewportEnabled ? viewport.query() : "";
        String cacheKey = registrationId + "/" + registration.revision() + "/" + layerId + "/" + query + "/" + normalizedDate;
        var cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return new ProxiedResource(cached.get().body(), cached.get().contentType());
        }
        CompletableFuture<ProxiedResource> flight = new CompletableFuture<>();
        CompletableFuture<ProxiedResource> existingFlight = singleFlight.putIfAbsent(cacheKey, flight);
        if (existingFlight != null) {
            return awaitFlight(existingFlight);
        }
        try {
            cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                ProxiedResource result = new ProxiedResource(cached.get().body(), cached.get().contentType());
                flight.complete(result);
                return result;
            }
            URI endpoint = mergeViewport(URI.create(layer.endpoint()), viewport, viewportEnabled, normalizedDate);
            ExtensionHttpClient.Resource resource = httpClient.fetch(endpoint,
                    credentialsFor(registration, endpoint), ExtensionHttpClient.ResourceType.DATA, layer.format());
            long ttl = layer.type().equals("polling")
                    ? Math.max(1000, Math.min(layer.pollIntervalMs(), 30_000)) : 300_000;
            cache.put(cacheKey, resource.body(), resource.contentType(), ttl);
            ProxiedResource result = new ProxiedResource(resource.body(), resource.contentType());
            flight.complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            flight.completeExceptionally(e);
            throw e;
        } finally {
            singleFlight.remove(cacheKey, flight);
        }
    }

    private ProxiedResource awaitFlight(CompletableFuture<ProxiedResource> flight) {
        try {
            return flight.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new ExtensionHttpClient.UpstreamTimeoutException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtensionException("The extension request was interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
            throw new ExtensionException("The extension upstream request failed", e.getCause());
        }
    }

    public ProxiedResource asset(long registrationId, String assetId) {
        ExtensionRegistration registration = enabledRegistration(registrationId);
        ExtensionManifest manifest = manifestService.parseStored(registration.manifestJson());
        String url = null;
        if (assetId.equals(manifest.iconAssetId())) {
            url = manifest.iconUrl();
        }
        if (url == null) {
            for (ExtensionLayer layer : manifest.layers()) {
                url = layer.assetUrls().get(assetId);
                if (url != null) {
                    break;
                }
            }
        }
        if (url == null || !manifestService.assetId(url).equals(assetId)) {
            throw new ExtensionCatalogService.ExtensionNotFoundException();
        }
        URI assetUri = URI.create(url);
        ExtensionHttpClient.Resource resource = httpClient.fetch(assetUri,
                credentialsFor(registration, assetUri), ExtensionHttpClient.ResourceType.ASSET, null);
        return new ProxiedResource(resource.body(), resource.contentType());
    }

    private String credentialsFor(ExtensionRegistration registration, URI target) {
        URI manifest = URI.create(registration.manifestUrl());
        if (!manifest.getScheme().equalsIgnoreCase(target.getScheme())
                || !manifest.getHost().equalsIgnoreCase(target.getHost())
                || effectivePort(manifest) != effectivePort(target)) {
            return null;
        }
        return credentialService.decrypt(registration.encryptedCredentials());
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private ExtensionRegistration enabledRegistration(long id) {
        return repository.findEnabledById(id).orElseThrow(ExtensionCatalogService.ExtensionNotFoundException::new);
    }

    private String normalizeDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new InvalidViewportException();
        }
    }

    private URI mergeViewport(URI endpoint, Viewport viewport, boolean polling, String date) {
        if (viewport.query().isEmpty() && date == null) {
            return endpoint;
        }
        Map<String, String> additions = new LinkedHashMap<>();
        if (viewport.bbox() != null) additions.put("bbox", viewport.bbox());
        if (viewport.zoom() != null) additions.put("zoom", viewport.zoom().toString());
        if (viewport.width() != null) additions.put("width", viewport.width().toString());
        if (viewport.height() != null) additions.put("height", viewport.height().toString());
        if (date != null) additions.put("date", date);
        List<String> existingKeys = new ArrayList<>();
        String existingQuery = endpoint.getRawQuery();
        if (existingQuery != null) {
            for (String parameter : existingQuery.split("&")) {
                String rawKey = parameter.split("=", 2)[0];
                try {
                    existingKeys.add(URLDecoder.decode(rawKey, StandardCharsets.UTF_8).toLowerCase());
                } catch (IllegalArgumentException e) {
                    throw new ExtensionException("The stored extension endpoint has an invalid query");
                }
            }
        }
        StringBuilder result = new StringBuilder(endpoint.toString());
        boolean first = existingQuery == null || existingQuery.isEmpty();
        if (first && result.toString().endsWith("?")) {
            result.setLength(result.length() - 1);
        }
        for (Map.Entry<String, String> addition : additions.entrySet()) {
            if (existingKeys.contains(addition.getKey().toLowerCase())) {
                continue;
            }
            result.append(first ? '?' : '&');
            first = false;
            result.append(URLEncoder.encode(addition.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(addition.getValue(), StandardCharsets.UTF_8));
        }
        return URI.create(result.toString());
    }

    public record ProxiedResource(byte[] body, String contentType) { }

    private record Viewport(String bbox, Integer zoom, Integer width, Integer height) {
        static Viewport parse(String bbox, String zoom, String width, String height) {
            String normalizedBbox = null;
            if (bbox != null) {
                String[] values = bbox.split(",", -1);
                if (values.length != 4) {
                    throw new InvalidViewportException();
                }
                double[] coordinates = new double[4];
                for (int i = 0; i < values.length; i++) {
                    try {
                        coordinates[i] = Double.parseDouble(values[i]);
                    } catch (NumberFormatException e) {
                        throw new InvalidViewportException();
                    }
                    if (!Double.isFinite(coordinates[i])) {
                        throw new InvalidViewportException();
                    }
                }
                if (coordinates[0] < -180 || coordinates[0] > 180 || coordinates[2] < -180 || coordinates[2] > 180
                        || coordinates[1] < -90 || coordinates[1] > 90 || coordinates[3] < -90 || coordinates[3] > 90
                        || coordinates[0] >= coordinates[2] || coordinates[1] >= coordinates[3]) {
                    throw new InvalidViewportException();
                }
                normalizedBbox = coordinates[0] + "," + coordinates[1] + "," + coordinates[2] + "," + coordinates[3];
            }
            return new Viewport(normalizedBbox, integer(zoom, 0, 30), integer(width, 1, 4096), integer(height, 1, 4096));
        }

        String query() {
            StringBuilder value = new StringBuilder();
            if (bbox != null) value.append("bbox=").append(bbox);
            if (zoom != null) value.append("&zoom=").append(zoom);
            if (width != null) value.append("&width=").append(width);
            if (height != null) value.append("&height=").append(height);
            return value.isEmpty() ? "" : value.substring(1);
        }

        private static Integer integer(String value, int min, int max) {
            if (value == null) {
                return null;
            }
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < min || parsed > max) {
                    throw new InvalidViewportException();
                }
                return parsed;
            } catch (NumberFormatException e) {
                throw new InvalidViewportException();
            }
        }
    }

    public static class InvalidViewportException extends ExtensionException {
        public InvalidViewportException() {
            super("Invalid viewport parameters");
        }
    }
}
