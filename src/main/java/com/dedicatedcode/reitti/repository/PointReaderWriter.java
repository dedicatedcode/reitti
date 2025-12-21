package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PointReaderWriter {

    private final WKTReader wktReader;
    private final GeometryFactory geometryFactory;

    public PointReaderWriter(GeometryFactory geometryFactory) {
        this.wktReader = new WKTReader(geometryFactory);
        this.geometryFactory = geometryFactory;
    }

    public GeoPoint read(String wkt) {
        // Fast manual parsing for POINT format: POINT(longitude latitude)
        if (wkt != null && wkt.startsWith("POINT(") && wkt.endsWith(")")) {
            try {
                // Extract coordinates from "POINT(13.05490119382739 40.79844924621284)"
                String coords = wkt.substring(6, wkt.length() - 1); // Remove "POINT(" and ")"
                String[] parts = coords.split(" ");
                if (parts.length == 2) {
                    double longitude = Double.parseDouble(parts[0]);
                    double latitude = Double.parseDouble(parts[1]);
                    return new GeoPoint(latitude, longitude);
                }
            } catch (NumberFormatException e) {
                // Fall back to WKT reader if manual parsing fails
            }
        }
        
        // Fallback to original WKT reader for non-POINT formats or parsing errors
        try {
            Point centroid = wktReader.read(wkt).getCentroid();
            return new GeoPoint(centroid.getY(), centroid.getX());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public String write(double x, double y) {
        return geometryFactory.createPoint(new Coordinate(x, y)).toString();
    }

    public String write(GeoPoint point) {
        return write(point.longitude(), point.latitude());
    }

    public String polygonToWkt(List<GeoPoint> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return null;
        }

        StringBuilder wkt = new StringBuilder("POLYGON((");
        for (int i = 0; i < polygon.size(); i++) {
            GeoPoint point = polygon.get(i);
            wkt.append(point.longitude()).append(" ").append(point.latitude());
            if (i < polygon.size() - 1) {
                wkt.append(", ");
            }
        }

        // Close the polygon by adding the first point again if not already closed
        GeoPoint first = polygon.getFirst();
        GeoPoint last = polygon.getLast();
        if (!first.equals(last)) {
            wkt.append(", ").append(first.longitude()).append(" ").append(first.latitude());
        }

        wkt.append("))");
        return wkt.toString();
    }

    public List<GeoPoint> wktToPolygon(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) {
            return null;
        }

        // Parse WKT format: POLYGON((lon1 lat1, lon2 lat2, ...))
        String coordinates = wkt.substring(wkt.indexOf("((") + 2, wkt.lastIndexOf("))"));
        String[] points = coordinates.split(",");

        List<GeoPoint> polygon = new ArrayList<>();
        for (String point : points) {
            String[] coords = point.trim().split("\\s+");
            if (coords.length >= 2) {
                double longitude = Double.parseDouble(coords[0]);
                double latitude = Double.parseDouble(coords[1]);
                polygon.add(GeoPoint.from(latitude, longitude));
            }
        }

        return polygon.isEmpty() ? null : polygon;
    }
}
