package com.dedicatedcode.reitti.repository;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Component;

@Component
public class PointReaderWriter {

    private final WKTReader wktReader;
    public PointReaderWriter(GeometryFactory geometryFactory) {
        this.wktReader = new WKTReader(geometryFactory);
    }

    public Point read(String wkt) {
        try {
            return wktReader.read(wkt).getCentroid();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

    }
}
