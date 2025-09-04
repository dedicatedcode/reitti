package com.dedicatedcode.reitti.repository;

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

    public Point read(String wkt) {
        try {
            return wktReader.read(wkt).getCentroid();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public String write(double x, double y) {
        return geometryFactory.createPoint(new Coordinate(x, y)).toString();
    }
}
