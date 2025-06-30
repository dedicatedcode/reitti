import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GoogleRecordsFilter {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: GoogleRecordsFilter <file-path> <date> <output-dir>");
            System.exit(1);
        }
        
        String filePath = args[0];
        String date = args[1];
        String outputDir = args[2];
        try {
            load(filePath, outputDir, date);
        } catch (IOException e) {
            System.err.println("Error loading file: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void load(String filePath, String outputDir, String date) throws IOException {
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + filePath);
        }
        
        if (!Files.isRegularFile(path)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new FileReader(filePath));
        JsonNode filtered = filter(root, date);

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
        
        String outputFileName = nameWithoutExtension + "_" + date + extension;
        Path outputPath = Paths.get(outputDir, outputFileName);
        
        // Ensure output directory exists
        Files.createDirectories(Paths.get(outputDir));
        
        // Write filtered JSON to output file
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), filtered);
        
        System.out.println("Filtered data written to: " + outputPath);
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
