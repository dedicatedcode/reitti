package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.List;

@Service
public class GpxExportService {
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public GpxExportService(RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    public void generateGpxContentStreaming(User user, LocalDate startDate, LocalDate endDate, Writer writer) throws IOException {
        // Write GPX header
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<gpx version=\"1.1\" creator=\"Reitti\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        writer.write("  <metadata>\n");
        writer.write("    <name>Location Data Export</name>\n");
        writer.write("    <desc>Exported location data from " + startDate + " to " + endDate + "</desc>\n");
        writer.write("  </metadata>\n");
        writer.write("  <trk>\n");
        writer.write("    <name>Location Track</name>\n");
        writer.write("    <trkseg>\n");
        
        // Stream location points in batches to avoid loading all into memory
        LocalDate currentDate = startDate;
        
        while (!currentDate.isAfter(endDate)) {
            LocalDate nextDate = currentDate.plusDays(1);
            
            List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndDateRange(
                user, currentDate.atStartOfDay(), nextDate.atStartOfDay());
            
            for (RawLocationPoint point : points) {
                writer.write("      <trkpt lat=\"" + point.getLatitude() + "\" lon=\"" + point.getLongitude() + "\">\n");
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
