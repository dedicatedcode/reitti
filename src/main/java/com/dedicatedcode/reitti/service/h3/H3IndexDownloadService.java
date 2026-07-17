package com.dedicatedcode.reitti.service.h3;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

@Service
public class H3IndexDownloadService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Downloads a remote package to disk. Supports resuming partially completed files.
     * 
     * @param downloadUrl The HTTP URL of your S3/R2-hosted ZIP package
     * @param targetFile The local path where the file should be saved
     */
    public void downloadDatabaseWithResume(String downloadUrl, Path targetFile) throws IOException, InterruptedException {
        // 1. Create parent folders if missing
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }

        // 2. Check size of the local partial download
        long existingFileSize = Files.exists(targetFile) ? Files.size(targetFile) : 0;

        System.out.println("Starting download. Current local file size: " + (existingFileSize / 1024 / 1024) + " MB");

        // 3. Prepare the HTTP GET request with the Range header
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofMinutes(10))
                .header("Range", "bytes=" + existingFileSize + "-") // Request byte offset
                .GET()
                .build();

        // Stream body directly rather than loading into JVM heap
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        int statusCode = response.statusCode();

        // Status 416 means Range Not Satisfiable. Typically means the file was already fully downloaded.
        if (statusCode == 416) {
            System.out.println("HTTP 416: File is already fully downloaded or local size exceeds remote size.");
            return;
        }

        boolean appendMode;
        if (statusCode == 206) {
            // Server accepted the range offset
            System.out.println("HTTP 206: Resuming download from " + existingFileSize + " bytes...");
            appendMode = true;
        } else if (statusCode == 200) {
            // Server ignored Range header (or file was brand new). Starting fresh.
            System.out.println("HTTP 200: Server starting download from scratch (resume unsupported or not needed).");
            existingFileSize = 0;
            appendMode = false;
        } else {
            throw new IOException("Unable to download database package. Server returned HTTP Status " + statusCode);
        }

        // Configure file write options based on append vs overwrite status
        StandardOpenOption[] writeOptions = appendMode 
            ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND}
            : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

        // 4. Stream directly to disk using OS-optimized FileChannels
        try (InputStream bodyStream = response.body();
             ReadableByteChannel readableChannel = Channels.newChannel(bodyStream);
             FileChannel fileChannel = FileChannel.open(targetFile, writeOptions)) {
             
            // OS-level bulk copy with maximum performance
            fileChannel.transferFrom(readableChannel, existingFileSize, Long.MAX_VALUE);
        }
        System.out.println("Download operation complete.");
    }
}