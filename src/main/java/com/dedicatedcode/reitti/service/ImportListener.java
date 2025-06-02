package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;

import java.util.List;

public interface ImportListener {

    void handleImport(User user, List<LocationDataRequest.LocationPoint> data);
}
