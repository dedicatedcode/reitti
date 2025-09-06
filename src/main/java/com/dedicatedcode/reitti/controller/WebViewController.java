package com.dedicatedcode.reitti.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebViewController {
    private final boolean dataManagementEnabled;
    private final boolean oidcEnabled;
    private final boolean localLoginEnabled;

    public WebViewController(@Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                             @Value("${reitti.security.oidc.enabled:false}") boolean oidcEnabled,
                             @Value("${reitti.security.local-login.disable:false}") boolean localLoginDisabled) {
        this.dataManagementEnabled = dataManagementEnabled;
        this.oidcEnabled = oidcEnabled;
        this.localLoginEnabled = !localLoginDisabled;

        if (!oidcEnabled && localLoginDisabled) {
            throw new IllegalConfigurationException("No login possible.", "enable and configured OIDC support", "Enable local-login via 'reitti.security.local-login.disable:false' or 'DISABLE_LOCAL_LOGIN=false'");
        }
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
    public String login(Model model) {
        model.addAttribute("oidcEnabled", oidcEnabled);
        model.addAttribute("localLoginEnabled", localLoginEnabled);
        return "login";
    }

}
