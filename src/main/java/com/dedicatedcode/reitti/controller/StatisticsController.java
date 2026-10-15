package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceOverrideJdbcService;
import com.dedicatedcode.reitti.repository.StatisticsJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.statistics.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.Optional;

@Controller
@RequestMapping("/statistics")
public class StatisticsController {

    private static final String COMPARE_SELECT_ID = "stats-compare-select";

    private final StatisticsReportService reportService;
    private final StatisticsCoverageService coverageService;
    private final StatisticsPhotoService photoService;
    private final StatisticsDataVersionService dataVersionService;
    private final StatisticsJdbcService statisticsJdbcService;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final SignificantPlaceOverrideJdbcService placeOverrideJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final ContextPathHolder contextPathHolder;

    public StatisticsController(StatisticsReportService reportService,
                                StatisticsCoverageService coverageService,
                                StatisticsPhotoService photoService,
                                StatisticsDataVersionService dataVersionService,
                                StatisticsJdbcService statisticsJdbcService,
                                SignificantPlaceJdbcService placeJdbcService,
                                SignificantPlaceOverrideJdbcService placeOverrideJdbcService,
                                UserSettingsJdbcService userSettingsJdbcService,
                                ContextPathHolder contextPathHolder) {
        this.reportService = reportService;
        this.coverageService = coverageService;
        this.photoService = photoService;
        this.dataVersionService = dataVersionService;
        this.statisticsJdbcService = statisticsJdbcService;
        this.placeJdbcService = placeJdbcService;
        this.placeOverrideJdbcService = placeOverrideJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.contextPathHolder = contextPathHolder;
    }

    @GetMapping
    public String latest(@AuthenticationPrincipal User user, Model model) {
        return page(user, "", null, model);
    }

    @GetMapping("/all")
    public String all(@AuthenticationPrincipal User user, Model model) {
        return page(user, StatisticsPeriod.all().key(), null, model);
    }

    @GetMapping("/{year:\\d{4}}")
    public String year(@PathVariable int year, @RequestParam(required = false) String compare,
                       @AuthenticationPrincipal User user, Model model) {
        return page(user, StatisticsPeriod.ofYear(year).key(), compare, model);
    }

    @GetMapping("/{year:\\d{4}}/{month:\\d{1,2}}")
    public String month(@PathVariable int year, @PathVariable int month, @RequestParam(required = false) String compare,
                        @AuthenticationPrincipal User user, Model model) {
        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return page(user, StatisticsPeriod.ofMonth(year, month).key(), compare, model);
    }

    private String page(User user, String periodKey, String compareKey, Model model) {
        if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
            return "redirect:/";
        }
        model.addAttribute("periodKey", periodKey);
        model.addAttribute("compareKey", StatisticsPeriod.parse(compareKey).map(StatisticsPeriod::key).orElse(""));
        return "statistics";
    }

    @GetMapping("/fragments/report")
    public String report(@AuthenticationPrincipal User user,
                         @RequestParam(required = false, defaultValue = "") String period,
                         @RequestParam(required = false) String compare,
                         @RequestParam(required = false) ZoneId timezone,
                         @RequestHeader(value = "HX-Trigger", required = false) String htmxTrigger,
                         HttpServletResponse response,
                         Model model) {
        StatisticsReport report = loadReport(user, period, compare, timezone);
        if (COMPARE_SELECT_ID.equals(htmxTrigger)) {
            response.setHeader("HX-Push-Url", pushUrl(report));
        }
        model.addAttribute("report", report);
        model.addAttribute("timezone", reportService.effectiveZone(user, timezone).getId());
        model.addAttribute("coverageEnabled", coverageService.isEnabled());
        return "fragments/statistics :: report";
    }

    @GetMapping("/fragments/coverage")
    public String coverage(@AuthenticationPrincipal User user,
                           @RequestParam String period,
                           @RequestParam(required = false) ZoneId timezone,
                           Model model) {
        StatisticsReport report = loadReport(user, period, null, timezone);
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        model.addAttribute("coverage", coverageService.getCoverage(user, report, LocaleContextHolder.getLocale(), settings.getUnitSystem()).orElse(null));
        return "fragments/statistics :: coverage";
    }

    @GetMapping("/fragments/photos")
    public String photos(@AuthenticationPrincipal User user,
                         @RequestParam String period,
                         @RequestParam(required = false) ZoneId timezone,
                         Model model) {
        StatisticsReport report = loadReport(user, period, null, timezone);
        long withoutPhotos = report.photoCheckAvailable() ? photoService.countStaysWithoutPhotos(user, report.longStays()) : 0;
        model.addAttribute("staysWithoutPhotos", withoutPhotos);
        return "fragments/statistics :: photos";
    }

    @PostMapping("/home-suggestion/{placeId}/accept")
    public String acceptHomeSuggestion(@PathVariable long placeId,
                                       @AuthenticationPrincipal User user,
                                       @RequestParam(required = false, defaultValue = "") String period,
                                       @RequestParam(required = false) ZoneId timezone,
                                       Model model) {
        if (!placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        SignificantPlace place = placeJdbcService.findById(placeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        SignificantPlace updated = placeJdbcService.update(user, place.withType(SignificantPlace.PlaceType.HOME));
        placeOverrideJdbcService.insertOverride(user, updated);
        dataVersionService.bump(user.getId());
        return report(user, period, null, timezone, null, null, model);
    }

    @PostMapping("/home-suggestion/{placeId}/dismiss")
    public String dismissHomeSuggestion(@PathVariable long placeId,
                                        @AuthenticationPrincipal User user,
                                        @RequestParam(required = false, defaultValue = "") String period,
                                        @RequestParam(required = false) ZoneId timezone,
                                        Model model) {
        if (!placeJdbcService.exists(user, placeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        statisticsJdbcService.dismissHomeSuggestion(user, placeId);
        dataVersionService.bump(user.getId());
        return report(user, period, null, timezone, null, null, model);
    }

    private String pushUrl(StatisticsReport report) {
        String path = contextPathHolder.getContextPath() + report.period().path();
        StatisticsPeriod defaultComparison = reportService.resolveComparison(report.period(), null);
        return report.comparePeriod() == null || report.comparePeriod().equals(defaultComparison)
                ? path
                : path + "?compare=" + report.comparePeriod().key();
    }

    private StatisticsReport loadReport(User user, String periodKey, String compareKey, ZoneId requestedZone) {
        if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        ZoneId zone = reportService.effectiveZone(user, requestedZone);
        Optional<StatisticsPeriod> parsed = StatisticsPeriod.parse(periodKey);
        StatisticsPeriod period = parsed.or(() -> reportService.latestPeriod(user, zone))
                .orElseGet(() -> StatisticsPeriod.ofMonth(java.time.YearMonth.now(zone)));
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        return reportService.getReport(user, period, reportService.resolveComparison(period, compareKey), zone,
                                       LocaleContextHolder.getLocale(), settings.getUnitSystem(), settings.getTimeMode());
    }
}
