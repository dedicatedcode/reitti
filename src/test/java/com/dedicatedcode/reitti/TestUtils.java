package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.Trip;
import de.siegmar.fastcsv.reader.CsvReader;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    public static void assertVisit(ProcessedVisit processedVisit, String startTime, String endTime, GeoPoint location) {
        assertEquals(Instant.parse(startTime).truncatedTo(ChronoUnit.SECONDS), processedVisit.getStartTime().truncatedTo(ChronoUnit.SECONDS));
        assertEquals(Instant.parse(endTime).truncatedTo(ChronoUnit.SECONDS), processedVisit.getEndTime().truncatedTo(ChronoUnit.SECONDS));
        GeoPoint currentLocation = new GeoPoint(processedVisit.getPlace().getLatitudeCentroid(), processedVisit.getPlace().getLongitudeCentroid());
        assertTrue(location.near(currentLocation), "Locations are not near to each other. \nExpected [" + currentLocation + "] to be in range \nto [" + location + "]");
    }

    public static void assertTrip(Trip trip, String startTime, String endTime, GeoPoint startLocation, GeoPoint endLocation) {
        assertEquals(Instant.parse(startTime).truncatedTo(ChronoUnit.SECONDS), trip.getStartTime().truncatedTo(ChronoUnit.SECONDS));
        assertEquals(Instant.parse(endTime).truncatedTo(ChronoUnit.SECONDS), trip.getEndTime().truncatedTo(ChronoUnit.SECONDS));

        GeoPoint actualStartLocation = GeoPoint.from(trip.getStartVisit().getPlace().getLatitudeCentroid(), trip.getStartVisit().getPlace().getLongitudeCentroid());
        assertTrue(startLocation.near(actualStartLocation),
            "Start locations are not near to each other. \nExpected [" + actualStartLocation + "] to be in range \nto [" + startLocation + "]");

        GeoPoint actualEndLocation = GeoPoint.from(trip.getEndVisit().getPlace().getLatitudeCentroid(), trip.getEndVisit().getPlace().getLongitudeCentroid());
        assertTrue(endLocation.near(actualEndLocation),
            "End locations are not near to each other. \nExpected [" + actualEndLocation + "] to be in range \nto [" + endLocation + "]");
    }
}
