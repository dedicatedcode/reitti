package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class LocationDataService {

    private final RawLocationPointRepository rawLocationPointRepository;

    @Autowired
    public LocationDataService(RawLocationPointRepository rawLocationPointRepository) {
        this.rawLocationPointRepository = rawLocationPointRepository;
    }

    @Transactional
    public List<RawLocationPoint> processLocationData(User user, List<LocationDataRequest.LocationPoint> points) {
        List<RawLocationPoint> savedPoints = new ArrayList<>();
        
        for (LocationDataRequest.LocationPoint point : points) {
            RawLocationPoint savedPoint = processSingleLocationPoint(user, point);
            savedPoints.add(savedPoint);
        }
        
        return savedPoints;
    }

    @Transactional
    public RawLocationPoint processSingleLocationPoint(User user, LocationDataRequest.LocationPoint point) {
        RawLocationPoint locationPoint = new RawLocationPoint();
        locationPoint.setUser(user);
        locationPoint.setLatitude(point.getLatitude());
        locationPoint.setLongitude(point.getLongitude());
        locationPoint.setTimestamp(Instant.parse(point.getTimestamp()));
        locationPoint.setAccuracyMeters(point.getAccuracyMeters());
        locationPoint.setActivityProvided(point.getActivity());
        
        return rawLocationPointRepository.save(locationPoint);
    }
}
