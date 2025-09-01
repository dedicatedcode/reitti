package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/export")
public class ExportDataController {
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public ExportDataController(RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    @GetMapping("/data-content")
    public String getExportDataContent(@AuthenticationPrincipal User user,
                                      @RequestParam(required = false) String startDate,
                                      @RequestParam(required = false) String endDate,
                                      Model model) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now();
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        
        // Get raw location points for the date range
        List<RawLocationPoint> rawLocationPoints = rawLocationPointJdbcService.findByUserAndDateRange(
            user, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        model.addAttribute("rawLocationPoints", rawLocationPoints);
        
        return "fragments/export-data :: export-data-content";
    }
    
    @PostMapping("/gpx")
    public ResponseEntity<String> exportGpx(@AuthenticationPrincipal User user,
                                           @RequestParam String startDate,
                                           @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            List<RawLocationPoint> rawLocationPoints = rawLocationPointJdbcService.findByUserAndDateRange(
                user, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
            
            String gpxContent = generateGpxContent(rawLocationPoints, start, end);
            
            String filename = String.format("location_data_%s_to_%s.gpx", 
                start.format(DateTimeFormatter.ISO_LOCAL_DATE),
                end.format(DateTimeFormatter.ISO_LOCAL_DATE));
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(gpxContent);
                
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Error generating GPX file: " + e.getMessage());
        }
    }
    
    private String generateGpxContent(List<RawLocationPoint> points, LocalDate startDate, LocalDate endDate) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();
            
            // Create root GPX element
            Element gpx = document.createElement("gpx");
            gpx.setAttribute("version", "1.1");
            gpx.setAttribute("creator", "Reitti");
            gpx.setAttribute("xmlns", "http://www.topografix.com/GPX/1/1");
            document.appendChild(gpx);
            
            // Create metadata
            Element metadata = document.createElement("metadata");
            gpx.appendChild(metadata);
            
            Element name = document.createElement("name");
            name.setTextContent("Location Data Export");
            metadata.appendChild(name);
            
            Element desc = document.createElement("desc");
            desc.setTextContent("Exported location data from " + startDate + " to " + endDate);
            metadata.appendChild(desc);
            
            // Create track
            Element trk = document.createElement("trk");
            gpx.appendChild(trk);
            
            Element trkName = document.createElement("name");
            trkName.setTextContent("Location Track");
            trk.appendChild(trkName);
            
            Element trkseg = document.createElement("trkseg");
            trk.appendChild(trkseg);
            
            // Add track points
            for (RawLocationPoint point : points) {
                Element trkpt = document.createElement("trkpt");
                trkpt.setAttribute("lat", String.valueOf(point.getLatitude()));
                trkpt.setAttribute("lon", String.valueOf(point.getLongitude()));
                trkseg.appendChild(trkpt);
                
                Element time = document.createElement("time");
                time.setTextContent(point.getTimestamp().toString());
                trkpt.appendChild(time);
                
                if (point.getAccuracyMeters() != null) {
                    Element extensions = document.createElement("extensions");
                    trkpt.appendChild(extensions);
                    
                    Element accuracy = document.createElement("accuracy");
                    accuracy.setTextContent(String.valueOf(point.getAccuracyMeters()));
                    extensions.appendChild(accuracy);
                }
            }
            
            // Transform to string
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            
            return writer.toString();
            
        } catch (Exception e) {
            throw new RuntimeException("Error generating GPX content", e);
        }
    }
}
