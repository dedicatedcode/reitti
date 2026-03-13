package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import de.siegmar.fastcsv.reader.CsvReader;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TestUtils {
    private static final GeometryFactory FACTORY = new GeometryFactory();
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.n ZZZZZ");
    public static List<RawLocationPoint> loadFromCsv(String name) {
        CsvReader reader = CsvReader.builder().build(new InputStreamReader(TestUtils.class.getResourceAsStream(name)));


       return reader.stream().filter(csvRow -> csvRow.getOriginalLineNumber() > 1)
               .map(row -> {

                   Instant timestamp = ZonedDateTime.parse(row.getField(3), DATE_TIME_FORMATTER).toInstant();

                   String pointString = row.getField(5);
                   pointString = pointString.substring(7);
                   double longitude = Double.parseDouble(pointString.substring(0, pointString.indexOf(" ")));
                   double latitude = Double.parseDouble(pointString.substring(pointString.indexOf(" ") + 1, pointString.length() - 1));
                   GeoPoint point = new GeoPoint(latitude, longitude);
                   return new RawLocationPoint(timestamp, point, Double.parseDouble(row.getField(1)));
               }).toList();
    }

}
