package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.timeline.TimelineData;
import com.dedicatedcode.reitti.dto.timeline.UserTimelineData;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.CoverageContextService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class CoveragePageController {

    private final UserJdbcService userJdbcService;
    private final CoverageContextService coverageContextService;

    public CoveragePageController(UserJdbcService userJdbcService,
                                  CoverageContextService coverageContextService) {
        this.userJdbcService = userJdbcService;
        this.coverageContextService = coverageContextService;
    }

    @GetMapping("/coverage")
    public String coverage(Authentication authentication, Model model) {
        if (authentication == null) {
            return "coverage";
        }

        User user = userJdbcService.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return "coverage";
        }

        if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
            return "redirect:/";
        }

        model.addAttribute("currentUserId", user.getId());

        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        boolean canSeeOthers = authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN");

        List<UserTimelineData> allUsers = coverageContextService.buildUsers(user, canSeeOthers);

        TimelineData timelineData = new TimelineData(allUsers);
        model.addAttribute("timelineData", timelineData);
        model.addAttribute("showUserSelection",
                timelineData.users().size() > 1
                        || timelineData.users().stream().anyMatch(d -> d.devices().size() > 1));

        return "coverage";
    }
}
