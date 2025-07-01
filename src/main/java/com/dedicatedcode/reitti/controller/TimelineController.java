package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.service.PlaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;

@Controller
@RequestMapping("/timeline")
public class TimelineController {

    private final PlaceService placeService;

    @Autowired
    public TimelineController(TimelineService timelineService, PlaceService placeService) {
        this.timelineService = timelineService;
        this.placeService = placeService;
    }

    @GetMapping("/content")
    public String getTimelineContent(@RequestParam String date, Principal principal, Model model) {
        LocalDate selectedDate = LocalDate.parse(date);
        var timelineData = timelineService.getTimelineForDate(principal.getName(), selectedDate);
        model.addAttribute("entries", timelineData.getEntries());
        return "fragments/timeline :: timeline-content";
    }

    @GetMapping("/places/edit-form/{id}")
    public String getPlaceEditForm(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id);
        model.addAttribute("place", place);
        return "fragments/place-edit :: edit-form";
    }

    @PutMapping("/places/{id}")
    public String updatePlace(@PathVariable Long id, @RequestParam String name, Model model) {
        SignificantPlace updated = placeService.updateName(id, name);
        model.addAttribute("place", updated);
        return "fragments/place-edit :: view-mode";
    }

    @GetMapping("/places/view/{id}")
    public String getPlaceView(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id);
        model.addAttribute("place", place);
        return "fragments/place-edit :: view-mode";
    }
}
