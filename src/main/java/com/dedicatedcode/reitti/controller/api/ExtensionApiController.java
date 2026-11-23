package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.service.extension.ExtensionCatalogService;
import com.dedicatedcode.reitti.service.extension.ExtensionException;
import com.dedicatedcode.reitti.service.extension.ExtensionHttpClient;
import com.dedicatedcode.reitti.service.extension.ExtensionProxyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/extensions")
public class ExtensionApiController {
    private final ExtensionCatalogService catalogService;
    private final ExtensionProxyService proxyService;

    public ExtensionApiController(ExtensionCatalogService catalogService, ExtensionProxyService proxyService) {
        this.catalogService = catalogService;
        this.proxyService = proxyService;
    }

    @GetMapping
    public ResponseEntity<ExtensionCatalogService.CatalogResponse> catalog() {
        return ResponseEntity.ok().headers(securityHeaders()).body(catalogService.catalog());
    }

    @GetMapping("/{registrationId}/layers/{layerId}/data")
    public ResponseEntity<byte[]> data(@PathVariable long registrationId,
                                       @PathVariable String layerId,
                                       @RequestParam(required = false) String bbox,
                                       @RequestParam(required = false) String zoom,
                                       @RequestParam(required = false) String width,
                                       @RequestParam(required = false) String height,
                                       @RequestParam(required = false) String date) {
        return response(proxyService.data(registrationId, layerId, bbox, zoom, width, height, date));
    }

    @GetMapping("/{registrationId}/assets/{assetId}")
    public ResponseEntity<byte[]> asset(@PathVariable long registrationId, @PathVariable String assetId) {
        return response(proxyService.asset(registrationId, assetId));
    }

    private ResponseEntity<byte[]> response(ExtensionProxyService.ProxiedResource resource) {
        return ResponseEntity.ok()
                .headers(securityHeaders())
                .contentType(MediaType.parseMediaType(resource.contentType().split(";", 2)[0]))
                .body(resource.body());
    }

    private HttpHeaders securityHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store, private");
        headers.setPragma("no-cache");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
        return headers;
    }

    @ExceptionHandler(ExtensionCatalogService.ExtensionNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound() {
        return error(HttpStatus.NOT_FOUND, "Extension resource not found");
    }

    @ExceptionHandler(ExtensionProxyService.InvalidViewportException.class)
    public ResponseEntity<Map<String, String>> invalidViewport() {
        return error(HttpStatus.BAD_REQUEST, "Invalid viewport parameters");
    }

    @ExceptionHandler(ExtensionHttpClient.UpstreamTimeoutException.class)
    public ResponseEntity<Map<String, String>> timeout() {
        return error(HttpStatus.GATEWAY_TIMEOUT, "Extension upstream timed out");
    }

    @ExceptionHandler(ExtensionHttpClient.ExtensionPayloadTooLargeException.class)
    public ResponseEntity<Map<String, String>> tooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Extension upstream response is too large");
    }

    @ExceptionHandler(ExtensionHttpClient.UnsupportedExtensionMediaTypeException.class)
    public ResponseEntity<Map<String, String>> unsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Extension upstream content type is not supported");
    }

    @ExceptionHandler({ExtensionHttpClient.UpstreamStatusException.class, ExtensionException.class})
    public ResponseEntity<Map<String, String>> upstreamFailure() {
        return error(HttpStatus.BAD_GATEWAY, "Extension upstream request failed");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).headers(securityHeaders())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("error", message));
    }
}
