package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VisitService {
    private static final Logger logger = LoggerFactory.getLogger(VisitService.class);

    private final VisitRepository visitRepository;

    @Autowired
    public VisitService(
            VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    public void processStayPoints(User user, List<StayPoint> stayPoints) {
        logger.info("Processing {} stay points for user {}", stayPoints.size(), user.getUsername());

        for (StayPoint stayPoint : stayPoints) {
            Visit visit = createVisit(user, stayPoint.getLongitude(), stayPoint.getLatitude(), stayPoint);
            visitRepository.save(visit);
        }
    }


    private Visit createVisit(User user, Double longitude, Double latitude, StayPoint stayPoint) {
        Visit visit = new Visit();
        visit.setUser(user);
        visit.setLongitude(longitude);
        visit.setLatitude(latitude);
        visit.setStartTime(stayPoint.getArrivalTime());
        visit.setEndTime(stayPoint.getDepartureTime());
        visit.setDurationSeconds(stayPoint.getDurationSeconds());
        return visit;
    }
}
