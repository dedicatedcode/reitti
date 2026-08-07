package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.timeline.DeviceTimelineData;
import com.dedicatedcode.reitti.dto.timeline.TimelineData;
import com.dedicatedcode.reitti.dto.timeline.UserTimelineData;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.AvatarService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

@Controller
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class CoveragePageController {

    private final UserJdbcService userJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final AvatarService avatarService;

    public CoveragePageController(UserJdbcService userJdbcService,
                                  UserSharingJdbcService userSharingJdbcService,
                                  DeviceJdbcService deviceJdbcService,
                                  UserSettingsJdbcService userSettingsJdbcService,
                                  AvatarService avatarService) {
        this.userJdbcService = userJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.avatarService = avatarService;
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

        List<UserTimelineData> allUsers = new ArrayList<>();
        allUsers.add(buildUserTimelineData(user, true));

        if (canSeeOthers) {
            userSharingJdbcService.findBySharedWithUser(user.getId()).stream()
                    .map(us -> userJdbcService.findById(us.getSharingUserId()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(u -> buildUserTimelineData(u, false))
                    .sorted(Comparator.comparing(UserTimelineData::displayName))
                    .forEach(allUsers::add);
        }

        TimelineData timelineData = new TimelineData(allUsers);
        model.addAttribute("timelineData", timelineData);
        model.addAttribute("showUserSelection",
                timelineData.users().size() > 1
                        || timelineData.users().stream().anyMatch(d -> d.devices().size() > 1));

        return "coverage";
    }

    private UserTimelineData buildUserTimelineData(User user, boolean active) {
        String avatarUrl = avatarService.getInfo(user.getId())
                .map(info -> String.format("/avatars/%d?ts=%s", user.getId(), info.updatedAt()))
                .orElse(String.format("/avatars/%d", user.getId()));
        String initials = avatarService.generateInitials(user.getDisplayName());
        String color = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).getColor();

        List<DeviceTimelineData> devices = Collections.emptyList();
        List<Device> enabledDevices = deviceJdbcService.getAllEnabled(user).stream()
                .filter(Device::showOnMap).toList();
        if (enabledDevices.size() >= 2) {
            devices = enabledDevices.stream()
                    .map(d -> new DeviceTimelineData(
                            d.id(),
                            d.name(),
                            avatarService.getAvatarDeviceId(user.getId(), d.id())
                                    .map(data -> "/avatars/" + user.getId() + "/" + d.id() + "?ts=" + data.updatedAt())
                                    .orElse(null),
                            avatarService.generateInitials(d.name()),
                            d.showOnMap(),
                            d.color(),
                            null, null, false))
                    .toList();
        }

        return new UserTimelineData(
                user.getId() + "",
                user.getDisplayName(),
                initials,
                avatarUrl,
                color,
                Collections.emptyList(),
                null, null, null, null, null,
                devices,
                active);
    }
}
