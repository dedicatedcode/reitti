package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.service.PanoramaxService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/panoramax")
public class PanoramaxPanelController {

    private final PanoramaxService panoramaxService;

    public PanoramaxPanelController(PanoramaxService panoramaxService) {
        this.panoramaxService = panoramaxService;
    }

    @GetMapping("/panel")
    public String panel(@RequestParam("lat") double latitude,
                        @RequestParam("lng") double longitude,
                        Model model) {
        model.addAttribute("endpoint", panoramaxService.getBaseUrl());
        PanoramaxService.NearbyPicture picture = panoramaxService.findNearby(latitude, longitude).orElse(null);
        model.addAttribute("picture", picture);
        model.addAttribute("capturedDate", formatCaptureDate(picture));
        return "fragments/panoramax :: panel";
    }

    private String formatCaptureDate(PanoramaxService.NearbyPicture picture) {
        if (picture == null || picture.datetime() == null) {
            return null;
        }
        try {
            OffsetDateTime captured = OffsetDateTime.parse(picture.datetime());
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(LocaleContextHolder.getLocale())
                    .format(captured);
        } catch (DateTimeParseException e) {
            return picture.datetime();
        }
    }
}
