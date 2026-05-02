package com.dedicatedcode.reitti.controller.settings;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/settings")
public class SettingsController {

    @GetMapping
    public String settingsPage() {
        return "redirect:/settings/job-status";
    }

}
