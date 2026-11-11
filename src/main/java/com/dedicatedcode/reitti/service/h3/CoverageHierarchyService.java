package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.model.CoverageRegion;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.CoverageRegionJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class CoverageHierarchyService {

    private static final int DEFAULT_COLUMN_LIMIT = 250;
    private static final int EXPANDED_COLUMN_LIMIT = 5000;
    private static final int MAX_PATH_DEPTH = 12;
    private static final int EXPLORED_POINT_THRESHOLD = 10;

    private final CoverageRegionJdbcService regionService;
    private final CoveragePointStatsService pointStatsService;
    private final H3SpatialCoverageService coverageService;
    private final SignificantPlaceJdbcService placeService;
    private final MessageSource messageSource;

    public CoverageHierarchyService(CoverageRegionJdbcService regionService,
                                    CoveragePointStatsService pointStatsService,
                                    H3SpatialCoverageService coverageService,
                                    SignificantPlaceJdbcService placeService,
                                    MessageSource messageSource) {
        this.regionService = regionService;
        this.pointStatsService = pointStatsService;
        this.coverageService = coverageService;
        this.placeService = placeService;
        this.messageSource = messageSource;
    }

    public List<HierarchyColumn> buildPath(User user, Device device, String pathParam, Long expandedParentId) {
        Locale locale = LocaleContextHolder.getLocale();
        List<Long> path = parsePath(pathParam);
        CoveragePointStatsService.AreaPointStats stats = pointStatsService.getStats(user, device);

        List<CoverageRegion> current = regionService.findRoots();
        if (current.stream().noneMatch(CoverageRegion::isContinent)) {
            int rootLevel = current.stream()
                    .mapToInt(CoverageRegion::adminLevel)
                    .min()
                    .orElse(0);
            current = current.stream()
                    .filter(region -> region.isContinent() || region.adminLevel() == rootLevel)
                    .toList();
        }
        List<HierarchyColumn> columns = new ArrayList<>();
        Long parentId = null;
        String pathPrefix = "";

        for (int depth = 0; depth <= path.size(); depth++) {
            Long selectedId = depth < path.size() ? path.get(depth) : null;
            if (selectedId != null && current.stream().noneMatch(region -> region.osmId() == selectedId)) {
                break;
            }
            if (current.isEmpty()) {
                break;
            }

            Map<Long, RegionMetrics> metrics = current.stream()
                    .collect(Collectors.toMap(CoverageRegion::osmId, region -> metricsFor(region, stats)));
            Map<Long, String> names = regionService.findLocalizedNames(
                    current.stream()
                            .filter(region -> !region.isContinent())
                            .map(CoverageRegion::osmId)
                            .toList(),
                    locale);

            List<HierarchyItem> items = new ArrayList<>(current.size());
            for (CoverageRegion region : current) {
                RegionMetrics regionMetrics = metrics.get(region.osmId());
                boolean explored = regionMetrics.totalPoints() >= EXPLORED_POINT_THRESHOLD;
                Double percentage = regionMetrics.totalCells() > 0
                        ? Math.min(100.0, regionMetrics.visitedCells() * 100.0 / regionMetrics.totalCells())
                        : null;
                String name = region.isContinent()
                        ? messageSource.getMessage(region.nameKey(), null, region.nameKey(), locale)
                        : names.getOrDefault(region.osmId(), "Area #" + region.osmId());
                String itemPath = pathPrefix.isEmpty() ? Long.toString(region.osmId()) : pathPrefix + "," + region.osmId();
                items.add(new HierarchyItem(
                        region.osmId(),
                        name,
                        percentage == null ? "–" : String.format(locale, "%.1f%%", percentage),
                        percentage,
                        explored,
                        region.childCount() > 0,
                        selectedId != null && selectedId == region.osmId(),
                        region.isContinent(),
                        region.adminLevel(),
                        itemPath
                ));
            }

            Collator collator = Collator.getInstance(locale);
            items.sort(Comparator
                    .comparing(HierarchyItem::explored).reversed()
                    .thenComparing(item -> item.percentage() == null ? -1.0 : item.percentage(), Comparator.reverseOrder())
                    .thenComparing(HierarchyItem::name, collator));

            int limit = Objects.equals(parentId, expandedParentId) ? EXPANDED_COLUMN_LIMIT : DEFAULT_COLUMN_LIMIT;
            boolean truncated = items.size() > limit;
            List<HierarchyItem> visible = truncated ? new ArrayList<>(items.subList(0, limit)) : items;
            columns.add(new HierarchyColumn(
                    parentId,
                    labelFor(current, depth == 0, locale),
                    items.size(),
                    truncated,
                    truncated ? parentId : null,
                    visible
            ));

            if (selectedId == null) {
                break;
            }
            current = regionService.findChildren(selectedId);
            parentId = selectedId;
            pathPrefix = pathPrefix.isEmpty() ? Long.toString(selectedId) : pathPrefix + "," + selectedId;
        }

        return columns;
    }

    private RegionMetrics metricsFor(CoverageRegion region, CoveragePointStatsService.AreaPointStats stats) {
        if (!region.isContinent()) {
            return new RegionMetrics(stats.visitedCells(region.osmId()),
                    region.totalCells(),
                    stats.totalPoints(region.osmId()),
                    stats.firstVisited(region.osmId()),
                    stats.lastVisited(region.osmId()));
        }
        long visitedCells = 0;
        long totalCells = 0;
        long totalPoints = 0;
        Instant firstVisited = null;
        Instant lastVisited = null;
        // A continent's direct children are its top-level admin regions; their stats already
        // aggregate everything below them, so there is no need to walk the whole tree.
        for (CoverageRegion child : regionService.findChildren(region.osmId())) {
            visitedCells += stats.visitedCells(child.osmId());
            totalCells += child.totalCells();
            totalPoints += stats.totalPoints(child.osmId());
            firstVisited = min(firstVisited, stats.firstVisited(child.osmId()));
            lastVisited = max(lastVisited, stats.lastVisited(child.osmId()));
        }
        return new RegionMetrics(visitedCells, totalCells, totalPoints, firstVisited, lastVisited);
    }

    private static Instant min(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static Instant max(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    public AreaDetails buildDetails(User user, Device device, String pathParam) {
        List<Long> path = parsePath(pathParam);
        Long selectedId = path.isEmpty() ? null : path.getLast();
        if (selectedId == null) {
            return null;
        }
        CoverageRegion region = regionService.findById(selectedId).orElse(null);
        if (region == null) {
            return null;
        }
        Locale locale = LocaleContextHolder.getLocale();
        CoveragePointStatsService.AreaPointStats stats = pointStatsService.getStats(user, device);
        RegionMetrics metrics = metricsFor(region, stats);
        String name = region.isContinent()
                ? messageSource.getMessage(region.nameKey(), null, region.nameKey(), locale)
                : regionService.findLocalizedNames(List.of(region.osmId()), locale)
                        .getOrDefault(region.osmId(), "Area #" + region.osmId());
        double percentage = metrics.totalCells() > 0
                ? Math.min(100.0, metrics.visitedCells() * 100.0 / metrics.totalCells())
                : 0.0;
        boolean explored = metrics.totalPoints() >= EXPLORED_POINT_THRESHOLD;
        List<PlaceSummary> places = List.of();
        if (!region.isContinent()) {
            byte[] wkb = coverageService.getBoundaryWkb(region.osmId());
            if (wkb != null && wkb.length > 0) {
                places = placeService.findWithin(user, wkb).stream()
                        .map(place -> new PlaceSummary(
                                place.getName(),
                                messageSource.getMessage(place.getType().getMessageKey(), null,
                                        place.getType().name(), locale),
                                place.getAddress()))
                        .toList();
            }
        }
        return new AreaDetails(region.osmId(),
                name,
                String.format(locale, "%.1f%%", percentage),
                percentage,
                explored,
                region.childCount() > 0,
                metrics.firstVisited(),
                metrics.lastVisited(),
                metrics.totalPoints(),
                places);
    }

    private String labelFor(List<CoverageRegion> regions, boolean root, Locale locale) {
        if (root) {
            boolean hasContinents = regions.stream().anyMatch(CoverageRegion::isContinent);
            return messageSource.getMessage(
                    hasContinents ? "coverage.hierarchy.continents" : "coverage.hierarchy.areas",
                    null,
                    hasContinents ? "Continents" : "Areas",
                    locale);
        }
        List<Integer> levels = regions.stream()
                .filter(region -> !region.isContinent())
                .map(CoverageRegion::adminLevel)
                .toList();
        if (levels.isEmpty()) {
            return messageSource.getMessage("coverage.hierarchy.areas", null, "Areas", locale);
        }
        boolean countries = levels.stream().allMatch(level -> level == 2);
        boolean states = levels.stream().allMatch(level -> level >= 3 && level <= 5);
        boolean districts = levels.stream().allMatch(level -> level >= 6 && level <= 8);
        boolean cities = levels.stream().allMatch(level -> level >= 9);
        String key;
        String fallback;
        if (countries) {
            key = "coverage.hierarchy.countries";
            fallback = "Countries";
        } else if (states) {
            key = "coverage.hierarchy.states";
            fallback = "States / Regions";
        } else if (districts) {
            key = "coverage.hierarchy.districts";
            fallback = "Districts / Cities";
        } else if (cities) {
            key = "coverage.hierarchy.cities";
            fallback = "Cities / Districts";
        } else {
            key = "coverage.hierarchy.subareas";
            fallback = "Sub-areas";
        }
        return messageSource.getMessage(key, null, fallback, locale);
    }

    public static List<Long> parsePath(String pathParam) {
        if (pathParam == null || pathParam.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(pathParam.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .limit(MAX_PATH_DEPTH)
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    private record RegionMetrics(long visitedCells,
                                 long totalCells,
                                 long totalPoints,
                                 Instant firstVisited,
                                 Instant lastVisited) {
    }

    public record HierarchyColumn(Long parentId,
                                  String label,
                                  int totalCount,
                                  boolean truncated,
                                  Long expandableParentId,
                                  List<HierarchyItem> items) {
    }

    public record AreaDetails(long osmId,
                              String name,
                              String percentageLabel,
                              Double percentage,
                              boolean explored,
                              boolean hasChildren,
                              Instant firstVisited,
                              Instant lastVisited,
                              long pointCount,
                              List<PlaceSummary> places) {
    }

    public record PlaceSummary(String name, String typeLabel, String address) {
    }

    public record HierarchyItem(long osmId,
                                String name,
                                String percentageLabel,
                                Double percentage,
                                boolean explored,
                                boolean hasChildren,
                                boolean selected,
                                boolean continent,
                                int adminLevel,
                                String pathValue) {
    }
}
