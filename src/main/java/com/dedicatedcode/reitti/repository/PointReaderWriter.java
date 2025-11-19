package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Component;

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
}
