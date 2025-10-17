package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.S3Storage;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/photos/reitti")
public class ReittiPhotoApiController {

    private final RestTemplate restTemplate;
    private final S3Storage s3Storage;

    public ReittiPhotoApiController(S3Storage s3Storage, RestTemplate restTemplate) {
        this.s3Storage = s3Storage;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable String filename, @AuthenticationPrincipal User user) {

        this.s3Storage.read("images/" + filename);
        HttpHeaders responseHeaders = new HttpHeaders();

                if (response.getHeaders().getContentType() != null) {
                    responseHeaders.setContentType(response.getHeaders().getContentType());
                } else {
                    responseHeaders.setContentType(MediaType.IMAGE_JPEG);
                }

                responseHeaders.setCacheControl("public, max-age=3600");

                return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);
    }
    
    private ResponseEntity<byte[]> proxyImageRequest(User user, String assetId, String size) {

        return null;
//        Optional<ImmichIntegration> integrationOpt = s3Storage.getIntegrationForUser(user);
//
//        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
//            return ResponseEntity.notFound().build();
//        }
//
//        ImmichIntegration integration = integrationOpt.get();
//
//        try {
//            String baseUrl = integration.getServerUrl().endsWith("/") ?
//                integration.getServerUrl() : integration.getServerUrl() + "/";
//            String imageUrl = baseUrl + "api/assets/" + assetId + "/thumbnail?size=" + size;
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.add("x-api-key", integration.getApiToken());
//
//            HttpEntity<String> entity = new HttpEntity<>(headers);
//
//            ResponseEntity<byte[]> response = restTemplate.exchange(
//                imageUrl,
//                HttpMethod.GET,
//                entity,
//                byte[].class
//            );
//
//            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
//                HttpHeaders responseHeaders = new HttpHeaders();
//
//                // Copy content type from Immich response if available
//                if (response.getHeaders().getContentType() != null) {
//                    responseHeaders.setContentType(response.getHeaders().getContentType());
//                } else {
//                    // Default to JPEG for images
//                    responseHeaders.setContentType(MediaType.IMAGE_JPEG);
//                }
//
//                // Set cache headers for better performance
//                responseHeaders.setCacheControl("public, max-age=3600");
//
//                return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);
//            }
//
//        } catch (Exception e) {
//            // Log error but don't expose details
//            return ResponseEntity.notFound().build();
//        }
//
//        return ResponseEntity.notFound().build();
    }
}
