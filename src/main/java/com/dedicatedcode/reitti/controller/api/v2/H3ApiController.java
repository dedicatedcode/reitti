package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;


@RestController
@RequestMapping("/api/v2/h3")

public class H3ApiController {
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;

    public H3ApiController(RawLocationPointJdbcService rawLocationPointJdbcService,
                           UserSharingJdbcService userSharingJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
    }

    @GetMapping("/cells/{userId}")
    public List<H3CellCount> getH3Cells(
            @AuthenticationPrincipal User user,
            @PathVariable long userId,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {

        if (user.getId() != userId) {
            if (this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream().noneMatch(userSharing -> userSharing.getSharingUserId().equals(userId))) {
                throw new IllegalAccessException("User not allowed to fetch cells for other user with id " + userId);
            }
        }
        Instant startOfRange = start.atStartOfDay(timezone).toInstant();
        Instant endOfRange = end.plusDays(1).atStartOfDay(timezone).toInstant();
        return rawLocationPointJdbcService.findVisitedH3CellsCounts(userId, startOfRange, endOfRange);
    }

    public record H3CellCount(String hexagon, Instant time, long count) {}
}
