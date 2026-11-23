package com.dedicatedcode.reitti.service.extension;

import com.dedicatedcode.reitti.model.extension.ExtensionLayer;
import com.dedicatedcode.reitti.model.extension.ExtensionManifest;
import com.dedicatedcode.reitti.model.extension.ExtensionRegistration;
import com.dedicatedcode.reitti.repository.MapExtensionJdbcService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExtensionCatalogService {
    private final MapExtensionJdbcService repository;
    private final ExtensionManifestService manifestService;
    private final ExtensionHttpClient httpClient;
    private final ExtensionCredentialService credentialService;
    private final ExtensionDataCache cache;

    public ExtensionCatalogService(MapExtensionJdbcService repository, ExtensionManifestService manifestService,
                                   ExtensionHttpClient httpClient, ExtensionCredentialService credentialService,
                                   ExtensionDataCache cache) {
        this.repository = repository;
        this.manifestService = manifestService;
        this.httpClient = httpClient;
        this.credentialService = credentialService;
        this.cache = cache;
    }

    public CatalogResponse catalog() {
        List<ExtensionRegistration> registrations = repository.findEnabled();
        StringBuilder revisionInput = new StringBuilder();
        List<PublicExtension> extensions = new ArrayList<>();
        for (ExtensionRegistration registration : registrations) {
            ExtensionManifest manifest = manifestService.parseStored(registration.manifestJson());
            revisionInput.append(registration.id()).append(':').append(registration.revision()).append(':')
                    .append(registration.manifestId()).append(';');
            extensions.add(toPublic(registration, manifest));
        }
        String revision = manifestService.assetId(revisionInput.toString());
        return new CatalogResponse(revision, extensions);
    }

    public ExtensionRegistration create(String url, String bearerToken) {
        String token = normalizeToken(bearerToken);
        FetchedManifest fetched = fetch(url, token);
        String encrypted = credentialService.encrypt(token);
        ExtensionRegistration registration = new ExtensionRegistration(null, fetched.manifest().id(), fetched.manifest().name(),
                fetched.manifest().version(), fetched.url(), fetched.json(), true, encrypted, 1, Instant.now(), true, null);
        try {
            return repository.save(registration);
        } catch (DataIntegrityViolationException e) {
            throw new ExtensionException("This extension or manifest URL is already registered");
        }
    }

    public ExtensionRegistration update(long id, String url, String bearerToken, boolean clearCredentials) {
        ExtensionRegistration existing = required(id);
        URI updatedManifestUri = httpClient.validateUrl(url);
        boolean sameOrigin = sameOrigin(URI.create(existing.manifestUrl()), updatedManifestUri);
        String suppliedToken = normalizeToken(bearerToken);
        String fetchToken = suppliedToken != null ? suppliedToken
                : clearCredentials || !sameOrigin ? null : credentialService.decrypt(existing.encryptedCredentials());
        FetchedManifest fetched = fetch(updatedManifestUri.toString(), fetchToken);
        String encrypted = suppliedToken != null ? credentialService.encrypt(suppliedToken)
                : clearCredentials || !sameOrigin ? null : existing.encryptedCredentials();
        ExtensionRegistration updated = existing.withCredentials(encrypted)
                .withManifest(fetched.manifest(), fetched.url(), fetched.json(), Instant.now());
        try {
            ExtensionRegistration saved = repository.save(updated);
            cache.invalidate(id);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ExtensionException("This extension or manifest URL is already registered");
        }
    }

    public void refresh(long id) {
        ExtensionRegistration existing = required(id);
        try {
            String token = credentialService.decrypt(existing.encryptedCredentials());
            FetchedManifest fetched = fetch(existing.manifestUrl(), token);
            repository.save(existing.withManifest(fetched.manifest(), fetched.url(), fetched.json(), Instant.now()));
        } catch (ExtensionException e) {
            repository.recordRefreshFailure(id, existing.revision(), sanitizeError(e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            repository.recordRefreshFailure(id, existing.revision(), "The refreshed manifest conflicts with another registration");
        } finally {
            cache.invalidate(id);
        }
    }

    public void toggle(long id) {
        ExtensionRegistration registration = required(id);
        repository.save(registration.withEnabled(!registration.enabled()));
        cache.invalidate(id);
    }

    public void delete(long id) {
        required(id);
        repository.delete(id);
        cache.invalidate(id);
    }

    public ExtensionRegistration required(long id) {
        return repository.findById(id).orElseThrow(() -> new ExtensionNotFoundException());
    }

    private FetchedManifest fetch(String url, String token) {
        URI uri = httpClient.validateUrl(url);
        ExtensionHttpClient.Resource resource = httpClient.fetch(uri, token, ExtensionHttpClient.ResourceType.MANIFEST, "json");
        ExtensionManifest manifest = manifestService.parse(resource.body(), uri);
        validateEndpoints(manifest);
        return new FetchedManifest(uri.toString(), manifest, manifestService.serialize(manifest));
    }

    private void validateEndpoints(ExtensionManifest manifest) {
        if (manifest.iconUrl() != null) {
            httpClient.validateUrl(URI.create(manifest.iconUrl()));
        }
        for (ExtensionLayer layer : manifest.layers()) {
            httpClient.validateUrl(URI.create(layer.endpoint()));
            layer.assetUrls().values().forEach(url -> httpClient.validateUrl(URI.create(url)));
        }
    }

    private PublicExtension toPublic(ExtensionRegistration registration, ExtensionManifest manifest) {
        String base = "/api/v1/extensions/" + registration.id();
        String iconUrl = manifest.iconAssetId() == null ? null : base + "/assets/" + manifest.iconAssetId();
        List<PublicLayer> layers = manifest.layers().stream().map(layer -> {
            Map<String, Object> assets = new LinkedHashMap<>(layer.assets());
            for (String field : List.of("iconUrl", "modelUrl", "trainModelUrl", "imageUrl")) {
                rewriteAssetUrl(assets, field, base);
            }
            if (assets.get("models") instanceof Map<?, ?> models) {
                for (Object definition : models.values()) {
                    if (definition instanceof Map<?, ?> model) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mutableModel = (Map<String, Object>) model;
                        rewriteAssetUrl(mutableModel, "modelUrl", base);
                    }
                }
            }
            return new PublicLayer(layer.id(), layer.name(), layer.description(), layer.type(), layer.protocol(),
                    base + "/layers/" + layer.id() + "/data", layer.format(), layer.dataPath(), layer.pollIntervalMs(),
                    layer.pollTrigger(), layer.renderer(), Collections.unmodifiableMap(assets),
                    Collections.unmodifiableMap(new LinkedHashMap<>(layer.mapping())),
                    layer.minZoom(), layer.staleAfterMs(), layer.placement(), layer.zIndex());
        }).toList();
        return new PublicExtension(registration.id(), manifest.id(), manifest.name(), manifest.version(),
                manifest.description(), iconUrl, layers);
    }

    private void rewriteAssetUrl(Map<String, Object> assets, String field, String base) {
        if (assets.get(field) instanceof String value) {
            assets.put(field, base + "/assets/" + manifestService.assetId(value));
        }
    }

    private boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.length() > 4096 || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new ExtensionException("The bearer token is invalid");
        }
        return trimmed;
    }

    private String sanitizeError(String message) {
        if (message == null || message.isBlank()) {
            return "Extension refresh failed";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]", " ");
        return sanitized.substring(0, Math.min(1000, sanitized.length()));
    }

    private record FetchedManifest(String url, ExtensionManifest manifest, String json) { }

    public record CatalogResponse(String revision, List<PublicExtension> extensions) { }

    public record PublicExtension(long id, String manifestId, String name, String version, String description,
                                  String iconUrl, List<PublicLayer> layers) { }

    public record PublicLayer(String id, String name, String description, String type, String protocol, String dataUrl,
                              String format, String dataPath, Long pollIntervalMs, String pollTrigger, String renderer,
                              Map<String, Object> assets, Map<String, Object> mapping, Integer minZoom, Long staleAfterMs,
                              String placement, Integer zIndex) { }

    public static class ExtensionNotFoundException extends ExtensionException {
        public ExtensionNotFoundException() {
            super("Extension registration not found");
        }
    }
}
