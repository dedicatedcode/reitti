package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.config.ConditionalOnPropertyNotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/tiles")
@ConditionalOnPropertyNotEmpty("reitti.ui.tiles.cache.url")
public class TileProxyController {
    private static final Logger log = LoggerFactory.getLogger(TileProxyController.class);

    private final HttpClient httpClient;
    private final String tileCacheUrl;

    public TileProxyController(@Value("${reitti.ui.tiles.cache.url}") String tileCacheUrl) {
        this.tileCacheUrl = tileCacheUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @GetMapping("/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> getTile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            HttpServletRequest request) {

        String tileUrl = String.format("%s/%d/%d/%d.png", tileCacheUrl, z, x, y);

        try {
            log.trace("Fetching tile: {}/{}/{}", z, x, y);

            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(tileUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET();

            // Add referer header if present
            String referer = request.getHeader("Referer");
            if (referer != null) {
                requestBuilder.header("Referer", referer);
            }

            HttpRequest httpRequest = requestBuilder.build();
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_PNG);
                headers.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
                headers.add("Access-Control-Allow-Origin", "*");

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(response.body());
            } else {
                log.warn("Failed to fetch tile {}/{}/{}: HTTP {}", z, x, y, response.statusCode());
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.warn("Failed to fetch tile {}/{}/{}: {}", z, x, y, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
