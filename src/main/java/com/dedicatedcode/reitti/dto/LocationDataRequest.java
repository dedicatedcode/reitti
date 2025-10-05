package com.dedicatedcode.reitti.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Objects;

public class LocationDataRequest {
    
    @NotEmpty
    private List<@Valid LocationPoint> points;

    public List<LocationPoint> getPoints() {
        return points;
    }

    public void setPoints(List<LocationPoint> points) {
        this.points = points;
    }

    @Override
    public String toString() {
        return "LocationDataRequest{" +
                "points=" + points +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LocationDataRequest that = (LocationDataRequest) o;
        return Objects.equals(points, that.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(points);
    }

}
