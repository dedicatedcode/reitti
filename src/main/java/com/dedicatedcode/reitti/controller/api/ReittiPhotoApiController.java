package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.S3Storage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

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
        try {
            S3Storage.S3Object result = this.s3Storage.read("images/" + filename);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.valueOf(result.getContentType()));
            responseHeaders.setContentLength(result.getContentLength());
            responseHeaders.setCacheControl("public, max-age=3600");
            // fix this code and return it in a way we do not need to keep the file in memory AI!
            return new ResponseEntity<>(result.getInputStream(), responseHeaders, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
