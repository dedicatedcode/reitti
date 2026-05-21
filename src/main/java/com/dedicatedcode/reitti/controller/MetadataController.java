package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.metadata.Mood;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.MetadataOverrideService;
import com.dedicatedcode.reitti.service.TimeUtil;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/metadata")
public class MetadataController {
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final MetadataOverrideService service;

    public MetadataController(TripJdbcService tripJdbcService,
                              ProcessedVisitJdbcService processedVisitJdbcService,
                              MetadataOverrideService service) {
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.service = service;
    }

    @GetMapping("/{type}/{id}")
    public String getMetadata(@AuthenticationPrincipal User user,
                              @PathVariable String type,
                              @PathVariable Long id,
                              Model model,
                              @RequestParam(defaultValue = "UTC") ZoneId timezone,
                              @RequestParam(required = false) String returnUrl) {

        Optional<ProcessedVisit> visit = type.equals("visit") ? this.processedVisitJdbcService.findById(id) : Optional.empty();
        Optional<Trip> trip = type.equals("trip") ? this.tripJdbcService.findById(id) : Optional.empty();
        Map<String, Object> properties = switch (type) {
            case ("trip") -> trip.map(Trip::getMetadata).orElse(Collections.emptyMap());
            case ("visit") -> visit.map(ProcessedVisit::getMetadata).orElse(Collections.emptyMap());
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };

        MemoryMetadata metadata = new MemoryMetadata(null, null);
        metadata.setProperties(properties);
        model.addAttribute("metadata", metadata);
        model.addAttribute("availableMoods", Mood.values());
        model.addAttribute("returnUrl", returnUrl);
        visit.ifPresent(processedVisit -> {
            model.addAttribute("name", processedVisit.getPlace().getName());
            model.addAttribute("start", TimeUtil.adjustInstant(processedVisit.getStartTime(), timezone));
            model.addAttribute("end", TimeUtil.adjustInstant(processedVisit.getEndTime(), timezone));
        });

        return "fragments/index/metadata :: metadata";
    }

    @GetMapping(value = "/suggestions/{field}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<String> getSuggestions(@AuthenticationPrincipal User user,
                                 @PathVariable String field,
                                 @RequestParam String query) {
        return List.of("Test 1", "Test 2", "Test 3", "Test 4");
    }

    @PostMapping
    public String updateMetadata(@AuthenticationPrincipal User user,
                                 @RequestParam String type,
                                 @RequestParam Long id,
                                 @RequestParam(required = false) Mood mood,
                                 @RequestParam(required = false) String reason,
                                 @RequestParam(required = false) String notes,
                                 @RequestParam(required = false) List<String> tags,
                                 @RequestParam(required = false) String returnUrl) {
        MemoryMetadata metadata = MemoryMetadata.empty();
        metadata.setTags(tags);
        metadata.setReason(reason);
        metadata.setDescription(notes);
        metadata.setMood(mood);
        switch (type) {
            case "trip" -> {
                Optional<Trip> trip = this.tripJdbcService.findById(id);
                if (trip.isEmpty()) {
                    throw new IllegalArgumentException("Trip not found");
                } else {
                    this.service.saveTripMetadata(trip.get(), metadata);
                }
            }
            case "visit" -> {
                Optional<ProcessedVisit> visit = this.processedVisitJdbcService.findById(id);
                if (visit.isEmpty()) {
                    throw new IllegalArgumentException("Visit not found");
                } else {
                    this.service.saveVisitMetadata(visit.get(), metadata);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + type);
        }
        if (returnUrl != null) {
            return "redirect:" + returnUrl;
        } else {
            return "redirect:/";
        }
    }

}
