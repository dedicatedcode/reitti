import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileReader;
import java.io.IOException;
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
            System.err.println("Usage: GoogleTimelineRandomizer --file=<path> --output-dir=<dir> --mode=<android|ios> [--max-semantic-segments=<num>] [--max-raw-signals=<num>]");
            System.exit(1);
        }

        String filePath = null;
        String outputDir = null;
        String mode = null;
        Integer maxSemanticSegments = null;
        Integer maxRawSignals = null;

        // Parse named arguments
        for (String arg : args) {
            if (arg.startsWith("--file=")) {
                filePath = arg.substring("--file=".length());
            } else if (arg.startsWith("--output-dir=")) {
                outputDir = arg.substring("--output-dir=".length());
            } else if (arg.startsWith("--mode=")) {
                mode = arg.substring("--mode=".length());
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
                System.err.println("Usage: GoogleTimelineRandomizer --file=<path> --output-dir=<dir> --mode=<android|ios> [--max-semantic-segments=<num>] [--max-raw-signals=<num>]");
                System.exit(1);
            }
        }

        // Validate required arguments
        if (filePath == null || outputDir == null || mode == null) {
            System.err.println("Missing required arguments: --file, --output-dir and --mode are required");
            System.err.println("Usage: GoogleTimelineRandomizer --file=<path> --output-dir=<dir> --mode=<android|ios> [--max-semantic-segments=<num>] [--max-raw-signals=<num>]");
            System.exit(1);
        }

        // Validate mode argument
        if (!mode.equals("android") && !mode.equals("ios")) {
            System.err.println("Invalid mode value: " + mode + ". Must be 'android' or 'ios'");
            System.exit(1);
        }

        int timeAdjustments = random.nextInt(3600 * 7);
        if (random.nextBoolean()) timeAdjustments *= -1;

        double longitudeAdjustment = random.nextDouble(-20, 20);
        
        try {
            load(filePath, outputDir, mode, timeAdjustments, longitudeAdjustment, maxSemanticSegments, maxRawSignals);
        } catch (IOException e) {
            System.err.println("Error loading file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void load(String filePath, String outputDir, String mode, int timeAdjustmentInMinutes, double longitudeAdjustment, Integer maxSemanticSegments, Integer maxRawSignals) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        if (mode.equals("android")) {
            root = modifyAndroidFile((ObjectNode) mapper.readTree(new FileReader(filePath)), timeAdjustmentInMinutes, longitudeAdjustment, maxSemanticSegments, maxRawSignals);
        } else if (mode.equals("ios")) {
            ArrayNode semanticSegments = (ArrayNode) mapper.readTree(new FileReader(filePath));
            if (maxSemanticSegments == null) {
                root = modifyIosFile(semanticSegments, timeAdjustmentInMinutes, longitudeAdjustment);
            } else {
                ArrayNode newArray = mapper.createArrayNode();
                for (int i = 0; i < maxSemanticSegments; i++) {
                    newArray.add(semanticSegments.get(i));
                }
                root = modifyIosFile(newArray, timeAdjustmentInMinutes, longitudeAdjustment);
            }
        } else {
            throw new IOException("Invalid mode value: " + mode);
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

    private static JsonNode modifyIosFile(ArrayNode semanticSegments, int timeAdjustmentInMinutes, double longitudeAdjustment) {
        for (JsonNode semanticSegment : semanticSegments) {
            ObjectNode current = (ObjectNode) semanticSegment;
            if (current.has("endTime")) {
                adjustTime(timeAdjustmentInMinutes, current, "endTime");
            }
            if (current.has("startTime")) {
                adjustTime(timeAdjustmentInMinutes, current, "startTime");
            }

            if (current.has("activity")) {
                ObjectNode activity = (ObjectNode) current.get("activity");
                if (activity.has("start")) {
                    adjustGeoPoint(longitudeAdjustment, activity, "start");
                }
                if (activity.has("end")) {
                    adjustGeoPoint(longitudeAdjustment, activity, "end");
                }
            }
            if (current.has("visit")) {
                ObjectNode visit = (ObjectNode) current.get("visit");
                if (visit.has("topCandidate") && visit.path("topCandidate").has("placeLocation")) {
                    adjustGeoPoint(longitudeAdjustment, (ObjectNode) visit.get("topCandidate"), "placeLocation");
                }
            }
            if (current.has("timelinePath")) {
                ArrayNode timelinePath = (ArrayNode) current.get("timelinePath");
                for (JsonNode jsonNode : timelinePath) {
                    if (jsonNode.has("point")) {
                        adjustGeoPoint(longitudeAdjustment, (ObjectNode) jsonNode, "point");
                    }
                }
            }
        }

        return semanticSegments;
    }

    private static JsonNode modifyAndroidFile(ObjectNode root, int timeAdjustmentInMinutes, double longitudeAdjustment, Integer maxSemanticSegments, Integer maxRawSignals) {


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
                adjustTime(timeAdjustmentInMinutes, current, "startTime");
            }
            if (current.has("endTime")) {
                adjustTime(timeAdjustmentInMinutes, current, "endTime");
            }
            if (current.has("timelinePath")) {
                ArrayNode timelinePath = (ArrayNode) current.get("timelinePath");
                for (JsonNode jsonNode : timelinePath) {
                    if (jsonNode.isObject()) {
                        if (jsonNode.has("time")) {
                            adjustTime(timeAdjustmentInMinutes, (ObjectNode) jsonNode, "time");
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

        if (rawSignals != null) {
            for (JsonNode rawSignal : rawSignals) {
                ObjectNode current = (ObjectNode) rawSignal;
                if (current.has("position")) {
                    ObjectNode position = (ObjectNode) current.get("position");
                    if (position.has("LatLng")) {
                        adjustPoint(longitudeAdjustment, position.get("LatLng"), position, "LatLng");
                    }
                    if (position.has("timestamp")) {
                        adjustTime(timeAdjustmentInMinutes, position, "timestamp");
                    }
                }

                if (current.has("wifiScan")) {
                    current.set("wifiScan", new ObjectNode(JsonNodeFactory.instance));
                }
            }
        }

        return root;
    }

    private static void adjustGeoPoint(double longitudeAdjustment, ObjectNode jsonNode, String name) {
        String pointText = jsonNode.get(name).asText();
        
        // Parse the point text in format "geo:55.605487,13.007670"
        if (pointText.startsWith("geo:")) {
            String coordinates = pointText.substring(4); // Remove "geo:" prefix
            String[] parts = coordinates.split(",");
            
            if (parts.length == 2) {
                try {
                    double latitude = Double.parseDouble(parts[0].trim());
                    double longitude = Double.parseDouble(parts[1].trim());
                    
                    // Apply longitude adjustment
                    double adjustedLongitude = longitude + longitudeAdjustment;
                    
                    // Format back to the original geo: format
                    String adjustedPoint = String.format("geo:%.7f,%.7f", latitude, adjustedLongitude);
                    
                    // Add the adjusted value to the JSON node
                    jsonNode.put(name, adjustedPoint);
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse geo coordinates: " + pointText);
                }
            } else {
                System.err.println("Invalid geo format: " + pointText);
            }
        } else {
            System.err.println("Point text does not start with 'geo:': " + pointText);
        }
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

    private static void adjustTime(int timeAdjustmentInMinutes, ObjectNode current, String name) {
        String currentValue = current.get(name).asText();
        ZonedDateTime currentTime = ZonedDateTime.parse(currentValue);
        long newTime = currentTime.toEpochSecond() +  (timeAdjustmentInMinutes * 60L);
        current.put(name, ZonedDateTime.ofInstant(Instant.ofEpochSecond(newTime), currentTime.getZone()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
