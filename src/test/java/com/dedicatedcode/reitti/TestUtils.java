package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import de.siegmar.fastcsv.reader.CsvReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TestUtils {
    private static final GeometryFactory FACTORY = new GeometryFactory();
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.n ZZZZZ");
    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);
    public static List<RawLocationPoint> loadFromCsv(String name) {
        CsvReader reader = CsvReader.builder().build(new InputStreamReader(TestUtils.class.getResourceAsStream(name)));


       return reader.stream().filter(csvRow -> csvRow.getOriginalLineNumber() > 1)
               .map(row -> {

                   Instant timestamp = ZonedDateTime.parse(row.getField(3), DATE_TIME_FORMATTER).toInstant();

                   String pointString = row.getField(5);
                   pointString = pointString.substring(7);
                   double x = Double.parseDouble(pointString.substring(0, pointString.indexOf(" ")));
                   double y = Double.parseDouble(pointString.substring(pointString.indexOf(" ") + 1, pointString.length() - 1));
                   Point point = FACTORY.createPoint(new Coordinate(x, y));
                   return new RawLocationPoint(timestamp, point, Double.parseDouble(row.getField(1)));
               }).toList();
    }

    public static void printGPXFile(String path) {
        try {
            InputStream inputStream = TestUtils.class.getResourceAsStream(path);
            if (inputStream == null) {
                LOGGER.error("Resource not found: [{}]", path);
                return;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);

            NodeList trackPoints = document.getElementsByTagName("trkpt");
            for (int i = 0; i < trackPoints.getLength(); i++) {
                Element trkpt = (Element) trackPoints.item(i);
                double lat = Double.parseDouble(trkpt.getAttribute("lat"));
                double lon = Double.parseDouble(trkpt.getAttribute("lon"));
                
                NodeList timeNodes = trkpt.getElementsByTagName("time");
                if (timeNodes.getLength() > 0) {
                    String timeStr = timeNodes.item(0).getTextContent();
                    Instant timestamp = Instant.parse(timeStr);
                    LOGGER.info("Lat: {}, Lon: {}, Time: {}", lat, lon, timestamp);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error parsing GPX file: ", e);

        }
    }

    public static List<LocationDataRequest.LocationPoint> readFromTableOutput(String path) {
        try {
            InputStream inputStream = TestUtils.class.getResourceAsStream(path);
            if (inputStream == null) {
                LOGGER.error("Resource not found: [{}]", path);
                return List.of();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                return reader.lines()
                        .skip(3) // Skip header line
                        .map(line -> {
                            try {
                                String[] parts = line.split("\\|");
                                if (parts.length >= 3) {
                                    Double accuracy = Double.parseDouble(parts[0].trim());
                                    String timestamp = parts[1].trim().replace(" ", "T") + ":00";

                                    String pointValue  = parts[2].trim();
                                    pointValue = pointValue.substring(6, pointValue.length() - 1);
                                    String[] pointParts = pointValue.split(" ");
                                    double longitude = Double.parseDouble(pointParts[0]);
                                    double latitude = Double.parseDouble(pointParts[1]);

                                    LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
                                    point.setLatitude(latitude);
                                    point.setLongitude(longitude);
                                    point.setTimestamp(timestamp);
                                    point.setAccuracyMeters(accuracy);

                                    return point;
                                }
                                return null;
                            } catch (Exception e) {
                                LOGGER.warn("Failed to parse line: {}", line, e);
                                return null;
                            }
                        })
                        .filter(point -> point != null)
                        .toList();
            }
        } catch (Exception e) {
            LOGGER.error("Error reading table file: {}", path, e);
            return List.of();
        }
    }
}
