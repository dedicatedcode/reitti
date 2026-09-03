package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.service.PanoramaxService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/panoramax")
public class PanoramaxApiController {

    private final PanoramaxService panoramaxService;

    public PanoramaxApiController(PanoramaxService panoramaxService) {
        this.panoramaxService = panoramaxService;
    }

    @GetMapping("/nearby")
    public ResponseEntity<PanoramaxService.NearbyPicture> nearby(@RequestParam("lat") double latitude,
                                                                 @RequestParam("lng") double longitude) {
        return panoramaxService.findNearby(latitude, longitude)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
