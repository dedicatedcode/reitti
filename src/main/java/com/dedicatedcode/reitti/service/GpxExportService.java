package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GpxExportService {

    private final SourceLocationPointJdbcService locationPointJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public GpxExportService(SourceLocationPointJdbcService locationPointJdbcService,
                            RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.locationPointJdbcService = locationPointJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    /**
     * Generates GPX content for the specified user, device and time range, and writes it directly to the provided writer.
     * The location data is exported in batches to minimize memory usage.
     * <p>
     * Note: start and end date should be given in UTC
     *
     * @param user     the user whose location data will be exported
     * @param device   the device to export data for, or {@code null} for the main device
     * @param start    the start time of the export range
     * @param end      the end time of the export range
     * @param writer   the writer to which the GPX content will be streamed
     * @throws IOException if an I/O error occurs during writing
     */
    public void generateGpxContentStreaming(User user, Device device, Instant start, Instant end, Writer writer) throws IOException {
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

            List<SourceLocationPoint> points = locationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, device, currentDate, nextDate, true, true);

            for (SourceLocationPoint point : points) {
                writePoint(writer, point.getLatitude(), point.getLongitude(), point.getElevationMeters(), point.getTimestamp(), point.getAccuracyMeters());
            }

            writer.flush(); // Flush periodically
            currentDate = nextDate;
        }

        // Write GPX footer
        writeFooter(writer);
    }

    /**
     * Generates GPX content for the consolidated timeline of the specified user (raw_location_points)
     * and time range, and writes it directly to the provided writer.
     * <p>
     * Synthetic points are included, ignored points are excluded.
     *
     * @param user   the user whose location data will be exported
     * @param start  the start time of the export range
     * @param end    the end time of the export range
     * @param writer the writer to which the GPX content will be streamed
     * @throws IOException if an I/O error occurs during writing
     */
    public void generateTimelineGpxContentStreaming(User user, Instant start, Instant end, Writer writer) throws IOException {
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

        for (RawLocationPoint point : rawLocationPointJdbcService.streamByUserAndTimestampBetween(user, start, end, true, false)) {
            writePoint(writer, point.getLatitude(), point.getLongitude(), point.getElevationMeters(), point.getTimestamp(), point.getAccuracyMeters());
        }

        // Write GPX footer
        writeFooter(writer);
    }

    private void writePoint(Writer writer, Double latitude, Double longitude, Double elevationMeters, Instant timestamp, Double accuracyMeters) {
        try {
            writer.write("      <trkpt lat=\"" + latitude + "\" lon=\"" + longitude + "\">\n");

            if (elevationMeters != null) {
                writer.write("        <ele>" + elevationMeters + "</ele>\n");
            }

            writer.write("        <time>" + timestamp.toString() + "</time>\n");

            if (accuracyMeters != null) {
                writer.write("        <extensions>\n");
                writer.write("          <accuracy>" + accuracyMeters + "</accuracy>\n");
                writer.write("        </extensions>\n");
            }

            writer.write("      </trkpt>\n");
        } catch (IOException e) {
            throw new RuntimeException("Error writing GPX point", e);
        }
    }

    private void writeFooter(Writer writer) throws IOException {
        writer.write("    </trkseg>\n");
        writer.write("  </trk>\n");
        writer.write("</gpx>");
        writer.flush();
    }
}