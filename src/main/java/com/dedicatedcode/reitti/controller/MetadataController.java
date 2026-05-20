package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/metadata")
public class MetadataController {
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;

    public MetadataController(TripJdbcService tripJdbcService, ProcessedVisitJdbcService processedVisitJdbcService) {
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
    }

    @GetMapping("/{type}/{id}")
    public String getMetadata(@AuthenticationPrincipal User user, @PathVariable String type, @PathVariable Long id, Model model) {

        Map<String, Object> properties = switch (type) {
            case ("trip") -> this.tripJdbcService.findById(id).map(Trip::getMetadata).orElse(Collections.emptyMap());
            case ("visit") -> this.processedVisitJdbcService.findById(id).map(ProcessedVisit::getMetadata).orElse(Collections.emptyMap());
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };

        MemoryMetadata metadata = new MemoryMetadata(null, null);
        metadata.setProperties(properties);
        model.addAttribute("metadata", metadata);

        return "fragments/index/metadata :: metadata";
    }

    @GetMapping(value = "/suggestions/{field}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<String> getSuggestions(@AuthenticationPrincipal User user,
                                 @PathVariable String field,
                                 @RequestParam String query) {
        return List.of("Test 1", "Test 2", "Test 3", "Test 4");
    }

}
