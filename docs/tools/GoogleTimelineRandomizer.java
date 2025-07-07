import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class GoogleTimelineRandomizer {

    private static final Random random = new Random();

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: GoogleTimelineRandomizer --file=<path> --output-dir=<dir> [--max-semantic-segments=<num>] [--max-raw-signals=<num>]");
            System.exit(1);
        }

        String filePath = null;
        String outputDir = null;
        Integer maxSemanticSegments = null;
        Integer maxRawSignals = null;

        // Parse named arguments
        for (String arg : args) {
            if (arg.startsWith("--file=")) {
                filePath = arg.substring("--file=".length());
            } else if (arg.startsWith("--output-dir=")) {
                outputDir = arg.substring("--output-dir=".length());
            } else if (arg.startsWith("--max-semantic-segments=")) {
                try {
                    maxSemanticSegments = Integer.parseInt(arg.substring("--max-semantic-segments=".length()));
                } catch (NumberFormatException e) {
                    System.err.println("Invalid max-semantic-segments value: " + arg);
                    System.exit(1);
                }
            } else if (arg.startsWith("--max-raw-signals=")) {
                try {
                    maxRawSignals = Integer.parseInt(arg.substring("--max-raw-signals=".length()));
                } catch (NumberFormatException e) {
                    System.err.println("Invalid max-raw-signals value: " + arg);
                    System.exit(1);
                }
            } else {
                System.err.println("Unknown argument: " + arg);
                System.err.println("Usage: GoogleTimelineRandomizer --file-path=<path> --output-dir=<dir> [--max-semantic-segments=<num>] [--max-raw-signals=<num>]");
                System.exit(1);
            }
        }

        // Validate required arguments
        if (filePath == null || outputDir == null) {
            System.err.println("Missing required arguments: --file-path and --output-dir are required");
            System.err.println("Usage: GoogleTimelineRandomizer --file-path=<path> --output-dir=<dir> [--max-semantic-segments=<num>] [--max-raw-signals=<num>]");
            System.exit(1);
        }

        int timeAdjustments = random.nextInt(3600 * 7);
        if (random.nextBoolean()) timeAdjustments *= -1;

        double longitudeAdjustment = random.nextDouble(-20, 20);
        
        try {
            load(filePath, outputDir, timeAdjustments, longitudeAdjustment, maxSemanticSegments, maxRawSignals);
        } catch (IOException e) {
            System.err.println("Error loading file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void load(String filePath, String outputDir, int timeAdjustmentInMinutes, double longitudeAdjustment, Integer maxSemanticSegments, Integer maxRawSignals) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(new FileReader(filePath));

        ArrayNode semanticSegments = (ArrayNode) root.path("semanticSegments");
        ArrayNode rawSignals = (ArrayNode) root.path("rawSignals");
        root.set("userLocationProfile", new ObjectNode(JsonNodeFactory.instance));
        // Limit semantic segments if specified
        if (maxSemanticSegments != null && semanticSegments.size() > maxSemanticSegments) {
            // Create new array with limited segments
            ArrayNode limitedSemanticSegments = semanticSegments.arrayNode();
            for (int i = 0; i < maxSemanticSegments; i++) {
                limitedSemanticSegments.add(semanticSegments.get(i));
            }
            ((ObjectNode) root).set("semanticSegments", limitedSemanticSegments);
            semanticSegments = limitedSemanticSegments;
        }

        // Limit raw signals if specified
        if (maxRawSignals != null && rawSignals.size() > maxRawSignals) {
            // Create new array with limited signals
            ArrayNode limitedRawSignals = rawSignals.arrayNode();
            for (int i = 0; i < maxRawSignals; i++) {
                limitedRawSignals.add(rawSignals.get(i));
            }
            root.set("rawSignals", limitedRawSignals);
            rawSignals = limitedRawSignals;
        }

        for (JsonNode segment : semanticSegments) {
            ObjectNode current = (ObjectNode) segment;
            if (current.has("startTime")) {
                adjustTime(timeAdjustmentInMinutes, current.get("startTime"), current, "startTime");
            }
            if (current.has("endTime")) {
                adjustTime(timeAdjustmentInMinutes, current.get("endTime"), current, "endTime");
            }
            if (current.has("timelinePath")) {
                ArrayNode timelinePath = (ArrayNode) current.get("timelinePath");
                for (JsonNode jsonNode : timelinePath) {
                    if (jsonNode.isObject()) {
                        if (jsonNode.has("time")) {
                            adjustTime(timeAdjustmentInMinutes, jsonNode.get("time"), (ObjectNode) jsonNode, "time");
                        }
                        if (jsonNode.has("point")) {
                            adjustPoint(longitudeAdjustment, jsonNode.get("point"), (ObjectNode) jsonNode, "point");
                        }
                    }

                }
            }
            if (current.has("visit")) {
                ObjectNode visit = (ObjectNode) current.get("visit");
                if (visit.has("topCandidate")) {
                    ObjectNode topCandidate = (ObjectNode) visit.get("topCandidate");
                    if (topCandidate.has("placeLocation")) {
                        ObjectNode placeLocation = (ObjectNode) topCandidate.get("placeLocation");
                        if (placeLocation.has("latLng")) {
                            adjustPoint(longitudeAdjustment, placeLocation.get("latLng"), placeLocation, "latLng");
                        }
                    }
                }
            }
            if (current.has("activity")) {
                ObjectNode activity = (ObjectNode) current.get("activity");
                if (activity.has("start")) {
                    ObjectNode start = (ObjectNode) activity.get("start");
                    if (start.has("latLng")) {
                        adjustPoint(longitudeAdjustment, start.get("latLng"), start, "latLng");
                    }
                }
                if (activity.has("end")) {
                    ObjectNode end = (ObjectNode) activity.get("end");
                    if (end.has("latLng")) {
                        adjustPoint(longitudeAdjustment, end.get("latLng"), end, "latLng");
                    }
                }
            }
        }

        for (JsonNode rawSignal : rawSignals) {
            ObjectNode current = (ObjectNode) rawSignal;
            if (current.has("position")) {
                ObjectNode position = (ObjectNode) current.get("position");
                if (position.has("LatLng")) {
                    adjustPoint(longitudeAdjustment, position.get("LatLng"), position, "LatLng");
                }
                if (position.has("timestamp")) {
                    adjustTime(timeAdjustmentInMinutes, position.get("timestamp"), position, "timestamp");
                }
            }

            if (current.has("wifiScan")) {
                current.set("wifiScan", new ObjectNode(JsonNodeFactory.instance));
            }
        }
        // Create output filename by appending date before extension
        Path inputPath = Paths.get(filePath);
        String inputFileName = inputPath.getFileName().toString();
        String nameWithoutExtension;
        String extension;

        int lastDotIndex = inputFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            nameWithoutExtension = inputFileName.substring(0, lastDotIndex);
            extension = inputFileName.substring(lastDotIndex);
        } else {
            nameWithoutExtension = inputFileName;
            extension = "";
        }

        String outputFileName = nameWithoutExtension + "_randomized" + extension;
        Path outputPath = Paths.get(outputDir, outputFileName);

        // Ensure output directory exists
        Files.createDirectories(Paths.get(outputDir));

        // Write randomized JSON to output file
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), root);

        System.out.println("Filtered data written to: " + outputPath);
    }

    private static void adjustPoint(double longitudeAdjustment, JsonNode point, ObjectNode jsonNode, String name) {
        String pointText = point.asText();
        
        // Parse the point text in format "-27.4127738°, 153.0617186°"
        // Remove degree symbols and split by comma
        String cleaned = pointText.replace("°", "").trim();
        String[] parts = cleaned.split(",");
        
        if (parts.length == 2) {
            try {
                double latitude = Double.parseDouble(parts[0].trim());
                double longitude = Double.parseDouble(parts[1].trim());
                
                // Apply longitude adjustment
                double adjustedLongitude = longitude + longitudeAdjustment;
                
                // Format back to the original format with degree symbols
                String adjustedPoint = String.format("%.7f°, %.7f°", latitude, adjustedLongitude);
                
                // Add the adjusted value to the JSON node
                jsonNode.put(name, adjustedPoint);
            } catch (NumberFormatException e) {
                System.err.println("Failed to parse point coordinates: " + pointText);
            }
        } else {
            System.err.println("Invalid point format: " + pointText);
        }
    }

    private static void adjustTime(int timeAdjustmentInMinutes, JsonNode jsonNode, ObjectNode current, String name) {
        String currentValue = jsonNode.asText();
        ZonedDateTime currentTime = ZonedDateTime.parse(currentValue);
        long newTime = currentTime.toEpochSecond() +  (timeAdjustmentInMinutes * 60L);
        current.put(name, ZonedDateTime.ofInstant(Instant.ofEpochSecond(newTime), currentTime.getZone()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
