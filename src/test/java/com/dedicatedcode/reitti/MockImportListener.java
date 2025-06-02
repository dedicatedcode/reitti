package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImportListener;

import java.util.ArrayList;
import java.util.List;

public class MockImportListener implements ImportListener {
    private final List<LocationDataRequest.LocationPoint> points = new ArrayList<>();

    @Override
    public void handleImport(User user, List<LocationDataRequest.LocationPoint> data) {
        points.addAll(data);
    }

    public List<LocationDataRequest.LocationPoint> getPoints() {
        return points;
    }

    public void clearAll() {
        this.points.clear();
    }
}
