package com.dedicatedcode.reitti.controller;

import ch.qos.logback.core.model.Model;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/workbench")
public class WorkbenchController {

    @GetMapping
    public String workbench(@AuthenticationPrincipal User user, Model model) {
        return "workbench";
    }
}
