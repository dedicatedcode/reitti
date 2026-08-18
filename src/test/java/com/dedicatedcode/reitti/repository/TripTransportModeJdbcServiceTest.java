package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeSegment;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class TripTransportModeJdbcServiceTest {

    @Autowired
    private TestingService testingService;

    @Autowired
    private TripJdbcService tripJdbcService;

    private User user;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
    }

    @Test
    void shouldPersistAndReloadSegments() {
        // Given
        Trip trip = createTrip();
        List<TransportModeSegment> segments = List.of(
                new TransportModeSegment(TransportMode.WALKING, 0, 300, 1000.0),
                new TransportModeSegment(TransportMode.DRIVING, 300, 700, 9000.0)
        );

        // When
        tripJdbcService.update(trip.withSegments(segments));

        // Then
        Trip reloaded = tripJdbcService.findById(trip.getId()).orElseThrow();
        assertThat(reloaded.getSegments()).hasSize(2);
        assertThat(reloaded.getSegments().get(0).mode()).isEqualTo(TransportMode.WALKING);
        assertThat(reloaded.getSegments().get(0).offsetSeconds()).isEqualTo(0);
        assertThat(reloaded.getSegments().get(0).durationSeconds()).isEqualTo(300);
        assertThat(reloaded.getSegments().get(0).distanceMeters()).isEqualTo(1000.0);
        assertThat(reloaded.getSegments().get(1).mode()).isEqualTo(TransportMode.DRIVING);
    }

    @Test
    void shouldAggregateStatisticsAcrossSegments() {
        // Given
        Trip trip = createTrip();
        List<TransportModeSegment> segments = List.of(
                new TransportModeSegment(TransportMode.WALKING, 0, 300, 1000.0),
                new TransportModeSegment(TransportMode.DRIVING, 300, 700, 9000.0)
        );
        tripJdbcService.update(trip.withSegments(segments));

        // When
        List<Object[]> stats = tripJdbcService.findTransportStatisticsByUser(user);

        // Then
        Map<String, Object[]> byMode = stats.stream().collect(Collectors.toMap(r -> (String) r[0], r -> r));
        assertThat(byMode).containsKeys("WALKING", "DRIVING");

        Object[] walking = byMode.get("WALKING");
        assertThat((Double) walking[1]).isEqualTo(1000.0);
        assertThat((Long) walking[2]).isEqualTo(300L);
        assertThat((Long) walking[3]).isEqualTo(1L);

        Object[] driving = byMode.get("DRIVING");
        assertThat((Double) driving[1]).isEqualTo(9000.0);
        assertThat((Long) driving[2]).isEqualTo(700L);
        assertThat((Long) driving[3]).isEqualTo(1L);
    }

    private Trip createTrip() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        SignificantPlace startPlace = testingService.newSignificantPlace(user);
        SignificantPlace endPlace = testingService.newSignificantPlace(user, 53.48278089848833, 9.32412809124706, "end");
        ProcessedVisit startVisit = testingService.createVisit(user, startPlace, now.minusSeconds(3600), now.minusSeconds(1800));
        ProcessedVisit endVisit = testingService.createVisit(user, endPlace, now.minusSeconds(1200), now);
        return testingService.createTrip(user, startVisit, endVisit, TransportMode.WALKING);
    }
}
