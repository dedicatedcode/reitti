package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VisitMergingServiceTest extends AbstractIntegrationTest {

    @Autowired
    private VisitMergingService visitMergingService;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private ProcessedVisitRepository processedVisitRepository;

    @Test
    void shouldMergeVisitsInTimeFrame() {
        importUntilVisits("/data/gpx/20250531.gpx");

        visitMergingService.mergeVisits(new MergeVisitEvent(user.getUsername(), null, null));

        assertEquals(0, visitRepository.findByUserAndProcessedFalse(user).size());

        List<StayPoint> expectedVisits = new ArrayList<>();
        expectedVisits.add(new StayPoint(53.86334557300011, 10.701107468000021, null, null, null)); //Moltkestr.
        expectedVisits.add(new StayPoint(53.86334557300011, 10.701107468000021, null, null, null)); //Moltkestr.
        expectedVisits.add(new StayPoint(53.86889230000001, 10.680612066666669, null, null, null)); //Diele.
        expectedVisits.add(new StayPoint(53.86334557300011, 10.701107468000021, null, null, null)); //Moltkestr.
        expectedVisits.add(new StayPoint(53.86889230000001, 10.680612066666669, null, null, null)); //Diele.
        expectedVisits.add(new StayPoint(53.87306318052629, 10.732658768947365, null, null, null)); //Garten.
        expectedVisits.add(new StayPoint(53.87101884785715, 10.745859928571429, null, null, null)); //Fimila
        expectedVisits.add(new StayPoint(53.871636138461504, 10.747298292564096, null, null, null)); //Obi
        expectedVisits.add(new StayPoint(53.87216447272729,10.747552527272727, null, null, null)); //Obi
        expectedVisits.add(new StayPoint(53.873079353158, 10.73264953157896, null, null, null)); //Garten
        expectedVisits.add(new StayPoint(53.86334557300011, 10.701107468000021, null, null, null)); //Moltkestr.


        List<ProcessedVisit> actual = this.processedVisitRepository.findByUserOrderByStartTime(user);
        assertEquals(expectedVisits.size(), actual.size());
    }
}