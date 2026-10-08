package com.dedicatedcode.reitti.service.security;

import com.dedicatedcode.reitti.controller.error.ForbiddenException;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.*;
import com.dedicatedcode.reitti.repository.MemoryJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.TimeUtil;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Central access checks for the location data APIs which take the id of the user whose data is requested.
 */
@Service
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
                throw new ForbiddenException("TokenUsers are only allowed to fetch their own data");
            }
            return user;
        }
        if (Objects.equals(user.getId(), userId)) {
            return user;
        }
        if (this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream()
                .noneMatch(userSharing -> userSharing.getSharingUserId().equals(userId))) {
            throw new ForbiddenException("User is not shared with caller");
        }
        return userJdbcService.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }


    public TimeRange loadTimeRange(User user, String start, String end, ZoneId timezone) {
        if (!(user instanceof TokenUser)) {
            return TimeRange.of(parseInstant(start, timezone, false), parseInstant(end, timezone, true));
        } else {
            MagicLinkAccessLevel accessLevel = accessLevel((TokenUser) user).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
            TimeRange allowed =  switch (accessLevel) {
                case FULL_ACCESS -> TimeRange.unbound();
                case ONLY_LIVE, ONLY_LIVE_WITH_PHOTOS, ONLY_LAST_LOCATION -> today(user, timezone);
                case MEMORY_VIEW_ONLY, MEMORY_EDIT_ACCESS -> memoryRange((TokenUser) user);
            };
            TimeRange requested = TimeRange.of(parseInstant(start, timezone, false), parseInstant(end, timezone, true));

            if (allowed.contains(requested)) {
                return requested;
            }
        }
        throw new ForbiddenException("Requested time range is outside of allowed range");
    }

    /**
     * Returns the time window the caller may read data from. Empty means unrestricted, which is the case for
     * regular sessions, API tokens and FULL_ACCESS magic links. Magic links with one of the given levels are
     * restricted to "today" (live links) or the time range of their memory, all other magic links are rejected.
     */
    public Optional<TimeRange> readableWindow(User user, ZoneId timezone, MagicLinkAccessLevel... allowedLinkLevels) {
        if (!(user instanceof TokenUser tokenUser)) {
            return Optional.empty();
        }
        MagicLinkAccessLevel level = accessLevel(tokenUser).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!Arrays.asList(allowedLinkLevels).contains(level)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return switch (level) {
            case FULL_ACCESS -> Optional.empty();
            case ONLY_LIVE, ONLY_LIVE_WITH_PHOTOS, ONLY_LAST_LOCATION -> Optional.of(today(tokenUser, timezone));
            case MEMORY_VIEW_ONLY, MEMORY_EDIT_ACCESS -> Optional.of(memoryRange(tokenUser));
        };
    }

    public void hasAccessLevel(User user, MagicLinkAccessLevel... level) {
        if (!(user instanceof TokenUser)) {
            return; // Normal users have implicit access to all of their data
        }
        for (MagicLinkAccessLevel magicLinkAccessLevel : level) {
            if (user.getAuthorities().contains(magicLinkAccessLevel.asAuthority())) {
                return;
            }
        }
        throw new ForbiddenException("Insufficient permissions to access data");
    }

    private Optional<MagicLinkAccessLevel> accessLevel(TokenUser user) {
        return Arrays.stream(MagicLinkAccessLevel.values())
                .filter(level -> user.getAuthorities().contains(level.asAuthority()))
                .findFirst();
    }

    /**
     * "Today" as the timeline computes it for live links: the current day in the requested timezone, honouring the
     * owner's configured day start time.
     */
    private TimeRange today(User user, ZoneId timezone) {
        LocalTime dayStartTime = userSettingsJdbcService.findByUserId(user.getId())
                .map(UserSettings::getDayStartTime)
                .orElse(LocalTime.MIDNIGHT);
        LocalDate today = TimeUtil.effectiveToday(timezone, dayStartTime);
        return new TimeRange(TimeUtil.startOfDay(today, timezone, dayStartTime),
                              TimeUtil.startOfDay(today.plusDays(1), timezone, dayStartTime));
    }

    private TimeRange memoryRange(TokenUser user) {
        List<Memory> memories = memoryJdbcService.findAllByUser(user).stream()
                .filter(memory -> user.grantsAccessTo(MagicLinkResourceType.MEMORY, memory.getId()))
                .toList();
        if (memories.size() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Memory memory = memories.getFirst();
        Instant end = memory.getEndDate() != null ? memory.getEndDate() : Instant.now();
        // the APIs treat the end parameter as inclusive and add one second to it
        return new TimeRange(memory.getStartDate(), end.plusSeconds(1));
    }


    private Instant parseInstant(String input, ZoneId timezone, boolean end) {
        try {
            return LocalDateTime.parse(input).atZone(timezone).toInstant();
        } catch (Exception ignored) {}

        try {
            return LocalDateTime.parse(input + (end ? "T23:59:59" : "T00:00:00")).atZone(timezone).toInstant();
        } catch (Exception ignored) {}
        try {
            return ZonedDateTime.parse(input).toInstant();
        } catch (Exception ignored) {}

        throw new IllegalArgumentException("Invalid date format");
    }

}
