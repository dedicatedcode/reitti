package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.service.PanoramaxService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/panoramax")
public class PanoramaxApiController {

    private static final double METERS_PER_DEGREE = 111320.0;

    private final PanoramaxService panoramaxService;

    public PanoramaxApiController(PanoramaxService panoramaxService) {
        this.panoramaxService = panoramaxService;
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<PanoramaxService.NearbyPicture>> nearby(
            @RequestParam("lat") double latitude,
            @RequestParam("lng") double longitude,
            @RequestParam(value = "radius", defaultValue = "45") double radiusMeters,
            @RequestParam(value = "limit", defaultValue = "1") int limit) {
        List<PanoramaxService.NearbyPicture> pictures =
                panoramaxService.findNearbyList(latitude, longitude, radiusMeters / METERS_PER_DEGREE, limit);
        if (pictures.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pictures);
    }
}
