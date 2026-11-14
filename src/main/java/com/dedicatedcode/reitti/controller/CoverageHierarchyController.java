package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.timeline.UserTimelineData;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.CoverageRegionJdbcService;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.CoverageContextService;
import com.dedicatedcode.reitti.service.h3.CoverageHierarchyService;
import com.dedicatedcode.reitti.service.h3.H3SpatialCoverageService;
import com.dedicatedcode.reitti.service.security.DataAccessGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;
import java.time.ZoneId;
import java.util.List;

@Controller
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class CoverageHierarchyController {

    private final CoverageContextService coverageContextService;
    private final CoverageHierarchyService hierarchyService;
    private final DataAccessGuard dataAccessGuard;
    private final DeviceJdbcService deviceJdbcService;
    private final H3SpatialCoverageService coverageService;
    private final UserSettingsJdbcService userSettingsJdbcService;

    public record SearchResult(long osmId, String name, String context, String path) {
    }

    public CoverageHierarchyController(CoverageContextService coverageContextService,
                                       CoverageHierarchyService hierarchyService,
                                       DataAccessGuard dataAccessGuard,
                                       DeviceJdbcService deviceJdbcService,
                                       H3SpatialCoverageService coverageService,
                                       UserSettingsJdbcService userSettingsJdbcService) {
        this.coverageContextService = coverageContextService;
        this.hierarchyService = hierarchyService;
        this.dataAccessGuard = dataAccessGuard;
        this.deviceJdbcService = deviceJdbcService;
        this.coverageService = coverageService;
        this.userSettingsJdbcService = userSettingsJdbcService;
    }

    @GetMapping("/coverage/all")
    public String all(@AuthenticationPrincipal User user,
                      Authentication authentication,
                      @RequestParam(required = false) Long userId,
                      @RequestParam(required = false) Long deviceId,
                      @RequestParam(required = false) String path,
                      @RequestParam(required = false) Long expand,
                      Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
            return "redirect:/";
        }

        long targetUserId = userId == null ? user.getId() : userId;
        User targetUser = dataAccessGuard.loadUserToFetchDataFrom(user, targetUserId);
        dataAccessGuard.requireHistoryAccess(user, targetUser);
        Device selectedDevice = deviceId == null ? null : deviceJdbcService.find(targetUser, deviceId).orElse(null);

        List<String> authorities = authentication == null
                ? List.of()
                : authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        boolean canSeeOthers = authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN");
        List<UserTimelineData> users = coverageContextService.buildUsers(user, canSeeOthers);
        String selectedUserId = String.valueOf(targetUser.getId());
        UserTimelineData selectedUserData = users.stream()
                .filter(candidate -> candidate.userId().equals(selectedUserId))
                .findFirst()
                .orElse(users.getFirst());

        model.addAttribute("users", users);
        model.addAttribute("selectedUserId", selectedUserData.userId());
        model.addAttribute("selectedUserData", selectedUserData);
        model.addAttribute("selectedDeviceId", selectedDevice == null ? null : selectedDevice.id());
        model.addAttribute("selectedDeviceName", selectedDevice == null ? null : selectedDevice.name());
        boolean showUserSelection = users.size() > 1
                || users.stream().anyMatch(candidate -> candidate.devices().size() > 1);
        model.addAttribute("showUserSelection", showUserSelection);
        model.addAttribute("showDeviceSelection", selectedUserData.devices().size() >= 2);
        model.addAttribute("path", path);
        model.addAttribute("expand", expand);
        model.addAttribute("columns", hierarchyService.buildPath(targetUser, selectedDevice, path, expand));
        model.addAttribute("selectedAreaDetails", hierarchyService.buildDetails(targetUser, selectedDevice, path));
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(targetUser.getId());
        model.addAttribute("timeZone", settings.getTimeZoneOverride() != null
                ? settings.getTimeZoneOverride()
                : ZoneId.systemDefault());
        return "coverage-all";
    }

    @GetMapping("/coverage/all/search")
    public String search(@AuthenticationPrincipal User user,
                         Authentication authentication,
                         @RequestParam(name = "q", required = false) String query,
                         @RequestParam(required = false) Long userId,
                         @RequestParam(required = false) Long deviceId,
                         Model model) {
        if (user == null) {
            return "fragments/coverage-search-results :: results";
        }
        long targetUserId = userId == null ? user.getId() : userId;
        User targetUser = dataAccessGuard.loadUserToFetchDataFrom(user, targetUserId);
        List<CoverageRegionJdbcService.RegionSearchHit> hits =
                hierarchyService.search(query, 20);

        Locale locale = LocaleContextHolder.getLocale();
        List<SearchResult> results = hits.stream()
                .map(hit -> {
                    List<Long> path = hierarchyService.pathTo(hit.osmId());
                    String context = path.size() > 1
                            ? hierarchyService.regionName(path.get(path.size() - 2), locale)
                            : "";
                    return new SearchResult(hit.osmId(), hit.label(), context,
                            path.stream().map(String::valueOf).collect(Collectors.joining(",")));
                })
                .toList();

        model.addAttribute("results", results);
        model.addAttribute("selectedUserId", targetUser.getId());
        model.addAttribute("selectedDeviceId", deviceId);
        return "fragments/coverage-search-results :: results";
    }

    @GetMapping("/coverage/all/preview")
    @ResponseBody
    public ResponseEntity<String> preview(@RequestParam long osmId,
                                          @RequestParam(defaultValue = "128") int width,
                                          @RequestParam(defaultValue = "88") int height) {
        int safeWidth = Math.max(16, Math.min(width, 800));
        int safeHeight = Math.max(16, Math.min(height, 800));
        String svg = coverageService.getBoundarySvg(List.of(osmId), safeWidth, safeHeight);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePrivate())
                .body(svg);
    }
}
