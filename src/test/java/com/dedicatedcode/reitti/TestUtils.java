package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.GeoPoint;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import de.siegmar.fastcsv.reader.CsvReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
}
