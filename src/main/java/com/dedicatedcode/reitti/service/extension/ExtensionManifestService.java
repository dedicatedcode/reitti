package com.dedicatedcode.reitti.service.extension;

import com.dedicatedcode.reitti.model.extension.ExtensionLayer;
import com.dedicatedcode.reitti.model.extension.ExtensionManifest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ExtensionManifestService {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Set<String> RENDERERS = Set.of("PathLayer", "PolygonLayer", "IconLayer", "BitmapLayer", "ScenegraphLayer");
    private static final Set<String> GEOMETRIC_FORMATS = Set.of("json", "geojson", "gpx");
    private static final Set<String> POLL_TRIGGERS = Set.of("interval", "viewport", "both");
    private static final Set<String> PLACEMENTS = Set.of("behind-buildings", "above-buildings", "below-paths", "top");
    private static final Set<String> ASSET_URL_FIELDS = Set.of("iconUrl", "modelUrl", "trainModelUrl", "imageUrl");
    private static final int MAX_LAYERS = 50;

    private final ObjectMapper objectMapper;

    public ExtensionManifestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExtensionManifest parse(byte[] content, URI manifestUri) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception e) {
            throw new ExtensionException("The extension manifest is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new ExtensionException("The extension manifest must be a JSON object");
        }

        String id = requiredId(root, "id");
        String name = requiredText(root, "name", 256);
        String version = requiredText(root, "version", 64);
        String description = optionalText(root, "description", 1000, "");
        JsonNode layersNode = root.get("layers");
        if (layersNode == null || !layersNode.isArray() || layersNode.size() > MAX_LAYERS) {
            throw new ExtensionException("The manifest layers must be an array with at most " + MAX_LAYERS + " entries");
        }

        String iconAssetId = null;
        String iconUrl = null;
        if (root.hasNonNull("icon")) {
            iconUrl = resolveUrl(manifestUri, requiredNodeText(root.get("icon"), "icon", 2048)).toString();
            iconAssetId = assetId(iconUrl);
        }

        Set<String> layerIds = new HashSet<>();
        List<ExtensionLayer> layers = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (JsonNode layerNode : layersNode) {
            if (!layerNode.isObject()) {
                throw new ExtensionException("Every manifest layer must be an object");
            }
            String layerId = requiredId(layerNode, "id");
            if (!layerIds.add(layerId.toLowerCase(Locale.ROOT))) {
                throw new ExtensionException("Layer IDs must be unique");
            }
            String layerName = layerNode.hasNonNull("name")
                    ? requiredText(layerNode, "name", 256) : layerId;
            String layerDescription = layerNode.hasNonNull("description")
                    ? requiredText(layerNode, "description", 1000) : null;
            String type = requiredEnum(layerNode, "type", Set.of("static", "polling", "streaming"));
            String protocol = requiredEnum(layerNode, "protocol", Set.of("http", "websocket", "sse"));
            if (type.equals("streaming") || !protocol.equals("http")) {
                unsupported.add(layerId);
                continue;
            }

            String renderer = requiredEnum(layerNode, "renderer", RENDERERS);
            String format = layerNode.hasNonNull("format")
                    ? requiredEnum(layerNode, "format", Set.of("geojson", "gpx", "json", "image")) : "json";
            boolean supportedFormat = renderer.equals("BitmapLayer")
                    ? format.equals("image") : renderer.equals("ScenegraphLayer")
                    ? format.equals("json") : GEOMETRIC_FORMATS.contains(format);
            if (!supportedFormat) {
                throw new ExtensionException("The layer renderer and format combination is not supported");
            }
            URI endpoint = resolveUrl(manifestUri, requiredText(layerNode, "endpoint", 2048));
            Long pollIntervalMs = null;
            if (type.equals("polling")) {
                pollIntervalMs = layerNode.hasNonNull("pollIntervalMs")
                        ? boundedLong(layerNode.get("pollIntervalMs"), "pollIntervalMs", 1000, 86_400_000) : 30_000L;
            } else if (layerNode.hasNonNull("pollIntervalMs")) {
                throw new ExtensionException("pollIntervalMs is only valid for polling layers");
            }
            String pollTrigger = layerNode.hasNonNull("pollTrigger")
                    ? requiredEnum(layerNode, "pollTrigger", POLL_TRIGGERS) : "interval";
            String dataPath = layerNode.hasNonNull("dataPath")
                    ? requiredText(layerNode, "dataPath", 256) : null;
            Integer minZoom = layerNode.hasNonNull("minZoom")
                    ? (int) boundedLong(layerNode.get("minZoom"), "minZoom", 0, 30) : null;
            Long staleAfterMs = layerNode.hasNonNull("staleAfterMs")
                    ? boundedLong(layerNode.get("staleAfterMs"), "staleAfterMs", 1000, 86_400_000) : null;
            String placement = layerNode.hasNonNull("placement")
                    ? requiredEnum(layerNode, "placement", PLACEMENTS) : "top";
            Integer zIndex = layerNode.hasNonNull("zIndex")
                    ? (int) boundedLong(layerNode.get("zIndex"), "zIndex", -10_000, 10_000) : 0;

            JsonNode assetsNode = optionalObject(layerNode, "assets");
            if (renderer.equals("ScenegraphLayer") && !assetsNode.hasNonNull("modelUrl")) {
                throw new ExtensionException("ScenegraphLayer requires assets.modelUrl");
            }
            JsonNode mappingNode = optionalObject(layerNode, "mapping");
            Map<String, String> assetUrls = new HashMap<>();
            Map<String, Object> assets = sanitizedMap(assetsNode, "assets");
            for (String field : ASSET_URL_FIELDS) {
                if (assetsNode.hasNonNull(field)) {
                    String url = resolveUrl(manifestUri, requiredNodeText(assetsNode.get(field), field, 2048)).toString();
                    assetUrls.put(assetId(url), url);
                    assets.put(field, url);
                }
            }
            resolveModelAssets(assetsNode, assets, manifestUri, assetUrls);
            Map<String, Object> mapping = sanitizedMap(mappingNode, "mapping");
            layers.add(new ExtensionLayer(layerId, layerName, layerDescription, type, protocol, endpoint.toString(),
                    pollIntervalMs, pollTrigger, renderer, format, dataPath, assets, mapping, minZoom, staleAfterMs,
                    placement, zIndex, Map.copyOf(assetUrls)));
        }
        return new ExtensionManifest(id, name, version, description, iconAssetId, iconUrl,
                List.copyOf(layers), List.copyOf(unsupported));
    }

    public ExtensionManifest parseStored(String manifestJson) {
        try {
            return objectMapper.readValue(manifestJson, ExtensionManifest.class);
        } catch (Exception e) {
            throw new ExtensionException("The stored extension manifest cannot be read", e);
        }
    }

    public String serialize(ExtensionManifest manifest) {
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (Exception e) {
            throw new ExtensionException("The extension manifest cannot be stored", e);
        }
    }

    public String assetId(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public URI resolveUrl(URI base, String value) {
        final URI resolved;
        try {
            resolved = base.resolve(URI.create(value)).normalize();
        } catch (IllegalArgumentException e) {
            throw new ExtensionException("The manifest contains an invalid URL");
        }
        if (!Set.of("http", "https").contains(resolved.getScheme())
                || resolved.getHost() == null || resolved.getUserInfo() != null || resolved.getFragment() != null) {
            throw new ExtensionException("Extension URLs must be absolute HTTP(S) URLs without credentials or fragments");
        }
        return resolved;
    }

    private Map<String, Object> sanitizedMap(JsonNode node, String field) {
        JsonNode sanitized = sanitizeMetadata(node, field, 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.convertValue(sanitized, LinkedHashMap.class);
        return result;
    }

    private JsonNode sanitizeMetadata(JsonNode node, String path, int depth) {
        if (depth > 8) {
            throw new ExtensionException("Extension metadata is nested too deeply");
        }
        if (node.size() > 1000) {
            throw new ExtensionException("Extension metadata contains too many entries");
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String key = entry.getKey();
                if (key.length() > 128 || containsControlCharacter(key)) {
                    throw new ExtensionException("Extension metadata contains an invalid property name");
                }
                String normalizedKey = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (Set.of("endpoint", "url", "uri", "token", "secret", "password", "authorization", "cookie",
                        "credentials", "headers", "header").contains(normalizedKey)) {
                    continue;
                }
                if (isAssetUrl(path, key)) {
                    result.set(key, entry.getValue());
                } else {
                    result.set(key, sanitizeMetadata(entry.getValue(), path + "." + key, depth + 1));
                }
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                result.add(sanitizeMetadata(child, path, depth + 1));
            }
            return result;
        }
        if (node.isTextual()) {
            String value = node.textValue();
            if (value.length() > 2048 || containsControlCharacter(value)) {
                throw new ExtensionException("Extension metadata contains an invalid string");
            }
            boolean declaredAssetUrl = ASSET_URL_FIELDS.stream().anyMatch(field -> path.equals("assets." + field))
                    || path.matches("assets\\.models\\.[^.]+\\.modelUrl");
            if ((value.startsWith("http://") || value.startsWith("https://")) && !declaredAssetUrl) {
                throw new ExtensionException("Remote URLs are only allowed in declared asset fields");
            }
        }
        return node;
    }

    private boolean isAssetUrl(String path, String key) {
        return path.equals("assets") && ASSET_URL_FIELDS.contains(key)
                || path.matches("assets\\.models\\.[^.]+") && key.equals("modelUrl");
    }

    @SuppressWarnings("unchecked")
    private void resolveModelAssets(JsonNode assetsNode, Map<String, Object> assets, URI manifestUri,
                                    Map<String, String> assetUrls) {
        JsonNode modelsNode = assetsNode.get("models");
        if (modelsNode == null) return;
        if (!modelsNode.isObject()) throw new ExtensionException("assets.models must be an object");
        Map<String, Object> models = (Map<String, Object>) assets.get("models");
        for (Map.Entry<String, JsonNode> entry : modelsNode.properties()) {
            if (!ID.matcher(entry.getKey()).matches() || !entry.getValue().isObject()) {
                throw new ExtensionException("Every assets.models entry must have a valid ID and an object value");
            }
            if (!entry.getValue().hasNonNull("modelUrl")) continue;
            String url = resolveUrl(manifestUri, requiredNodeText(entry.getValue().get("modelUrl"), "modelUrl", 2048)).toString();
            assetUrls.put(assetId(url), url);
            ((Map<String, Object>) models.get(entry.getKey())).put("modelUrl", url);
        }
    }

    private JsonNode optionalObject(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!value.isObject()) {
            throw new ExtensionException(field + " must be an object");
        }
        return value;
    }

    private String requiredId(JsonNode node, String field) {
        String value = requiredText(node, field, 64);
        if (!ID.matcher(value).matches()) {
            throw new ExtensionException(field + " contains invalid characters");
        }
        return value;
    }

    private String requiredEnum(JsonNode node, String field, Set<String> allowed) {
        String value = requiredText(node, field, 32);
        if (!allowed.contains(value)) {
            throw new ExtensionException(field + " has an unsupported value");
        }
        return value;
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new ExtensionException(field + " must be a string");
        }
        return requiredNodeText(value, field, maxLength);
    }

    private String requiredNodeText(JsonNode node, String field, int maxLength) {
        if (!node.isTextual()) {
            throw new ExtensionException(field + " must be a string");
        }
        String value = node.textValue().trim();
        if (value.isEmpty() || value.length() > maxLength || containsControlCharacter(value)) {
            throw new ExtensionException(field + " is empty or too long");
        }
        return value;
    }

    private String optionalText(JsonNode node, String field, int maxLength, String defaultValue) {
        return node.hasNonNull(field) ? requiredText(node, field, maxLength) : defaultValue;
    }

    private long boundedLong(JsonNode node, String field, long min, long max) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new ExtensionException(field + " must be an integer");
        }
        long value = node.longValue();
        if (value < min || value > max) {
            throw new ExtensionException(field + " is outside the allowed range");
        }
        return value;
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
