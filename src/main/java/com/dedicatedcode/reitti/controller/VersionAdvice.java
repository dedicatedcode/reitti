package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.service.VersionService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class VersionAdvice {

    private final String appVersion;

    public VersionAdvice(VersionService versionService) {
        this.appVersion = versionService.getVersion();
    }

    @ModelAttribute("appVersion")
    public String addVersionToModel() {
        return appVersion;
    }
}