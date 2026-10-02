package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.MemoryJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.TimeUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Central access checks for the location data APIs which take the id of the user whose data is requested.
 */
@Component
public class DataAccessGuard {

    private final UserJdbcService userJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final MemoryJdbcService memoryJdbcService;

    public DataAccessGuard(UserJdbcService userJdbcService,
                           UserSharingJdbcService userSharingJdbcService,
                           UserSettingsJdbcService userSettingsJdbcService,
                           MemoryJdbcService memoryJdbcService) {
        this.userJdbcService = userJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.memoryJdbcService = memoryJdbcService;
    }

    /**
     * Resolves the user whose data is requested. Allowed are the caller and users sharing their data with the
     * caller. Magic links only ever reach the data of the link owner.
     */
    public User loadUserToFetchDataFrom(User user, Long userId) {
        if (user instanceof TokenUser) {
            if (!Objects.equals(user.getId(), userId)) {
                throw forbidden();
            }
            return user;
        }
        if (Objects.equals(user.getId(), userId)) {
            return user;
        }
        if (this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream()
                .noneMatch(userSharing -> userSharing.getSharingUserId().equals(userId))) {
            throw forbidden();
        }
        return userJdbcService.findById(userId).orElseThrow(DataAccessGuard::forbidden);
    }

    /**
     * Returns the time window the caller may read data from. Empty means unrestricted, which is the case for
     * regular sessions, API tokens and FULL_ACCESS magic links. Magic links with one of the given levels are
     * restricted to "today" (live links) or the time range of their memory, all other magic links are rejected.
     */
    public Optional<TimeWindow> readableWindow(User user, ZoneId timezone, MagicLinkAccessLevel... allowedLinkLevels) {
        if (!(user instanceof TokenUser tokenUser)) {
            return Optional.empty();
        }
        MagicLinkAccessLevel level = accessLevel(tokenUser).orElseThrow(DataAccessGuard::forbidden);
        if (!Arrays.asList(allowedLinkLevels).contains(level)) {
            throw forbidden();
        }
        return switch (level) {
            case FULL_ACCESS -> Optional.empty();
            case ONLY_LIVE, ONLY_LIVE_WITH_PHOTOS, ONLY_LAST_LOCATION -> Optional.of(today(tokenUser, timezone));
            case MEMORY_VIEW_ONLY, MEMORY_EDIT_ACCESS -> Optional.of(memoryRange(tokenUser));
        };
    }

    public static boolean hasAccessLevel(User user, MagicLinkAccessLevel level) {
        return user instanceof TokenUser && user.getAuthorities().contains(level.asAuthority());
    }

    private static Optional<MagicLinkAccessLevel> accessLevel(TokenUser user) {
        return Arrays.stream(MagicLinkAccessLevel.values())
                .filter(level -> user.getAuthorities().contains(level.asAuthority()))
                .findFirst();
    }

    /**
     * "Today" as the timeline computes it for live links: the current day in the requested timezone, honouring the
     * owner's configured day start time.
     */
    private TimeWindow today(TokenUser user, ZoneId timezone) {
        LocalTime dayStartTime = userSettingsJdbcService.findByUserId(user.getId())
                .map(UserSettings::getDayStartTime)
                .orElse(LocalTime.MIDNIGHT);
        LocalDate today = TimeUtil.effectiveToday(timezone, dayStartTime);
        return new TimeWindow(TimeUtil.startOfDay(today, timezone, dayStartTime),
                              TimeUtil.startOfDay(today.plusDays(1), timezone, dayStartTime));
    }

    private TimeWindow memoryRange(TokenUser user) {
        List<Memory> memories = memoryJdbcService.findAllByUser(user).stream()
                .filter(memory -> user.grantsAccessTo(MagicLinkResourceType.MEMORY, memory.getId()))
                .toList();
        if (memories.size() != 1) {
            throw forbidden();
        }
        Memory memory = memories.getFirst();
        Instant end = memory.getEndDate() != null ? memory.getEndDate() : Instant.now();
        // the APIs treat the end parameter as inclusive and add one second to it
        return new TimeWindow(memory.getStartDate(), end.plusSeconds(1));
    }

    static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    /**
     * A half-open time range [start, end).
     */
    public record TimeWindow(Instant start, Instant end) {

        /**
         * Intersects the requested range with this window. A request entirely outside the window is rejected.
         */
        public TimeWindow restrict(Instant requestedStart, Instant requestedEnd) {
            Instant restrictedStart = requestedStart.isBefore(start) ? start : requestedStart;
            Instant restrictedEnd = requestedEnd.isAfter(end) ? end : requestedEnd;
            if (!restrictedStart.isBefore(restrictedEnd)) {
                throw forbidden();
            }
            return new TimeWindow(restrictedStart, restrictedEnd);
        }
    }
}
