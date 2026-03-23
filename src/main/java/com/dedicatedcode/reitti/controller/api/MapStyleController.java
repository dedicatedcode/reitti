package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.StringNode;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/map")
public class MapStyleController {

    private final ObjectMapper objectMapper;
    private final ContextPathHolder contextPathHolder;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final boolean tileCacheEnabled;
    
    public MapStyleController(
            ObjectMapper objectMapper,
            ContextPathHolder contextPathHolder,
            UserSettingsJdbcService userSettingsJdbcService,
            @Value("${reitti.ui.tiles.cache.url:}") String cacheUrl) {
        this.objectMapper = objectMapper;
        this.contextPathHolder = contextPathHolder;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.tileCacheEnabled = StringUtils.hasText(cacheUrl);
    }

    @GetMapping(value = "/reitti.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getStyle(@AuthenticationPrincipal User user, HttpServletRequest request) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/map/reitti.json");
        ClassPathResource coloredResource = new ClassPathResource("static/map/colored.json");

        JsonNode style;
        if (this.userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).isPreferColoredMap()) {
            style = objectMapper.readTree(coloredResource.getInputStream());
        } else {
            style = objectMapper.readTree(resource.getInputStream());
        }

        if (this.tileCacheEnabled) {
            style = rewriteUrlsForProxy((ObjectNode) style, request);
        }

        if (!this.contextPathHolder.getContextPath().equals("/")) {
            style = rewriteResourceUrls((ObjectNode) style, request);
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(style);
    }

    private JsonNode rewriteResourceUrls(ObjectNode style, HttpServletRequest request) {
        ObjectNode mutableStyle = style.deepCopy();
        // Rewrite sources
        StringNode glyphs = (StringNode) mutableStyle.get("glyphs");
        mutableStyle.set("glyphs", new StringNode(this.contextPathHolder.getContextPath() + glyphs.asString()));
        return mutableStyle;
    }

    private JsonNode rewriteUrlsForProxy(ObjectNode style, HttpServletRequest request) {
        ObjectNode mutableStyle = style.deepCopy();
        String baseUrl = getBaseUrl(request);
        
        // Rewrite sources
        JsonNode sources = mutableStyle.get("sources");
        if (sources != null) {
            ObjectNode mutableSources = (ObjectNode) sources;
            
            // Handle vector tiles (OpenFreeMap)
            if (mutableSources.has("openmaptiles")) {
                ObjectNode openMapTiles = (ObjectNode) mutableSources.get("openmaptiles");
                openMapTiles.remove("url");
                ArrayNode tiles = objectMapper.createArrayNode();
                tiles.add(baseUrl + "/api/v1/tiles/vector/{z}/{x}/{y}.pbf");
                openMapTiles.set("tiles", tiles);
            }
            
            // Handle raster sources
            rewriteRasterSource(mutableSources, "terrain-source", baseUrl + "/api/v1/tiles/terrain/{z}/{x}/{y}.webp");
            rewriteRasterSource(mutableSources, "satellite-source", baseUrl + "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg");
        }
        
        return mutableStyle;
    }

    private void rewriteRasterSource(ObjectNode sources, String sourceName, String tileUrl) {
        if (sources.has(sourceName)) {
            ObjectNode source = (ObjectNode) sources.get(sourceName);
            ArrayNode tiles = objectMapper.createArrayNode();
            tiles.add(tileUrl);
            source.set("tiles", tiles);
        }
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        url.append(contextPath);
        return url.toString();
    }
}
