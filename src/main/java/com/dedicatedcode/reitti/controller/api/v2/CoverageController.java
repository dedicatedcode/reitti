package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.h3.H3SpatialCoverageService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/v2/coverage")
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class CoverageController {

    private final DeviceJdbcService deviceJdbcService;
    private final H3SpatialCoverageService coverageService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public CoverageController(DeviceJdbcService deviceJdbcService, H3SpatialCoverageService coverageService, UserSharingJdbcService userSharingJdbcService, RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.deviceJdbcService = deviceJdbcService;
        this.coverageService = coverageService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    /**
     * All coverage areas for the current user (aggregated across devices).
     * Returns a list of CoverageInformation, each with visitedCellIds.
     */
    @GetMapping
    public List<CoverageInformation> getUserCoverage(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Instant until) {
        Locale locale = LocaleContextHolder.getLocale();
        return coverageService.getCoverage(user, until, locale);
    }

    @GetMapping("/{osmId}")
    public ResponseEntity<CoverageInformation> getAreaCoverage(
            @AuthenticationPrincipal User user,
            @PathVariable long osmId) {
        Locale locale = LocaleContextHolder.getLocale();
        return coverageService.getCoverageInformation(user, osmId, locale)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{osmId}/cells")
    public ResponseEntity<List<HexCellDto>> getAreaCells(
            @AuthenticationPrincipal User user,
            @PathVariable long osmId) {
        Locale locale = LocaleContextHolder.getLocale();
        return coverageService.getCoverageInformation(user, osmId, locale)
                .map(ci -> ci.visitedCellIds().stream()
                        .map(id -> new HexCellDto(id, 1))
                        .toList())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/device/{deviceId}")
    public List<CoverageInformation> getDeviceCoverage(
            @AuthenticationPrincipal User user,
            @PathVariable Long deviceId,
            @RequestParam(required = false) Instant until) {
        Device device = deviceJdbcService.find(user, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
        return coverageService.getDeviceCoverage(user, device, until,
                LocaleContextHolder.getLocale());
    }

    @GetMapping("/device/{deviceId}/{osmId}")
    public ResponseEntity<CoverageInformation> getDeviceAreaCoverage(
            @AuthenticationPrincipal User user,
            @PathVariable Long deviceId,
            @PathVariable long osmId) {
        Device device = deviceJdbcService.find(user, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
        return coverageService.getCoverageInformation(user, device, osmId,
                LocaleContextHolder.getLocale())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cells/{userId}")
    public List<H3CellCount> getH3Cells(
            @AuthenticationPrincipal User user,
            @PathVariable long userId,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {

        if (user.getId() != userId) {
            if (this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream().noneMatch(userSharing -> userSharing.getSharingUserId().equals(userId))) {
                throw new IllegalAccessException("User not allowed to fetch cells for other user with id " + userId);
            }
        }
        Instant startOfRange = start.atStartOfDay(timezone).toInstant();
        Instant endOfRange = end.plusDays(1).atStartOfDay(timezone).toInstant();
        return rawLocationPointJdbcService.findVisitedH3CellsCounts(userId, startOfRange, endOfRange);
    }

    @GetMapping("/boundary/{osmId}")
    public ResponseEntity<Map<String, Object>> getBoundary(
            @PathVariable long osmId) {
        String geojson = coverageService.getBoundaryGeoJson(osmId);
        if (geojson == null) {
            return ResponseEntity.notFound().build();
        }

        H3SpatialCoverageService.BoundingBox bbox = coverageService.getOrComputeBounds(osmId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("geojson", geojson);
        if (bbox != null) {
            result.put("bbox", Map.of(
                    "minLat", bbox.minLat(),
                    "minLon", bbox.minLon(),
                    "maxLat", bbox.maxLat(),
                    "maxLon", bbox.maxLon()
            ));
        } else {
            result.put("bbox", null);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/areas")
    public List<CoverageInformation> getFilteredAreas(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double maxLon,
            @RequestParam(required = false) Instant until) {
        Locale locale = LocaleContextHolder.getLocale();
        return coverageService.getCoverageFiltered(user, until, locale, minLat, minLon, maxLat, maxLon);
    }

    public record H3CellCount(String hexagon, Instant time, long count) {}

    public record HexCellDto(@JsonProperty("hex") long hex,
                             @JsonProperty("value") int value) {}
}