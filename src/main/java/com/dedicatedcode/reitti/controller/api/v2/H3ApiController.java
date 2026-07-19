package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;


@RestController
@RequestMapping("/api/v2/h3")

public class H3ApiController {
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public H3ApiController(RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    @GetMapping("/cells")
    public List<H3CellCount> getH3Cells(
            @AuthenticationPrincipal User user,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {

        Instant startOfRange = startDate.atStartOfDay(timezone).toInstant();
        Instant endOfRange = endDate.plusDays(1).atStartOfDay(timezone).toInstant();
        return rawLocationPointJdbcService.findVisitedH3CellsCounts(user, startOfRange, endOfRange);
    }

    public record H3CellCount(String hexagon, Instant lastVisited, long count) {}

}
