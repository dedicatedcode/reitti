package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.workbench.MovedPointDto;
import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface SpatialCoverageService {

    Long getLevelCellForPoint(double latitude, double longitude, int resolution);

    void postPromotion(List<Long> insertedIds);

    void postDeletion(List<Long> deletedPointIds);

    void preMove(List<MovedPointDto> movedPoints);

    void postSourceRecalculation(List<Long> sourcePointIds);

    Optional<CoverageInformation> getCoverageInformation(User user, long osmId, Locale locale);

    Optional<CoverageInformation> getCoverageInformation(User user, Device device, long osmId, Locale locale);

    List<CoverageInformation> getCoverage(User user, Instant until, Locale locale);

    List<CoverageInformation> getDeviceCoverage(User user, Device device, Instant until, Locale locale);
}
