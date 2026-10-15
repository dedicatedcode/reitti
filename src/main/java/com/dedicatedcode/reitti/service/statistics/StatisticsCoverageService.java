package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.model.PeriodCoverage;
import com.dedicatedcode.reitti.model.TimeMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.SpatialCoverageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class StatisticsCoverageService {

    static final String COVERAGE_CACHE = "statistics-coverage";

    private final SpatialCoverageService spatialCoverageService;
    private final StatisticsDataVersionService dataVersionService;
    private final I18nService i18nService;
    private final CacheManager cacheManager;
    private final boolean enabled;

    public StatisticsCoverageService(SpatialCoverageService spatialCoverageService,
                                     StatisticsDataVersionService dataVersionService,
                                     I18nService i18nService,
                                     CacheManager cacheManager,
                                     @Value("${reitti.h3.enabled:false}") boolean enabled) {
        this.spatialCoverageService = spatialCoverageService;
        this.dataVersionService = dataVersionService;
        this.i18nService = i18nService;
        this.cacheManager = cacheManager;
        this.enabled = enabled;
    }

    public record CoverageView(String value, String label, List<String> lines) implements Serializable {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<CoverageView> getCoverage(User user, StatisticsReport report, Locale locale, UnitSystem unitSystem) {
        if (!enabled || !report.hasData() || report.home() == null || report.home().latitude() == null) {
            return Optional.empty();
        }
        String key = String.join(":", String.valueOf(user.getId()), String.valueOf(dataVersionService.currentVersion(user.getId())),
                                 report.period().key(), String.valueOf(report.rangeStart().toEpochMilli()),
                                 String.valueOf(report.rangeEnd().toEpochMilli()), locale.toLanguageTag(), String.valueOf(unitSystem));
        Cache cache = cacheManager.getCache(COVERAGE_CACHE);
        if (cache != null) {
            CoverageView cached = cache.get(key, CoverageView.class);
            if (cached != null) {
                return Optional.of(cached);
            }
        }
        Optional<CoverageView> view = spatialCoverageService
                .getPeriodCoverage(user, report.home().latitude(), report.home().longitude(), report.rangeStart(), report.rangeEnd(),
                                   report.period().isMonth(), locale)
                .map(coverage -> toView(coverage, new StatisticsFormatter(i18nService, locale, unitSystem, TimeMode.TWENTY_FOUR_HOUR)));
        if (cache != null) {
            view.ifPresent(v -> cache.put(key, v));
        }
        return view;
    }

    CoverageView toView(PeriodCoverage coverage, StatisticsFormatter f) {
        List<String> lines = new ArrayList<>();
        String value;
        String label;
        if (coverage.homeAreaName() != null) {
            double gain = coverage.homeAreaAfter() - coverage.homeAreaBefore();
            value = f.signedPercent(gain);
            label = f.t("statistics.coverage.home-area.label", coverage.homeAreaName());
            lines.add(f.t("statistics.coverage.home-area", coverage.homeAreaName(), f.signedPercent(gain), f.percentWithDecimal(coverage.homeAreaAfter())));
        } else {
            value = f.number(coverage.newCells());
            label = f.t("statistics.coverage.new-cells.label");
        }
        lines.add(f.t("statistics.coverage.new-cells", coverage.newCells(), coverage.totalCells()));
        if (coverage.knownDistanceShare() != null) {
            lines.add(f.t("statistics.coverage.known", f.percent(coverage.knownDistanceShare())));
            if (coverage.newDistanceMeters() > 0) {
                lines.add(f.t("statistics.coverage.new-distance", f.distance(coverage.newDistanceMeters())));
            }
        }
        if (coverage.direction() != null) {
            lines.add(f.t("statistics.coverage.direction", f.t("statistics.direction." + coverage.direction())));
        }
        return new CoverageView(value, label, lines);
    }
}
