package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.model.TimeMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.StatisticsJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.TimeUtil;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsReportService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsReportService.class);
    static final String REPORT_CACHE = "statistics-reports";
    private static final int HISTORY_DAYS = 366;
    private static final int SUGGESTION_WINDOW_DAYS = 90;
    private static final int SUGGESTION_MIN_NIGHTS = 20;
    private static final double SUGGESTION_MIN_SHARE = 0.6;
    private static final int SECOND_BASE_MIN_NIGHTS = 4;

    private final StatisticsJdbcService statisticsJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final StatisticsDataVersionService dataVersionService;
    private final ImmichIntegrationService immichIntegrationService;
    private final I18nService i18nService;
    private final CacheManager cacheManager;
    private final int leftoversMinNightsAway;
    private final double leftoversMinDistanceKm;
    private final NightCalculator nightCalculator = new NightCalculator();
    private final HomeResolver homeResolver = new HomeResolver();
    private final DayCalculator dayCalculator = new DayCalculator();

    public StatisticsReportService(StatisticsJdbcService statisticsJdbcService,
                                   UserSettingsJdbcService userSettingsJdbcService,
                                   StatisticsDataVersionService dataVersionService,
                                   ImmichIntegrationService immichIntegrationService,
                                   I18nService i18nService,
                                   CacheManager cacheManager,
                                   @Value("${reitti.statistics.leftovers.min-nights-away:3}") int leftoversMinNightsAway,
                                   @Value("${reitti.statistics.leftovers.min-distance-km:10}") double leftoversMinDistanceKm) {
        this.statisticsJdbcService = statisticsJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.dataVersionService = dataVersionService;
        this.immichIntegrationService = immichIntegrationService;
        this.i18nService = i18nService;
        this.cacheManager = cacheManager;
        this.leftoversMinNightsAway = leftoversMinNightsAway;
        this.leftoversMinDistanceKm = leftoversMinDistanceKm;
    }

    public ZoneId effectiveZone(User user, ZoneId requested) {
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        if (settings.getTimeZoneOverride() != null) {
            return settings.getTimeZoneOverride();
        }
        return requested != null ? requested : ZoneId.of("UTC");
    }

    public Optional<StatisticsPeriod> latestPeriod(User user, ZoneId zone) {
        LocalTime dayStart = dayStartTime(user);
        List<YearMonth> months = statisticsJdbcService.findAvailableMonths(user, zone, dayStart);
        return months.isEmpty() ? Optional.empty() : Optional.of(StatisticsPeriod.ofMonth(months.getLast()));
    }

    /**
     * The period to compare with: the requested one if it has the same type, otherwise the previous period.
     */
    public StatisticsPeriod resolveComparison(StatisticsPeriod period, String compareKey) {
        if (period.isAll()) {
            return null;
        }
        return StatisticsPeriod.parse(compareKey)
                .filter(c -> c.type() == period.type() && !c.equals(period))
                .orElseGet(() -> period.previous().orElse(null));
    }

    public StatisticsReport getReport(User user, StatisticsPeriod period, ZoneId zone, Locale locale, UnitSystem unitSystem, TimeMode timeMode) {
        return getReport(user, period, resolveComparison(period, null), zone, locale, unitSystem, timeMode);
    }

    public StatisticsReport getReport(User user, StatisticsPeriod period, StatisticsPeriod compare, ZoneId zone, Locale locale, UnitSystem unitSystem, TimeMode timeMode) {
        String key = String.join(":",
                                 String.valueOf(user.getId()),
                                 String.valueOf(dataVersionService.currentVersion(user.getId())),
                                 period.key(),
                                 compare != null ? compare.key() : "-",
                                 zone.getId(),
                                 locale.toLanguageTag(),
                                 String.valueOf(unitSystem),
                                 String.valueOf(timeMode),
                                 String.valueOf(TimeUtil.effectiveToday(zone, dayStartTime(user))));
        Cache cache = cacheManager.getCache(REPORT_CACHE);
        if (cache != null) {
            StatisticsReport cached = cache.get(key, StatisticsReport.class);
            if (cached != null) {
                return cached;
            }
        }
        long started = System.currentTimeMillis();
        StatisticsReport report = buildReport(user, period, compare, zone, unitSystem, timeMode, locale);
        log.debug("Built statistics report [{}] for user [{}] in {} ms", period.key(), user.getId(), System.currentTimeMillis() - started);
        if (cache != null) {
            cache.put(key, report);
        }
        return report;
    }

    StatisticsReport buildReport(User user, StatisticsPeriod period, ZoneId zone, UnitSystem unitSystem, TimeMode timeMode, Locale locale) {
        return buildReport(user, period, resolveComparison(period, null), zone, unitSystem, timeMode, locale);
    }

    StatisticsReport buildReport(User user, StatisticsPeriod period, StatisticsPeriod compare, ZoneId zone, UnitSystem unitSystem, TimeMode timeMode, Locale locale) {
        StatisticsFormatter formatter = new StatisticsFormatter(i18nService, locale, unitSystem, timeMode);
        LocalTime dayStart = dayStartTime(user);
        List<YearMonth> months = statisticsJdbcService.findAvailableMonths(user, zone, dayStart);
        StatisticsContext context = loadContext(user, period, compare, zone, dayStart, months);
        StatisticsReportBuilder builder = new StatisticsReportBuilder(context, formatter, leftoversMinNightsAway, leftoversMinDistanceKm);
        return builder.build(navigation(period, months, builder));
    }

    StatisticsContext loadContext(User user, StatisticsPeriod period, StatisticsPeriod compare, ZoneId zone, LocalTime dayStart, List<YearMonth> months) {
        LocalDate today = TimeUtil.effectiveToday(zone, dayStart);
        Optional<Instant[]> dataRange = statisticsJdbcService.findDataRange(user);
        LocalDate firstDataDay = dataRange.map(r -> TimeUtil.dayKey(r[0], zone, dayStart)).orElse(today);
        LocalDate lastDataDay = dataRange.map(r -> TimeUtil.dayKey(r[1], zone, dayStart)).orElse(today);

        LocalDate periodStart = period.startDate(firstDataDay);
        LocalDate start = periodStart.isBefore(firstDataDay) ? firstDataDay : periodStart;
        LocalDate fullEnd = period.endDateExclusive(lastDataDay.plusDays(1));
        LocalDate end = fullEnd.isAfter(today.plusDays(1)) ? today.plusDays(1) : fullEnd;
        if (end.isBefore(start)) {
            end = start;
        }

        LocalDate loadFrom = period.isAll() ? start.minusDays(HomeResolver.WINDOW_DAYS + 1) : periodStart.minusDays(HISTORY_DAYS);
        LocalDate loadTo = fullEnd.plusDays(HomeResolver.WINDOW_DAYS + 1);
        Instant loadStart = TimeUtil.startOfDay(loadFrom.minusDays(1), zone, dayStart);
        Instant loadEnd = TimeUtil.startOfDay(loadTo.plusDays(1), zone, dayStart);

        Map<Long, StatisticsData.Place> places = statisticsJdbcService.findPlaces(user);
        Map<Long, Instant> firstVisits = statisticsJdbcService.findFirstVisitPerPlace(user);
        List<StatisticsData.Visit> visits = statisticsJdbcService.findVisits(user, loadStart, loadEnd);
        List<StatisticsData.Trip> trips = statisticsJdbcService.findTrips(user, loadStart, loadEnd);

        SortedMap<LocalDate, NightCalculator.Night> nights = nightCalculator.calculate(visits, trips, places, zone, loadFrom, loadTo);
        Set<Long> markedHomes = places.values().stream()
                .filter(p -> p.type() == SignificantPlace.PlaceType.HOME)
                .map(StatisticsData.Place::id)
                .collect(Collectors.toSet());
        HomeResolver.HomeTimeline homes = homeResolver.resolve(nights, markedHomes, loadFrom, loadTo);

        List<DayCalculator.Day> days = dayCalculator.calculate(start, end, zone, dayStart, visits, trips, places, firstVisits, homes);

        List<DayCalculator.Day> compareDays = List.of();
        if (compare != null) {
            LocalDate compareStart = max(compare.startDate(start), firstDataDay);
            LocalDate compareEnd = min(compare.endDateExclusive(start), today.plusDays(1));
            boolean loaded = !compareStart.isBefore(loadFrom.plusDays(HomeResolver.WINDOW_DAYS + 1))
                    && !compareEnd.isAfter(loadTo.minusDays(HomeResolver.WINDOW_DAYS + 1));
            compareDays = loaded
                    ? dayCalculator.calculate(compareStart, compareEnd, zone, dayStart, visits, trips, places, firstVisits, homes)
                    : loadDays(user, compareStart, compareEnd, zone, dayStart, places, firstVisits, markedHomes);
        }
        List<DayCalculator.Day> yearDays = days;
        if (period.isMonth()) {
            yearDays = dayCalculator.calculate(LocalDate.of(period.year(), 1, 1), end, zone, dayStart, visits, trips, places, firstVisits, homes);
        }

        Set<Long> commutePlaces = dayCalculator.findCommutePlaces(period.isMonth() ? yearDays : days);
        dayCalculator.classify(days, nights, places, commutePlaces);
        dayCalculator.classify(yearDays, nights, places, commutePlaces);

        List<StatisticsData.Memory> memories = statisticsJdbcService.findMemories(user,
                                                                                TimeUtil.startOfDay(start, zone, dayStart),
                                                                                TimeUtil.startOfDay(end.plusDays(1), zone, dayStart));

        Set<Long> secondBases = secondBaseCandidates(nights, homes, fullEnd);

        Long suggestion = null;
        int suggestionNights = 0;
        if (dataRange.isPresent()) {
            Map.Entry<Long, Integer> candidate = homeSuggestion(user, places, zone, lastDataDay, nights);
            if (candidate != null && !markedHomes.contains(candidate.getKey())
                    && !statisticsJdbcService.findDismissedHomeSuggestions(user).contains(candidate.getKey())) {
                suggestion = candidate.getKey();
                suggestionNights = candidate.getValue();
            }
        }

        boolean photoCheck = period.isMonth() && immichIntegrationService.getIntegrationForUser(user).map(i -> i.isEnabled()).orElse(false);

        return new StatisticsContext(period, zone, dayStart, today, start, end, places, firstVisits, visits, trips, nights, homes,
                                     days, compare, compareDays, compareCandidates(period, compare, months), yearDays, memories, months,
                                     secondBases, suggestion, suggestionNights, photoCheck);
    }

    private List<DayCalculator.Day> loadDays(User user, LocalDate from, LocalDate toExclusive, ZoneId zone, LocalTime dayStart,
                                             Map<Long, StatisticsData.Place> places, Map<Long, Instant> firstVisits, Set<Long> markedHomes) {
        if (!from.isBefore(toExclusive)) {
            return List.of();
        }
        LocalDate loadFrom = from.minusDays(HomeResolver.WINDOW_DAYS + 1);
        LocalDate loadTo = toExclusive.plusDays(HomeResolver.WINDOW_DAYS + 1);
        Instant loadStart = TimeUtil.startOfDay(loadFrom.minusDays(1), zone, dayStart);
        Instant loadEnd = TimeUtil.startOfDay(loadTo.plusDays(1), zone, dayStart);
        List<StatisticsData.Visit> visits = statisticsJdbcService.findVisits(user, loadStart, loadEnd);
        List<StatisticsData.Trip> trips = statisticsJdbcService.findTrips(user, loadStart, loadEnd);
        SortedMap<LocalDate, NightCalculator.Night> nights = nightCalculator.calculate(visits, trips, places, zone, loadFrom, loadTo);
        HomeResolver.HomeTimeline homes = homeResolver.resolve(nights, markedHomes, loadFrom, loadTo);
        return dayCalculator.calculate(from, toExclusive, zone, dayStart, visits, trips, places, firstVisits, homes);
    }

    /**
     * Comparison choices: the previous period, the same month one year earlier and every other period of the same
     * type that contains data.
     */
    private List<StatisticsPeriod> compareCandidates(StatisticsPeriod period, StatisticsPeriod selected, List<YearMonth> months) {
        if (period.isAll()) {
            return List.of();
        }
        LinkedHashSet<StatisticsPeriod> candidates = new LinkedHashSet<>();
        period.previous().ifPresent(candidates::add);
        if (period.isMonth()) {
            candidates.add(StatisticsPeriod.ofMonth(period.year() - 1, period.month()));
        }
        List<StatisticsPeriod> withData = period.isMonth()
                ? months.stream().map(StatisticsPeriod::ofMonth).toList()
                : months.stream().map(YearMonth::getYear).distinct().map(StatisticsPeriod::ofYear).toList();
        List<StatisticsPeriod> others = new ArrayList<>(withData);
        Collections.reverse(others);
        candidates.addAll(others);
        if (selected != null) {
            candidates.add(selected);
        }
        candidates.remove(period);
        return List.copyOf(candidates);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private Set<Long> secondBaseCandidates(SortedMap<LocalDate, NightCalculator.Night> nights, HomeResolver.HomeTimeline homes, LocalDate until) {
        Map<Long, Integer> nightCounts = new HashMap<>();
        Map<Long, Integer> stays = new HashMap<>();
        Long previousPlace = null;
        for (NightCalculator.Night night : nights.headMap(until).tailMap(until.minusDays(HISTORY_DAYS)).values()) {
            boolean away = night.state() == NightCalculator.State.AT_PLACE && !homes.isHome(night.date(), night.placeId()) && homes.homeFor(night.date()) != null;
            if (away) {
                nightCounts.merge(night.placeId(), 1, Integer::sum);
                if (!night.placeId().equals(previousPlace)) {
                    stays.merge(night.placeId(), 1, Integer::sum);
                }
                previousPlace = night.placeId();
            } else {
                previousPlace = null;
            }
        }
        Set<Long> result = new HashSet<>();
        nightCounts.forEach((placeId, count) -> {
            if (count >= SECOND_BASE_MIN_NIGHTS && stays.getOrDefault(placeId, 0) >= 2) {
                result.add(placeId);
            }
        });
        return result;
    }

    private Map.Entry<Long, Integer> homeSuggestion(User user, Map<Long, StatisticsData.Place> places, ZoneId zone,
                                                    LocalDate lastDataDay, SortedMap<LocalDate, NightCalculator.Night> loadedNights) {
        LocalDate from = lastDataDay.minusDays(SUGGESTION_WINDOW_DAYS);
        SortedMap<LocalDate, NightCalculator.Night> nights;
        if (!loadedNights.isEmpty() && !loadedNights.firstKey().isAfter(from) && !loadedNights.lastKey().isBefore(lastDataDay)) {
            nights = loadedNights.subMap(from, lastDataDay.plusDays(1));
        } else {
            Instant start = from.minusDays(1).atStartOfDay(zone).toInstant();
            Instant end = lastDataDay.plusDays(2).atStartOfDay(zone).toInstant();
            nights = nightCalculator.calculate(statisticsJdbcService.findVisits(user, start, end),
                                               statisticsJdbcService.findTrips(user, start, end),
                                               places, zone, from, lastDataDay);
        }
        Map<Long, Integer> counts = new HashMap<>();
        int tracked = 0;
        for (NightCalculator.Night night : nights.values()) {
            if (night.state() == NightCalculator.State.AT_PLACE) {
                counts.merge(night.placeId(), 1, Integer::sum);
                tracked++;
            }
        }
        int trackedNights = tracked;
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() >= SUGGESTION_MIN_NIGHTS && e.getValue() >= trackedNights * SUGGESTION_MIN_SHARE)
                .orElse(null);
    }

    private StatisticsReport.Navigation navigation(StatisticsPeriod period, List<YearMonth> months, StatisticsReportBuilder builder) {
        List<StatisticsPeriod> candidates = switch (period.type()) {
            case MONTH -> months.stream().map(StatisticsPeriod::ofMonth).toList();
            case YEAR -> months.stream().map(YearMonth::getYear).distinct().map(StatisticsPeriod::ofYear).toList();
            case ALL -> List.of();
        };
        StatisticsReport.Link previous = null;
        StatisticsReport.Link next = null;
        if (!candidates.isEmpty()) {
            int index = candidates.indexOf(period);
            Comparator<StatisticsPeriod> order = Comparator.comparing(StatisticsPeriod::year)
                    .thenComparing(p -> p.month() != null ? p.month() : 0);
            StatisticsPeriod before = index > 0 ? candidates.get(index - 1)
                    : candidates.stream().filter(c -> order.compare(c, period) < 0).reduce((a, b) -> b).orElse(null);
            StatisticsPeriod after = index >= 0 && index < candidates.size() - 1 ? candidates.get(index + 1)
                    : (index < 0 ? candidates.stream().filter(c -> order.compare(c, period) > 0).findFirst().orElse(null) : null);
            previous = before != null ? link(before, builder, false) : null;
            next = after != null ? link(after, builder, false) : null;
        }

        YearMonth latest = months.isEmpty() ? YearMonth.now() : months.getLast();
        StatisticsPeriod monthTarget = switch (period.type()) {
            case MONTH -> period;
            case YEAR -> months.stream().filter(m -> m.getYear() == period.year()).reduce((a, b) -> b)
                    .map(StatisticsPeriod::ofMonth).orElse(StatisticsPeriod.ofMonth(period.year(), 1));
            case ALL -> StatisticsPeriod.ofMonth(latest);
        };
        StatisticsPeriod yearTarget = period.isAll() ? StatisticsPeriod.ofYear(latest.getYear()) : StatisticsPeriod.ofYear(period.year());

        Map<Integer, List<YearMonth>> perYear = months.stream().collect(Collectors.groupingBy(YearMonth::getYear, TreeMap::new, Collectors.toList()));
        List<StatisticsReport.YearOption> years = new ArrayList<>();
        perYear.forEach((year, yearMonths) -> years.add(new StatisticsReport.YearOption(
                year,
                link(StatisticsPeriod.ofYear(year), builder, period.equals(StatisticsPeriod.ofYear(year))),
                yearMonths.stream().map(m -> link(StatisticsPeriod.ofMonth(m), builder, period.equals(StatisticsPeriod.ofMonth(m)))).toList())));
        Collections.reverse(years);

        return new StatisticsReport.Navigation(
                period.type(),
                previous,
                next,
                link(monthTarget, builder, period.isMonth()),
                link(yearTarget, builder, period.isYear()),
                link(StatisticsPeriod.all(), builder, period.isAll()),
                years);
    }

    private StatisticsReport.Link link(StatisticsPeriod period, StatisticsReportBuilder builder, boolean active) {
        return new StatisticsReport.Link(period.key(), builder.title(period), period.path(), active);
    }

    private LocalTime dayStartTime(User user) {
        LocalTime dayStart = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).getDayStartTime();
        return dayStart != null ? dayStart : LocalTime.MIDNIGHT;
    }
}
