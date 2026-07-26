package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface SpatialCoverageService {

    Long getLevelCellForPoint(double latitude, double longitude, int resolution);

    void postPromotion(List<Long> insertedIds);

    Optional<CoverageInformation> getCoverageInformation(User user, Device device, long osmId, Locale locale);

    void postDeletion(List<Long> deletedPointIds);
}
