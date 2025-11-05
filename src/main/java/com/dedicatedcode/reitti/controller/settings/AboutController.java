package com.dedicatedcode.reitti.controller.settings;


import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.VersionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings")
public class AboutController {
    private final VersionService versionService;
    private final boolean dataManagementEnabled;
    private final ObjectMapper objectMapper;

    public AboutController(VersionService versionService,
                           @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                           ObjectMapper objectMapper) {
        this.versionService = versionService;
        this.dataManagementEnabled = dataManagementEnabled;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/about")
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("activeSection", "about");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("buildVersion", this.versionService.getVersion());
        model.addAttribute("gitCommitDetails", this.versionService.getCommitDetails());
        model.addAttribute("buildTime", this.versionService.getBuildTime());
        
        // Load acknowledgments data
        try {
            model.addAttribute("contributors", loadContributors());
            model.addAttribute("translators", loadTranslators());
            model.addAttribute("projects", loadProjects());
        } catch (IOException e) {
            // Log error and continue without acknowledgments
            model.addAttribute("contributors", List.of());
            model.addAttribute("translators", List.of());
            model.addAttribute("projects", List.of());
        }
        
        return "settings/about";
    }

    private List<Map<String, Object>> loadContributors() throws IOException {
        var resource = new ClassPathResource("contributors.json");
        var data = objectMapper.readValue(resource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        return (List<Map<String, Object>>) data.get("contributors");
    }

    private List<Map<String, Object>> loadTranslators() throws IOException {
        var resource = new ClassPathResource("translators.json");
        var data = objectMapper.readValue(resource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        return (List<Map<String, Object>>) data.get("translators");
    }

    private List<Map<String, Object>> loadProjects() throws IOException {
        var resource = new ClassPathResource("projects.json");
        var data = objectMapper.readValue(resource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        return (List<Map<String, Object>>) data.get("projects");
    }

}
