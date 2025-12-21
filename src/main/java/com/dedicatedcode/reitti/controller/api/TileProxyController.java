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
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/tiles")
@ConditionalOnPropertyNotEmpty("reitti.ui.tiles.cache.url")
public class TileProxyController {
    private static final Logger log = LoggerFactory.getLogger(TileProxyController.class);

    private final RestTemplate restTemplate;
    private final String tileCacheUrl;

    public TileProxyController(@Value("${reitti.ui.tiles.cache.url}") String tileCacheUrl) {
        this.tileCacheUrl = tileCacheUrl;
        this.restTemplate = new RestTemplate();
    }

    @GetMapping("/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> getTile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {

        String tileUrl = String.format("%s/%d/%d/%d.png", tileCacheUrl, z, x, y);

        try {
            log.trace("Fetching tile: {}/{}/{}", z, x, y);

            ResponseEntity<byte[]> response = restTemplate.getForEntity(tileUrl, byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());

            headers.add("Access-Control-Allow-Origin", "*");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(response.getBody());

        } catch (Exception e) {
            log.warn("Failed to fetch tile {}/{}/{}: {}", z, x, y, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}