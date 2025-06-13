package com.dedicatedcode.reitti.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebViewController {
    private final boolean dataManagementEnabled;

    public WebViewController(@Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        if (authentication != null) {
            model.addAttribute("username", authentication.getName());
        }
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);

        return "index";
    }
    
    @GetMapping("/login")
    public String login() {
        return "login";
    }

}
