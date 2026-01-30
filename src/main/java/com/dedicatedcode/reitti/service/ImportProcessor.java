package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationPoint2;
import com.dedicatedcode.reitti.model.security.User;

import java.util.List;

public interface ImportProcessor {
    void processBatch(User user, List<LocationPoint2> batch);
    void scheduleProcessingTrigger(String username);
    boolean isIdle();
}
