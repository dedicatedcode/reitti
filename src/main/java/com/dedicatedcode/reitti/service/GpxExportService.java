package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GpxExportService {
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public GpxExportService(RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    /**
     * Generates GPX content for the specified user and time range, and writes it directly to the provided writer.
     * The location data is exported in batches to minimize memory usage.
     * <p>
     * Note: start and end date should be given in UTC
     *
     * @param user the user whose location data will be exported
     * @param start the start time of the export range
     * @param end the end time of the export range
     * @param writer the writer to which the GPX content will be streamed
     * @param relevant controls if we export only the relevant data for processing (true) or only the imported data (false)
     * @throws IOException if an I/O error occurs during writing
     */
    public void generateGpxContentStreaming(User user, Instant start, Instant end, Writer writer, boolean relevant) throws IOException {
        // Write GPX header
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<gpx version=\"1.1\" creator=\"Reitti\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        writer.write("  <metadata>\n");
        writer.write("    <name>Location Data Export</name>\n");
        writer.write("    <desc>Exported location data from " + start + " to " + end + "</desc>\n");
        writer.write("  </metadata>\n");
        writer.write("  <trk>\n");
        writer.write("    <name>Location Track</name>\n");
        writer.write("    <trkseg>\n");
        
        // Stream location points in batches to avoid loading all into memory
        Instant currentDate = start;
        
        while (!currentDate.isAfter(end)) {
            Instant nextDate = currentDate.plus(1, ChronoUnit.DAYS);
            
            List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                user, currentDate, nextDate, relevant, !relevant);
            
            for (RawLocationPoint point : points) {
                writer.write("      <trkpt lat=\"" + point.getLatitude() + "\" lon=\"" + point.getLongitude() + "\">\n");
                
                if (point.getElevationMeters() != null) {
                    writer.write("        <ele>" + point.getElevationMeters() + "</ele>\n");
                }
                
                writer.write("        <time>" + point.getTimestamp().toString() + "</time>\n");
                
                if (point.getAccuracyMeters() != null) {
                    writer.write("        <extensions>\n");
                    writer.write("          <accuracy>" + point.getAccuracyMeters() + "</accuracy>\n");
                    writer.write("        </extensions>\n");
                }
                
                writer.write("      </trkpt>\n");
            }
            
            writer.flush(); // Flush periodically
            currentDate = nextDate;
        }
        
        // Write GPX footer
        writer.write("    </trkseg>\n");
        writer.write("  </trk>\n");
        writer.write("</gpx>");
        writer.flush();
    }
}
