import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Random;

public class GoogleTimelineRandomizer {

    private static final Random random = new Random();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: GoogleTimelineRandomizer <file-path> <output-dir>");
            System.exit(1);
        }


        int timeAdjustments = random.nextInt(3600 * 7);
        if (random.nextBoolean()) timeAdjustments *= -1;

        double longitudeAdjustment = random.nextDouble(-10, 10);

        String filePath = args[0];
        String outputDir = args[1];
        try {
            load(filePath, outputDir, timeAdjustments, longitudeAdjustment);
        } catch (IOException e) {
            System.err.println("Error loading file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void load(String filePath, String outputDir, int timeAdjustmentInMinutes, double longitudeAdjustment) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new FileReader(filePath));

        JsonNode semanticSegments = root.path("semanticSegments");
        JsonNode rawSignals = root.path("rawSignals");

        for (JsonNode segment : semanticSegments) {
            ObjectNode current = (ObjectNode) segment;
            if (current.has("startTime")) {
                adjustTime(timeAdjustmentInMinutes, current.get("startTime"), current, "startTime");
            }
            if (current.has("endTime")) {
                adjustTime(timeAdjustmentInMinutes, current.get("endTime"), current, "startTime");
            }
            if (current.has("timelinePath")) {
                ArrayNode timelinePath = (ArrayNode) current.get("timelinePath");
                for (JsonNode jsonNode : timelinePath) {
                    if (jsonNode.isObject()) {
                        if (jsonNode.has("time")) {
                            adjustTime(timeAdjustmentInMinutes, jsonNode.get("time"), (ObjectNode) jsonNode, "startTime");
                        }
                        if (jsonNode.has("point")) {
                            adjustPoint(longitudeAdjustment, jsonNode.get("point"), (ObjectNode) jsonNode);
                        }
                    }

                }
            }
        }
        for (JsonNode jsonNode : root) {
            System.out.println();
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

        // Write filtered JSON to output file
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), null);

        System.out.println("Filtered data written to: " + outputPath);
    }

    private static void adjustPoint(double longitudeAdjustment, JsonNode point, ObjectNode jsonNode, String name) {
        //pasres the point text which is in this format point -> {TextNode@1939}"-27.4127738°, 153.0617186°", add the longitude adustment and add the adjusted value under name to the jsonNode AI!
    }

    private static void adjustTime(int timeAdjustmentInMinutes, JsonNode jsonNode, ObjectNode current, String name) {
        String currentValue = jsonNode.asText();
        ZonedDateTime currentTime = ZonedDateTime.parse(currentValue);
        long newTime = currentTime.toEpochSecond() +  (timeAdjustmentInMinutes * 60L);
        current.put(name, ZonedDateTime.ofInstant(Instant.ofEpochSecond(newTime), currentTime.getZone()).toString());
    }

    private static JsonNode filter(JsonNode root, String date) {
        System.out.println("Filtering locations for date " + date);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode rootNode = objectMapper.createObjectNode();
        ArrayNode locations = objectMapper.createArrayNode();
        rootNode.set("locations", locations);

        JsonNode existingLocations = root.get("locations");
        for (JsonNode existingLocation : existingLocations) {
            if (existingLocation.path("timestamp").asText().startsWith(date)) {
                locations.add(existingLocation);
            }
        }
        return rootNode;
    }

}
