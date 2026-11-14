package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.timeline.DeviceTimelineData;
import com.dedicatedcode.reitti.dto.timeline.UserTimelineData;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class CoverageContextService {

    private final UserJdbcService userJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final AvatarService avatarService;

    public CoverageContextService(UserJdbcService userJdbcService,
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

    public List<UserTimelineData> buildUsers(User currentUser, boolean canSeeOthers) {
        List<UserTimelineData> allUsers = new ArrayList<>();
        allUsers.add(buildUserTimelineData(currentUser, true));
        if (canSeeOthers) {
            userSharingJdbcService.findBySharedWithUser(currentUser.getId()).stream()
                    .filter(sharing -> !sharing.isLatestLocationOnly())
                    .map(sharing -> userJdbcService.findById(sharing.getSharingUserId()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(user -> buildUserTimelineData(user, false))
                    .sorted(Comparator.comparing(UserTimelineData::displayName))
                    .forEach(allUsers::add);
        }
        return allUsers;
    }

    public UserTimelineData buildUserTimelineData(User user, boolean active) {
        String avatarUrl = avatarService.getInfo(user.getId())
                .map(info -> String.format("/api/avatars/%d?ts=%s", user.getId(), info.updatedAt()))
                .orElse(String.format("/api/avatars/%d", user.getId()));
        String initials = avatarService.generateInitials(user.getDisplayName());
        String color = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).getColor();

        List<DeviceTimelineData> devices = Collections.emptyList();
        List<Device> enabledDevices = deviceJdbcService.getAllEnabled(user).stream()
                .filter(Device::showOnMap).toList();
        if (enabledDevices.size() >= 2) {
            devices = enabledDevices.stream()
                    .map(device -> new DeviceTimelineData(
                            device.id(),
                            device.name(),
                            avatarService.getAvatarDeviceId(user.getId(), device.id())
                                    .map(data -> "/api/avatars/" + user.getId() + "/" + device.id() + "?ts=" + data.updatedAt())
                                    .orElse(null),
                            avatarService.generateInitials(device.name()),
                            device.showOnMap(),
                            device.color(),
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
                null, null, null, null, null, null,
                devices,
                active);
    }
}
