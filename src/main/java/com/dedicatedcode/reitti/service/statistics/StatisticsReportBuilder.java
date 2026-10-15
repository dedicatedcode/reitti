package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.service.TimeUtil;
import com.dedicatedcode.reitti.service.statistics.DayCalculator.Day;
import com.dedicatedcode.reitti.service.statistics.DayCalculator.DayType;
import com.dedicatedcode.reitti.service.statistics.StatisticsReport.*;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class StatisticsReportBuilder {

    private static final List<DayType> DAY_TYPE_ORDER = List.of(DayType.COMMUTE, DayType.WANDER, DayType.TRAVEL, DayType.HOME, DayType.UNTRACKED);
    private static final long LONG_STAY_SECONDS = Duration.ofHours(2).getSeconds();
    private static final double LONG_STAY_MIN_DISTANCE_METERS = 1_000;
    private static final int RADAR_MAX_DOTS = 80;
    private static final int MAX_STRETCH_LINKS = 8;
    private static final long ROUTINE_MIN_SPAN_DAYS = 14;
    private static final long ROUTINE_MAX_MEDIAN_SECONDS = Duration.ofHours(10).getSeconds();
    private static final double RADAR_RADIUS = 90;
    private static final double RADAR_CENTER = 100;

    private final StatisticsContext ctx;
    private final StatisticsFormatter f;
    private final List<Day> tracked;
    private final Long periodHome;

    public StatisticsReportBuilder(StatisticsContext ctx, StatisticsFormatter formatter) {
        this.ctx = ctx;
        this.f = formatter;
        this.tracked = ctx.days().stream().filter(Day::isTracked).toList();
        this.periodHome = mostFrequent(ctx.days().stream().map(d -> d.homeId).filter(Objects::nonNull).toList());
    }

    public StatisticsReport build(Navigation navigation) {
        boolean hasData = !tracked.isEmpty();
        if (!hasData) {
            return new StatisticsReport(ctx.period(), ctx.comparePeriod(), rangeStart(), rangeEnd(), title(ctx.period()), false, navigation, null, null, List.of(), null,
                                        homeInfo(), null, null, null, null, List.of(), null, null, List.of(), List.of(),
                                        null, List.of(), false, List.of(), footnotes());
        }
        return new StatisticsReport(
                ctx.period(),
                ctx.comparePeriod(),
                rangeStart(),
                rangeEnd(),
                title(ctx.period()),
                true,
                navigation,
                headline(),
                trackingLine(),
                kpis(),
                f.t("statistics.kpi.eddington.note", eddingtonScopeValue(), f.distanceUnit()),
                homeInfo(),
                weekSplit(),
                movement(),
                dayTypes(),
                timeSplit(),
                distanceNotes(),
                usualTrip(),
                radar(),
                routines(),
                stays(),
                comparison(),
                leftovers(),
                ctx.photoCheckAvailable() && !longStays().isEmpty(),
                longStays(),
                footnotes());
    }

    public String title(StatisticsPeriod period) {
        return switch (period.type()) {
            case MONTH -> f.t("statistics.period.month", f.monthName(period.month()), String.valueOf(period.year()));
            case YEAR -> String.valueOf(period.year());
            case ALL -> f.t("statistics.period.all");
        };
    }

    private String trackingLine() {
        int total = ctx.days().size();
        long thin = ctx.days().stream().filter(Day::isThin).count();
        String line = f.t("statistics.tracking.days", tracked.size(), total);
        if (thin > 0) {
            line += " · " + f.t("statistics.tracking.thin", thin);
        }
        return line;
    }

    private String headline() {
        double distance = sum(tracked, d -> d.distanceMeters);
        long places = placesInPeriod().size();
        if (periodHome == null || tracked.size() < 3) {
            return f.t("statistics.headline.basic", f.distance(distance), places);
        }
        List<NightCalculator.Night> awayNights = awayNights();
        long trackedNights = periodNights().stream().filter(n -> n.state() != NightCalculator.State.UNTRACKED).count();
        if (awayNights.size() >= 7 && awayNights.size() >= trackedNights * 0.25) {
            String where = stayLabel(mostFrequent(awayNights.stream().map(NightCalculator.Night::placeId).filter(Objects::nonNull).toList()));
            return f.t("statistics.headline.away", awayNights.size(), where != null ? where : f.t("statistics.unknown.place"));
        }

        List<Day> weekdays = tracked.stream().filter(d -> !d.isWeekend() && d.type != DayType.TRAVEL).toList();
        List<Day> weekends = tracked.stream().filter(Day::isWeekend).toList();
        if (weekdays.isEmpty() || weekends.isEmpty()) {
            return f.t("statistics.headline.basic", f.distance(distance), places);
        }
        double radius = percentile(weekdays.stream().mapToDouble(d -> d.maxDistanceFromHomeMeters).sorted().toArray(), 0.9);
        TransportMode weekendMode = topMode(weekends, d -> d.modeDistance);
        String first = radius > 0
                ? f.t("statistics.headline.radius", f.distance(Math.max(radius, 1000)))
                : f.t("statistics.headline.weekdays-home");
        if (weekendMode == null) {
            return first;
        }
        String verb = f.t("statistics.mode.verb." + weekendMode.name());
        double weekendNew = weekends.stream().mapToInt(d -> d.newPlaces).sum() / (double) weekends.size();
        double weekdayNew = weekdays.stream().mapToInt(d -> d.newPlaces).sum() / (double) weekdays.size();
        String second = weekendNew > weekdayNew && weekendNew > 0
                ? f.t("statistics.headline.weekend.new", verb)
                : f.t("statistics.headline.weekend", verb);
        return first + " " + second;
    }

    private List<Kpi> kpis() {
        List<Kpi> kpis = new ArrayList<>();
        double distance = sum(tracked, d -> d.distanceMeters);
        kpis.add(new Kpi(f.distanceNumber(distance), f.distanceUnit(), f.t("statistics.kpi.distance")));

        List<Day> weekdays = tracked.stream().filter(d -> !d.isWeekend()).toList();
        long weekdayCovered = weekdays.stream().mapToLong(Day::coveredSeconds).sum();
        if (periodHome != null && weekdayCovered > 0) {
            double share = weekdays.stream().mapToLong(d -> d.homeSeconds).sum() / (double) weekdayCovered;
            kpis.add(new Kpi(f.percent(share), null, f.t("statistics.kpi.weekdays-home")));
        }

        Set<Long> places = placesInPeriod();
        long newPlaces = places.stream().filter(this::isNewInPeriod).count();
        kpis.add(new Kpi(f.number(places.size()), null, f.t("statistics.kpi.places", newPlaces)));

        int eddington = eddington(eddingtonDays());
        String eddingtonLabel = switch (ctx.period().type()) {
            case MONTH -> f.t("statistics.kpi.eddington.year");
            case YEAR -> f.t("statistics.kpi.eddington");
            case ALL -> f.t("statistics.kpi.eddington.all");
        };
        kpis.add(new Kpi(String.valueOf(eddington), null, eddingtonLabel));

        if (periodHome != null) {
            kpis.add(new Kpi(String.valueOf(awayNights().size()), null, f.t("statistics.kpi.nights-away")));
        }
        return kpis;
    }

    private int eddingtonScopeValue() {
        return eddington(eddingtonDays());
    }

    private List<Day> eddingtonDays() {
        return ctx.period().isMonth() ? ctx.yearDays() : ctx.days();
    }

    int eddington(List<Day> days) {
        double[] values = days.stream().mapToDouble(d -> f.toDistanceUnit(d.distanceMeters)).sorted().toArray();
        int e = 0;
        for (int i = values.length - 1, n = 1; i >= 0; i--, n++) {
            if (values[i] >= n) {
                e = n;
            } else {
                break;
            }
        }
        return e;
    }

    private HomeInfo homeInfo() {
        HomeResolver.HomeTimeline homes = ctx.homes();
        String name = periodHome != null ? placeName(periodHome) : null;
        HomeResolver.Source source = homes.sourceOf(periodHome);
        String description = switch (source) {
            case MARKED -> f.t("statistics.home.marked", name);
            case INFERRED -> name != null ? f.t("statistics.home.inferred", name) : f.t("statistics.home.none");
            case NONE -> f.t("statistics.home.none");
        };
        List<String> changes = new ArrayList<>();
        Long previous = null;
        for (Day day : ctx.days()) {
            if (previous != null && day.homeId != null && !previous.equals(day.homeId)) {
                changes.add(f.t("statistics.home.changed", f.date(day.date), placeName(previous), placeName(day.homeId)));
            }
            if (day.homeId != null) {
                previous = day.homeId;
            }
        }
        if (!changes.isEmpty()) {
            description = description + " " + String.join(" ", changes);
        }
        String changing = homes.changingTo() != null && !homes.changingTo().equals(ctx.homeSuggestion())
                ? f.t("statistics.home.changing", placeName(homes.changingTo()))
                : null;
        Suggestion suggestion = ctx.homeSuggestion() != null
                ? new Suggestion(ctx.homeSuggestion(), f.t("statistics.home.suggestion", placeName(ctx.homeSuggestion()), ctx.homeSuggestionNights()))
                : null;
        StatisticsData.Place home = periodHome != null ? ctx.places().get(periodHome) : null;
        return new HomeInfo(name, home != null ? home.latitude() : null, home != null ? home.longitude() : null,
                            source, description, changing, suggestion);
    }

    private WeekSplit weekSplit() {
        List<Day> weekdays = tracked.stream().filter(d -> !d.isWeekend()).toList();
        List<Day> weekends = tracked.stream().filter(Day::isWeekend).toList();
        List<Row> rows = new ArrayList<>();
        if (periodHome != null) {
            rows.add(row("statistics.week.at-home", weekdays, weekends, days -> share(days, d -> d.homeSeconds)));
        }
        rows.add(row("statistics.week.other-places", weekdays, weekends, days -> share(days, d -> d.placeSeconds)));
        rows.add(row("statistics.week.typical-day", weekdays, weekends,
                     days -> days.isEmpty() ? "–" : f.distance(median(days.stream().mapToDouble(d -> d.distanceMeters).sorted().toArray()))));
        rows.add(row("statistics.week.places", weekdays, weekends,
                     days -> days.isEmpty() ? "–" : f.number(days.stream().flatMap(d -> d.placeTime.keySet().stream()).distinct().count())));
        rows.add(row("statistics.week.mode", weekdays, weekends, days -> {
            TransportMode mode = topMode(days, d -> d.modeDistance);
            return mode != null ? f.modeName(mode) : "–";
        }));
        if (periodHome != null) {
            rows.add(row("statistics.week.leave", weekdays, weekends, days -> f.time(medianTime(days, d -> d.firstDeparture))));
            rows.add(row("statistics.week.back", weekdays, weekends, days -> f.time(medianTime(days, d -> d.lastReturn))));
            rows.add(row("statistics.week.outings", weekdays, weekends,
                         days -> days.isEmpty() ? "–" : f.number(median(days.stream().mapToDouble(d -> d.outings).sorted().toArray()))));
        }
        rows.add(row("statistics.week.stops", weekdays, weekends,
                     days -> days.isEmpty() ? "–" : f.number(median(days.stream().mapToDouble(d -> d.stops).sorted().toArray()))));
        return new WeekSplit(f.t("statistics.week.weekday"), f.t("statistics.week.weekend"), rows);
    }

    private Row row(String key, List<Day> weekdays, List<Day> weekends, Function<List<Day>, String> value) {
        return new Row(f.t(key), value.apply(weekdays), value.apply(weekends));
    }

    private String share(List<Day> days, java.util.function.ToLongFunction<Day> part) {
        long covered = days.stream().mapToLong(Day::coveredSeconds).sum();
        if (covered == 0) {
            return "–";
        }
        return f.percent(days.stream().mapToLong(part).sum() / (double) covered);
    }

    private Movement movement() {
        Map<TransportMode, Double> distance = new EnumMap<>(TransportMode.class);
        Map<TransportMode, Long> seconds = new EnumMap<>(TransportMode.class);
        Map<TransportMode, Integer> trips = new EnumMap<>(TransportMode.class);
        for (Day day : tracked) {
            day.modeDistance.forEach((mode, value) -> distance.merge(mode, value, Double::sum));
            day.modeSeconds.forEach((mode, value) -> seconds.merge(mode, value, Long::sum));
            day.modeTrips.forEach((mode, value) -> trips.merge(mode, value, Integer::sum));
        }
        List<TransportMode> modes = Arrays.stream(TransportMode.values())
                .filter(m -> distance.getOrDefault(m, 0.0) > 0 || seconds.getOrDefault(m, 0L) > 0 || trips.getOrDefault(m, 0) > 0)
                .sorted(Comparator.comparingDouble((TransportMode m) -> distance.getOrDefault(m, 0.0)).reversed())
                .toList();
        if (modes.isEmpty()) {
            return null;
        }

        double totalDistance = distance.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalSeconds = seconds.values().stream().mapToLong(Long::longValue).sum();
        double totalTrips = trips.values().stream().mapToInt(Integer::intValue).sum();

        List<Bar> bars = List.of(
                new Bar(f.t("statistics.movement.distance"), segments(modes, m -> distance.getOrDefault(m, 0.0), totalDistance, m -> f.distance(distance.getOrDefault(m, 0.0)))),
                new Bar(f.t("statistics.movement.time"), segments(modes, m -> (double) seconds.getOrDefault(m, 0L), totalSeconds, m -> f.duration(seconds.getOrDefault(m, 0L)))),
                new Bar(f.t("statistics.movement.trips"), segments(modes, m -> (double) trips.getOrDefault(m, 0), totalTrips, m -> f.t("statistics.movement.trip-count", trips.getOrDefault(m, 0)))));
        List<Legend> legend = modes.stream().map(m -> new Legend(m.name(), f.modeName(m))).toList();

        TransportMode byDistance = modes.getFirst();
        TransportMode byTrips = trips.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(byDistance);
        String caption = byDistance == byTrips
                ? f.t("statistics.movement.caption.same", f.modeName(byDistance))
                : f.t("statistics.movement.caption", f.modeName(byDistance), f.modeName(byTrips));

        String speedUnit = f.t("statistics.speed.unit." + f.distanceUnit());
        String speeds = modes.stream()
                .filter(m -> seconds.getOrDefault(m, 0L) >= 600 && distance.getOrDefault(m, 0.0) > 0 && m != TransportMode.UNKNOWN)
                .map(m -> f.modeName(m) + " " + f.number(f.toDistanceUnit(distance.get(m)) / (seconds.get(m) / 3600.0)) + " " + speedUnit)
                .collect(Collectors.joining(" · "));
        return new Movement(bars, legend, caption, speeds.isEmpty() ? null : speeds);
    }

    private List<BarSegment> segments(List<TransportMode> modes, Function<TransportMode, Double> value, double total, Function<TransportMode, String> label) {
        if (total <= 0) {
            return List.of();
        }
        return modes.stream()
                .filter(m -> value.apply(m) > 0)
                .map(m -> new BarSegment(m.name(), f.modeName(m) + " · " + label.apply(m), round1(value.apply(m) * 100 / total)))
                .toList();
    }

    private DayTypes dayTypes() {
        Map<DayType, Long> counts = ctx.days().stream().collect(Collectors.groupingBy(d -> d.type, () -> new EnumMap<>(DayType.class), Collectors.counting()));
        int total = ctx.days().size();
        List<DonutSlice> slices = new ArrayList<>();
        List<double[]> ranges = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        for (DayType type : DAY_TYPE_ORDER) {
            long count = counts.getOrDefault(type, 0L);
            if (count == 0) {
                continue;
            }
            double share = count / (double) total;
            String key = type.name().toLowerCase(Locale.ROOT);
            slices.add(new DonutSlice(key, f.t("statistics.day-type." + key), f.percent(share)));
            ranges.add(new double[]{share});
            keys.add("--day-" + key);
            summary.add(count + " " + f.t("statistics.day-type." + key).toLowerCase(Locale.ROOT));
        }
        Donut donut = new Donut(f.t("statistics.days.count", total), slices, gradient(keys, ranges));

        List<CalendarCell> calendar = new ArrayList<>();
        int leadingBlanks = 0;
        if (!ctx.period().isAll() && !ctx.days().isEmpty()) {
            leadingBlanks = ctx.days().getFirst().date.getDayOfWeek().getValue() - 1;
            for (Day day : ctx.days()) {
                String key = day.type.name().toLowerCase(Locale.ROOT);
                calendar.add(new CalendarCell(key, f.day(day.date) + " · " + f.t("statistics.day-type." + key), day.isThin()));
            }
        }
        return new DayTypes(donut, calendar, leadingBlanks, String.join(" · ", summary));
    }

    private Donut timeSplit() {
        long length = ctx.days().stream().mapToLong(Day::lengthSeconds).sum();
        if (length == 0) {
            return null;
        }
        long home = ctx.days().stream().mapToLong(d -> d.homeSeconds).sum();
        long places = ctx.days().stream().mapToLong(d -> d.placeSeconds).sum();
        long moving = ctx.days().stream().mapToLong(d -> d.movingSeconds).sum();
        long noData = ctx.days().stream().mapToLong(Day::noDataSeconds).sum();
        List<String> keys = new ArrayList<>();
        List<double[]> ranges = new ArrayList<>();
        List<DonutSlice> slices = new ArrayList<>();
        addSlice("home", home, length, keys, ranges, slices);
        addSlice("places", places, length, keys, ranges, slices);
        addSlice("moving", moving, length, keys, ranges, slices);
        addSlice("nodata", noData, length, keys, ranges, slices);
        String center = periodHome != null ? f.percent(home / (double) length) : f.percent(places / (double) length);
        return new Donut(center, slices, gradient(keys, ranges));
    }

    private void addSlice(String key, long value, long total, List<String> keys, List<double[]> ranges, List<DonutSlice> slices) {
        double share = value / (double) total;
        if (share < 0.005 || (key.equals("home") && periodHome == null)) {
            return;
        }
        keys.add("--time-" + key);
        ranges.add(new double[]{share});
        slices.add(new DonutSlice(key, f.t("statistics.time-split." + key), f.percent(share)));
    }

    private String gradient(List<String> colorVariables, List<double[]> shares) {
        StringBuilder gradient = new StringBuilder("conic-gradient(");
        double position = 0;
        for (int i = 0; i < colorVariables.size(); i++) {
            double next = i == colorVariables.size() - 1 ? 100 : position + shares.get(i)[0] * 100;
            if (i > 0) {
                gradient.append(", ");
            }
            gradient.append("var(").append(colorVariables.get(i)).append(") ")
                    .append(String.format(Locale.ROOT, "%.2f%% %.2f%%", position, next));
            position = next;
        }
        if (colorVariables.isEmpty()) {
            gradient.append("transparent 0% 100%");
        }
        return gradient.append(")").toString();
    }

    private List<String> distanceNotes() {
        List<String> notes = new ArrayList<>();
        if (periodHome == null) {
            return notes;
        }
        List<Double> distances = new ArrayList<>();
        for (StatisticsData.Visit visit : periodVisits()) {
            LocalDate date = dayOf(visit.start());
            Long home = ctx.homes().homeFor(date);
            if (home == null || home == visit.placeId()) {
                continue;
            }
            distanceBetween(home, visit.placeId()).ifPresent(distances::add);
        }
        if (!distances.isEmpty()) {
            double[] sorted = distances.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            notes.add(f.t("statistics.distance.median", f.distance(median(sorted)), f.distance(percentile(sorted, 0.9))));
        }
        double longestWalk = tracked.stream().mapToDouble(d -> d.longestWalkMeters).max().orElse(0);
        if (longestWalk > 0) {
            notes.add(f.t("statistics.distance.longest-walk", f.distance(longestWalk)));
        }
        tracked.stream().filter(d -> d.farthestPlaceId != null)
                .max(Comparator.comparingDouble(d -> d.maxDistanceFromHomeMeters))
                .ifPresent(d -> notes.add(f.t("statistics.distance.farthest", placeName(d.farthestPlaceId), f.distance(d.maxDistanceFromHomeMeters), f.day(d.date))));
        return notes;
    }

    private UsualTrip usualTrip() {
        Set<LocalDate> trackedWeekdays = tracked.stream().filter(d -> !d.isWeekend()).map(d -> d.date).collect(Collectors.toSet());
        if (trackedWeekdays.isEmpty()) {
            return null;
        }
        Map<List<Long>, Set<LocalDate>> daysPerPair = new HashMap<>();
        Map<List<Long>, List<Long>> durationsPerPair = new HashMap<>();
        Map<List<Long>, Map<Long, Integer>> originPerPair = new HashMap<>();
        for (StatisticsData.Trip trip : periodTrips()) {
            if (trip.startPlaceId() == null || trip.endPlaceId() == null || trip.startPlaceId().equals(trip.endPlaceId())) {
                continue;
            }
            LocalDate date = dayOf(trip.start());
            if (!trackedWeekdays.contains(date)) {
                continue;
            }
            List<Long> pair = List.of(Math.min(trip.startPlaceId(), trip.endPlaceId()), Math.max(trip.startPlaceId(), trip.endPlaceId()));
            daysPerPair.computeIfAbsent(pair, _ -> new HashSet<>()).add(date);
            durationsPerPair.computeIfAbsent(pair, _ -> new ArrayList<>()).add(trip.durationSeconds());
            originPerPair.computeIfAbsent(pair, _ -> new HashMap<>()).merge(trip.startPlaceId(), 1, Integer::sum);
        }
        Optional<Map.Entry<List<Long>, Set<LocalDate>>> best = daysPerPair.entrySet().stream()
                .filter(e -> e.getValue().size() >= 3)
                .max(Comparator.comparingInt(e -> e.getValue().size()));
        if (best.isEmpty()) {
            return null;
        }
        List<Long> pair = best.get().getKey();
        Long origin = pair.contains(periodHome) ? periodHome : originPerPair.get(pair).entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(pair.getFirst());
        Long destination = pair.get(0).equals(origin) ? pair.get(1) : pair.get(0);
        double[] durations = durationsPerPair.get(pair).stream().mapToDouble(s -> s / 60.0).sorted().toArray();
        long median = Math.round(median(durations));
        long spread = Math.round((percentile(durations, 0.75) - percentile(durations, 0.25)) / 2);
        int days = best.get().getValue().size();
        return new UsualTrip(
                placeName(origin) + " → " + placeName(destination),
                f.t("statistics.usual-trip.frequency", days, trackedWeekdays.size()),
                spread > 0 ? f.t("statistics.usual-trip.duration", median, spread) : f.t("statistics.usual-trip.duration.exact", median),
                Math.min(100, round1(days * 100.0 / trackedWeekdays.size())));
    }

    private Radar radar() {
        if (periodHome == null || !ctx.places().containsKey(periodHome)) {
            return null;
        }
        StatisticsData.Place home = ctx.places().get(periodHome);
        Map<Long, Long> hours = new HashMap<>();
        for (Day day : tracked) {
            day.placeTime.forEach((placeId, seconds) -> {
                if (!placeId.equals(day.homeId)) {
                    hours.merge(placeId, seconds, Long::sum);
                }
            });
        }
        List<Map.Entry<Long, Long>> top = hours.entrySet().stream()
                .filter(e -> ctx.places().containsKey(e.getKey()))
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(RADAR_MAX_DOTS)
                .toList();
        if (top.isEmpty()) {
            return null;
        }
        double maxDistance = 0;
        for (Map.Entry<Long, Long> entry : top) {
            StatisticsData.Place place = ctx.places().get(entry.getKey());
            maxDistance = Math.max(maxDistance, GeoUtils.distanceInMeters(home.latitude(), home.longitude(), place.latitude(), place.longitude()));
        }
        maxDistance = Math.max(maxDistance, 1000);
        long maxSeconds = top.getFirst().getValue();
        double scale = Math.log10(1 + maxDistance / 100.0);

        List<RadarDot> dots = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : top) {
            StatisticsData.Place place = ctx.places().get(entry.getKey());
            double distance = GeoUtils.distanceInMeters(home.latitude(), home.longitude(), place.latitude(), place.longitude());
            double bearing = Math.toRadians(bearing(home.latitude(), home.longitude(), place.latitude(), place.longitude()));
            double r = RADAR_RADIUS * Math.log10(1 + distance / 100.0) / scale;
            double size = 1.5 + 3.5 * Math.sqrt(entry.getValue() / (double) maxSeconds);
            dots.add(new RadarDot(round1(RADAR_CENTER + r * Math.sin(bearing)), round1(RADAR_CENTER - r * Math.cos(bearing)), round1(size),
                                  placeName(entry.getKey()) + " · " + f.distance(distance) + " · " + f.duration(entry.getValue())));
        }

        List<RadarRing> rings = new ArrayList<>();
        double unitMeters = f.distanceUnit().equals("mi") ? 1609.344 : 1000;
        for (double ring = 1; ring * unitMeters < maxDistance * 0.8; ring *= 10) {
            double r = RADAR_RADIUS * Math.log10(1 + ring * unitMeters / 100.0) / scale;
            rings.add(new RadarRing(round1(r), round1(RADAR_CENTER + r * Math.sin(Math.toRadians(135))), round1(RADAR_CENTER - r * Math.cos(Math.toRadians(135))), f.distance(ring * unitMeters)));
        }
        rings.add(new RadarRing(RADAR_RADIUS, round1(RADAR_CENTER + RADAR_RADIUS * Math.sin(Math.toRadians(60))), round1(RADAR_CENTER - RADAR_RADIUS * Math.cos(Math.toRadians(60))), f.distance(maxDistance)));
        return new Radar(dots, rings, f.distance(maxDistance));
    }

    private List<TextLine> routines() {
        List<TextLine> lines = new ArrayList<>();
        Map<Long, List<StatisticsData.Visit>> perPlace = new HashMap<>();
        for (StatisticsData.Visit visit : periodVisits()) {
            if (visit.start().isBefore(TimeUtil.startOfDay(ctx.start(), ctx.zone(), ctx.dayStartTime()))) {
                continue;
            }
            Long home = ctx.homes().homeFor(dayOf(visit.start()));
            if (home != null && home == visit.placeId()) {
                continue;
            }
            perPlace.computeIfAbsent(visit.placeId(), _ -> new ArrayList<>()).add(visit);
        }
        perPlace.entrySet().stream()
                .filter(e -> isRoutine(e.getValue()))
                .sorted(Comparator.comparingInt((Map.Entry<Long, List<StatisticsData.Visit>> e) -> e.getValue().size()).reversed())
                .limit(3)
                .forEach(e -> lines.add(new TextLine(placeLink(e.getKey()), routineLine(e.getValue()), List.of())));

        long awayTotal = perPlace.values().stream().flatMap(List::stream).mapToLong(v -> Duration.between(v.start(), v.end()).getSeconds()).sum();
        if (perPlace.size() > 3 && awayTotal > 0) {
            List<Map.Entry<Long, Long>> topThree = perPlace.entrySet().stream()
                    .map(e -> Map.entry(e.getKey(), e.getValue().stream().mapToLong(v -> Duration.between(v.start(), v.end()).getSeconds()).sum()))
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .limit(3)
                    .toList();
            long topThreeSeconds = topThree.stream().mapToLong(Map.Entry::getValue).sum();
            lines.add(new TextLine(f.t("statistics.routine.top-three", f.percent(topThreeSeconds / (double) awayTotal)),
                                   topThree.stream().map(e -> placeLink(e.getKey())).toList()));
        }
        return lines;
    }

    private boolean isRoutine(List<StatisticsData.Visit> visits) {
        List<LocalDate> dates = visits.stream().map(v -> dayOf(v.start())).distinct().sorted().toList();
        if (dates.size() < 4 || Duration.between(dates.getFirst().atStartOfDay(), dates.getLast().atStartOfDay()).toDays() < ROUTINE_MIN_SPAN_DAYS) {
            return false;
        }
        double[] durations = visits.stream().mapToDouble(v -> Duration.between(v.start(), v.end()).getSeconds()).sorted().toArray();
        return median(durations) < ROUTINE_MAX_MEDIAN_SECONDS;
    }

    private String routineLine(List<StatisticsData.Visit> visits) {
        List<LocalDate> dates = visits.stream().map(v -> dayOf(v.start())).distinct().sorted().toList();
        double[] intervals = new double[dates.size() - 1];
        for (int i = 1; i < dates.size(); i++) {
            intervals[i - 1] = Duration.between(dates.get(i - 1).atStartOfDay(), dates.get(i).atStartOfDay()).toDays();
        }
        Arrays.sort(intervals);
        long interval = Math.max(1, Math.round(median(intervals)));
        LocalTime arrival = medianTime(visits.stream().map(v -> v.start().atZone(ctx.zone()).toLocalTime()).toList());
        double[] durations = visits.stream().mapToDouble(v -> Duration.between(v.start(), v.end()).getSeconds()).sorted().toArray();
        double medianDuration = median(durations);
        String line = interval == 1
                ? f.t("statistics.routine.daily", f.time(arrival), f.duration((long) medianDuration))
                : f.t("statistics.routine.line", interval, f.time(arrival), f.duration((long) medianDuration));
        double longest = durations[durations.length - 1];
        if (longest > medianDuration * 2 && longest - medianDuration > 1800) {
            line += " " + f.t("statistics.routine.outlier", f.duration((long) longest));
        }
        return line;
    }

    private List<String> stays() {
        List<String> lines = new ArrayList<>();
        if (periodHome != null) {
            List<NightCalculator.Night> away = awayNights();
            if (!away.isEmpty()) {
                lines.add(f.t("statistics.stays.nights", away.size(), longestAwayStretch()));
                Map<String, Long> perLabel = new LinkedHashMap<>();
                away.stream().filter(n -> n.placeId() != null)
                        .collect(Collectors.groupingBy(NightCalculator.Night::placeId, Collectors.counting()))
                        .entrySet().stream()
                        .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                        .forEach(e -> perLabel.merge(stayLabel(e.getKey()), e.getValue(), Long::sum));
                String stayList = perLabel.entrySet().stream().limit(4)
                        .map(e -> f.t("statistics.stays.place", e.getKey(), e.getValue()))
                        .collect(Collectors.joining(" "));
                if (!stayList.isEmpty()) {
                    lines.add(stayList);
                }
                long transit = away.stream().filter(n -> n.state() == NightCalculator.State.IN_TRANSIT).count();
                if (transit > 0) {
                    lines.add(f.t("statistics.stays.transit", transit));
                }
                away.stream().map(NightCalculator.Night::placeId).filter(Objects::nonNull).distinct()
                        .filter(ctx.secondBaseCandidates()::contains)
                        .findFirst()
                        .ifPresent(id -> lines.add(f.t("statistics.stays.second-base", stayLabel(id))));
            }
        }

        Set<Long> places = placesInPeriod();
        Set<String> countries = places.stream().map(ctx.places()::get).filter(Objects::nonNull)
                .map(StatisticsData.Place::countryCode).filter(c -> c != null && !c.isBlank())
                .map(c -> c.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(TreeSet::new));
        if (!countries.isEmpty()) {
            String names = countries.stream().map(f::countryName).collect(Collectors.joining(", "));
            String line = f.t("statistics.stays.countries", countries.size(), names);
            if (ctx.period().isMonth()) {
                long yearCountries = ctx.yearDays().stream().flatMap(d -> d.placeTime.keySet().stream()).distinct()
                        .map(ctx.places()::get).filter(Objects::nonNull)
                        .map(StatisticsData.Place::countryCode).filter(c -> c != null && !c.isBlank())
                        .map(c -> c.toLowerCase(Locale.ROOT)).distinct().count();
                line += " " + f.t("statistics.stays.countries.year", yearCountries);
            }
            lines.add(line);
        }
        Set<String> visitedCities = citiesOf(places);
        Set<String> sleptCities = citiesOf(periodNights().stream().filter(n -> n.state() == NightCalculator.State.AT_PLACE)
                                                  .map(NightCalculator.Night::placeId).collect(Collectors.toSet()));
        if (!visitedCities.isEmpty()) {
            lines.add(f.t("statistics.stays.cities", sleptCities.size(), visitedCities.size()));
        }
        return lines;
    }

    private Set<String> citiesOf(Collection<Long> placeIds) {
        return placeIds.stream().map(ctx.places()::get).filter(Objects::nonNull)
                .map(StatisticsData.Place::city).filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
    }

    private int longestAwayStretch() {
        int longest = 0;
        int current = 0;
        for (NightCalculator.Night night : periodNights()) {
            if (DayCalculator.isAway(night, ctx.homes().homeFor(night.date()))) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private Comparison comparison() {
        List<String> lines = new ArrayList<>();
        List<String> highlights = new ArrayList<>();
        String headline = null;
        StatisticsPeriod compare = ctx.comparePeriod();
        if (compare != null) {
            String currentLabel = title(ctx.period());
            String compareLabel = title(compare);
            List<Day> compareTracked = ctx.compareDays().stream().filter(Day::isTracked).toList();
            if (compareTracked.isEmpty()) {
                headline = f.t("statistics.comparison.no-data", currentLabel, compareLabel);
            } else {
                headline = compareTracked.size() == tracked.size()
                        ? f.t("statistics.comparison.headline.same", currentLabel, compareLabel, tracked.size())
                        : f.t("statistics.comparison.headline", currentLabel, compareLabel, tracked.size(), compareTracked.size());
                lines.add(f.t("statistics.comparison.distance", f.distance(sum(tracked, d -> d.distanceMeters)), f.distance(sum(compareTracked, d -> d.distanceMeters))));
                long comparePlaces = compareTracked.stream().flatMap(d -> d.placeTime.keySet().stream()).distinct().count();
                lines.add(f.t("statistics.comparison.places", placesInPeriod().size(), comparePlaces));
                long covered = tracked.stream().mapToLong(Day::coveredSeconds).sum();
                long compareCovered = compareTracked.stream().mapToLong(Day::coveredSeconds).sum();
                if (periodHome != null && covered > 0 && compareCovered > 0) {
                    lines.add(f.t("statistics.comparison.home",
                                  f.percent(tracked.stream().mapToLong(d -> d.homeSeconds).sum() / (double) covered),
                                  f.percent(compareTracked.stream().mapToLong(d -> d.homeSeconds).sum() / (double) compareCovered)));
                }
            }
        }

        List<Day> scope = ctx.period().isMonth() ? ctx.yearDays() : ctx.days();
        Map<String, Double> perBucket = new LinkedHashMap<>();
        for (Day day : scope) {
            String bucket = ctx.period().isAll() ? String.valueOf(day.date.getYear()) : f.monthName(day.date.getMonthValue());
            perBucket.merge(bucket, day.distanceMeters, Double::sum);
        }
        if (perBucket.size() > 1) {
            perBucket.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> highlights.add(f.t(
                    ctx.period().isAll() ? "statistics.comparison.busiest-year" : "statistics.comparison.busiest-month",
                    e.getKey(), f.distance(e.getValue()))));
        }
        tracked.stream().max(Comparator.comparingDouble(d -> d.distanceMeters))
                .filter(d -> d.distanceMeters > 0)
                .ifPresent(d -> highlights.add(f.t("statistics.comparison.busiest-day", f.day(d.date), f.distance(d.distanceMeters))));
        tracked.stream().max(Comparator.comparingInt(d -> d.newPlaces))
                .filter(d -> d.newPlaces >= 2)
                .ifPresent(d -> highlights.add(f.t("statistics.comparison.new-places-day", d.newPlaces, f.day(d.date))));

        Map<Long, Long> seconds = new HashMap<>();
        Map<Long, Integer> visits = new HashMap<>();
        for (Day day : tracked) {
            day.placeTime.forEach((placeId, value) -> {
                if (!placeId.equals(day.homeId)) {
                    seconds.merge(placeId, value, Long::sum);
                }
            });
            day.placeVisits.forEach((placeId, value) -> visits.merge(placeId, value, Integer::sum));
        }
        List<TopPlace> topPlaces = seconds.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    return new TopPlace(placeName(e.getKey()), f.duration(e.getValue()),
                                        f.t("statistics.comparison.visits", visits.getOrDefault(e.getKey(), 1)),
                                        placeHref(e.getKey()));
                })
                .toList();
        List<CompareOption> options = compareOptions();
        if (headline == null && highlights.isEmpty() && topPlaces.isEmpty()) {
            return null;
        }
        return new Comparison(headline, lines, highlights, topPlaces, options);
    }

    private List<CompareOption> compareOptions() {
        List<CompareOption> options = new ArrayList<>();
        StatisticsPeriod previous = ctx.period().previous().orElse(null);
        StatisticsPeriod lastYear = ctx.period().isMonth() ? StatisticsPeriod.ofMonth(ctx.period().year() - 1, ctx.period().month()) : null;
        for (StatisticsPeriod candidate : ctx.compareCandidates()) {
            boolean suggested = candidate.equals(previous) || candidate.equals(lastYear);
            String label = title(candidate);
            if (candidate.equals(previous)) {
                label = f.t(ctx.period().isMonth() ? "statistics.comparison.option.previous-month" : "statistics.comparison.option.previous-year", label);
            } else if (candidate.equals(lastYear)) {
                label = f.t("statistics.comparison.option.last-year", label);
            }
            options.add(new CompareOption(candidate.key(), label, suggested, candidate.equals(ctx.comparePeriod())));
        }
        return options;
    }

    private List<TextLine> leftovers() {
        List<TextLine> lines = new ArrayList<>();
        tracked.stream()
                .filter(d -> d.visitSeconds() >= Duration.ofHours(2).getSeconds())
                .filter(d -> d.unfamiliarSeconds >= d.visitSeconds() * 0.2)
                .max(Comparator.comparingDouble(d -> d.unfamiliarSeconds / (double) d.visitSeconds()))
                .ifPresent(d -> lines.add(new TextLine(f.t("statistics.leftovers.least-familiar", f.day(d.date)),
                                                       List.of(timelineLink(d.date, d.date)))));
        int walks = tracked.stream().mapToInt(d -> d.walksFromHome).sum();
        if (walks > 0) {
            lines.add(TextLine.of(f.t("statistics.leftovers.walks-home", walks)));
        }
        int trips = tracked.stream().mapToInt(d -> d.trips).sum();
        int changed = tracked.stream().mapToInt(d -> d.modeChangeTrips).sum();
        if (trips > 0 && changed > 0) {
            lines.add(TextLine.of(f.t("statistics.leftovers.mode-change", f.percent(changed / (double) trips))));
        }
        List<NightCalculator.Night> away = awayNights();
        if (!away.isEmpty()) {
            List<LocalDate> withoutMemory = away.stream().filter(n -> {
                ZoneId zone = n.placeId() != null ? NightCalculator.zoneOf(ctx.places().get(n.placeId()), ctx.zone()) : ctx.zone();
                Instant nightStart = NightCalculator.nightStart(n.date(), zone);
                Instant nightEnd = NightCalculator.nightEnd(n.date(), zone);
                return ctx.memories().stream().noneMatch(m -> StatisticsData.overlapSeconds(m.start(), m.end(), nightStart, nightEnd) > 0);
            }).map(NightCalculator.Night::date).sorted().toList();
            if (!withoutMemory.isEmpty()) {
                lines.add(new TextLine(f.t("statistics.leftovers.no-memory", withoutMemory.size()),
                                       stretches(withoutMemory).stream().limit(MAX_STRETCH_LINKS)
                                               .map(stretch -> timelineLink(stretch[0], stretch[1].plusDays(1)))
                                               .toList()));
            }
        }
        long untrackedNights = periodNights().stream().filter(n -> n.state() == NightCalculator.State.UNTRACKED).count();
        if (untrackedNights > 0) {
            lines.add(TextLine.of(f.t("statistics.leftovers.untracked-nights", untrackedNights)));
        }
        return lines;
    }

    static List<LocalDate[]> stretches(List<LocalDate> sortedDates) {
        List<LocalDate[]> result = new ArrayList<>();
        LocalDate first = null;
        LocalDate last = null;
        for (LocalDate date : sortedDates) {
            if (last != null && date.equals(last.plusDays(1))) {
                last = date;
                continue;
            }
            if (first != null) {
                result.add(new LocalDate[]{first, last});
            }
            first = date;
            last = date;
        }
        if (first != null) {
            result.add(new LocalDate[]{first, last});
        }
        return result;
    }

    private LineLink timelineLink(LocalDate from, LocalDate to) {
        String label = from.equals(to) ? f.day(from) : f.day(from) + " – " + f.day(to);
        return new LineLink(label, "/?startDate=" + from + "&endDate=" + to);
    }

    private LineLink placeLink(Long placeId) {
        return new LineLink(placeName(placeId), placeHref(placeId));
    }

    private String placeHref(Long placeId) {
        return "/settings/places/" + placeId + "/edit?returnUrl=" + ctx.period().path();
    }

    private List<StatisticsReport.StayWindow> longStays() {
        if (!ctx.period().isMonth() || periodHome == null) {
            return List.of();
        }
        List<StatisticsReport.StayWindow> stays = new ArrayList<>();
        for (StatisticsData.Visit visit : periodVisits()) {
            if (Duration.between(visit.start(), visit.end()).getSeconds() < LONG_STAY_SECONDS) {
                continue;
            }
            Long home = ctx.homes().homeFor(dayOf(visit.start()));
            if (home == null || home == visit.placeId()) {
                continue;
            }
            if (distanceBetween(home, visit.placeId()).orElse(0.0) < LONG_STAY_MIN_DISTANCE_METERS) {
                continue;
            }
            stays.add(new StatisticsReport.StayWindow(visit.start(), visit.end()));
        }
        return stays;
    }

    private List<String> footnotes() {
        List<String> notes = new ArrayList<>();
        notes.add(f.t("statistics.footnote.times", ctx.zone().getId()));
        notes.add(switch (ctx.homes().sourceOf(periodHome)) {
            case MARKED -> f.t("statistics.footnote.home.marked");
            case INFERRED -> f.t("statistics.footnote.home.inferred", HomeResolver.WINDOW_DAYS);
            case NONE -> f.t("statistics.footnote.home.none");
        });
        notes.add(f.t("statistics.footnote.nights"));
        notes.add(f.t("statistics.footnote.untracked"));
        return notes;
    }

    private Instant rangeStart() {
        return TimeUtil.startOfDay(ctx.start(), ctx.zone(), ctx.dayStartTime());
    }

    private Instant rangeEnd() {
        return TimeUtil.startOfDay(ctx.end(), ctx.zone(), ctx.dayStartTime());
    }

    private List<NightCalculator.Night> periodNights() {
        LocalDate lastNight = ctx.end().minusDays(1);
        if (!ctx.today().isAfter(lastNight)) {
            lastNight = ctx.today().minusDays(1);
        }
        LocalDate last = lastNight;
        return ctx.nights().subMap(ctx.start(), last.plusDays(1)).values().stream().toList();
    }

    private List<NightCalculator.Night> awayNights() {
        return periodNights().stream()
                .filter(n -> DayCalculator.isAway(n, ctx.homes().homeFor(n.date())))
                .toList();
    }

    private Set<Long> placesInPeriod() {
        return tracked.stream().flatMap(d -> d.placeTime.keySet().stream()).collect(Collectors.toSet());
    }

    private boolean isNewInPeriod(Long placeId) {
        Instant first = ctx.firstVisits().get(placeId);
        if (first == null) {
            return false;
        }
        Instant start = TimeUtil.startOfDay(ctx.start(), ctx.zone(), ctx.dayStartTime());
        Instant end = TimeUtil.startOfDay(ctx.end(), ctx.zone(), ctx.dayStartTime());
        return !first.isBefore(start) && first.isBefore(end);
    }

    private List<StatisticsData.Visit> periodVisits() {
        Instant start = TimeUtil.startOfDay(ctx.start(), ctx.zone(), ctx.dayStartTime());
        Instant end = TimeUtil.startOfDay(ctx.end(), ctx.zone(), ctx.dayStartTime());
        return ctx.visits().stream().filter(v -> v.start().isBefore(end) && v.end().isAfter(start)).toList();
    }

    private List<StatisticsData.Trip> periodTrips() {
        Instant start = TimeUtil.startOfDay(ctx.start(), ctx.zone(), ctx.dayStartTime());
        Instant end = TimeUtil.startOfDay(ctx.end(), ctx.zone(), ctx.dayStartTime());
        return ctx.trips().stream().filter(t -> !t.start().isBefore(start) && t.start().isBefore(end)).toList();
    }

    private LocalDate dayOf(Instant instant) {
        return TimeUtil.dayKey(instant, ctx.zone(), ctx.dayStartTime());
    }

    private Optional<Double> distanceBetween(Long from, Long to) {
        StatisticsData.Place a = ctx.places().get(from);
        StatisticsData.Place b = ctx.places().get(to);
        if (a == null || b == null) {
            return Optional.empty();
        }
        return Optional.of(GeoUtils.distanceInMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude()));
    }

    private String placeName(Long placeId) {
        if (placeId == null) {
            return f.t("statistics.unknown.place");
        }
        StatisticsData.Place place = ctx.places().get(placeId);
        if (place == null || place.name() == null || place.name().isBlank()) {
            return f.t("place.unknown.label");
        }
        return place.name();
    }

    private String stayLabel(Long placeId) {
        if (placeId == null) {
            return null;
        }
        StatisticsData.Place place = ctx.places().get(placeId);
        StatisticsData.Place home = periodHome != null ? ctx.places().get(periodHome) : null;
        if (place != null && place.city() != null && !place.city().isBlank()
                && (home == null || !place.city().equals(home.city()))) {
            return place.city();
        }
        return placeName(placeId);
    }

    private static TransportMode topMode(List<Day> days, Function<Day, Map<TransportMode, Double>> values) {
        Map<TransportMode, Double> totals = new EnumMap<>(TransportMode.class);
        days.forEach(d -> values.apply(d).forEach((mode, value) -> totals.merge(mode, value, Double::sum)));
        return totals.entrySet().stream()
                .filter(e -> e.getKey() != TransportMode.UNKNOWN && e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static LocalTime medianTime(List<Day> days, Function<Day, LocalTime> value) {
        return medianTime(days.stream().map(value).filter(Objects::nonNull).toList());
    }

    private static LocalTime medianTime(List<LocalTime> times) {
        if (times.isEmpty()) {
            return null;
        }
        double[] seconds = times.stream().mapToDouble(LocalTime::toSecondOfDay).sorted().toArray();
        long minutes = Math.round(median(seconds) / 60.0);
        return LocalTime.ofSecondOfDay((minutes * 60) % 86400);
    }

    private static <T> T mostFrequent(List<T> values) {
        return values.stream().collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static double sum(List<Day> days, ToDoubleFunction<Day> value) {
        return days.stream().mapToDouble(value).sum();
    }

    static double median(double[] sorted) {
        return percentile(sorted, 0.5);
    }

    static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        double index = p * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower);
    }

    private static double bearing(double fromLat, double fromLng, double toLat, double toLng) {
        double phi1 = Math.toRadians(fromLat);
        double phi2 = Math.toRadians(toLat);
        double deltaLambda = Math.toRadians(toLng - fromLng);
        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
