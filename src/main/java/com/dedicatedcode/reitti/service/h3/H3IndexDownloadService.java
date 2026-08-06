package com.dedicatedcode.reitti.service.h3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "reitti.h3", name = "enabled", havingValue = "true")
public class H3IndexDownloadService {

    private static final Logger log = LoggerFactory.getLogger(H3IndexDownloadService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public void downloadDatabaseWithResume(String downloadUrl, Path targetFile) throws IOException, InterruptedException {
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }

        long existingFileSize = Files.exists(targetFile) ? Files.size(targetFile) : 0;

        log.info("Starting download. Current local file size: {} MB", existingFileSize / 1024 / 1024);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofMinutes(10))
                .header("Range", "bytes=" + existingFileSize + "-")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        int statusCode = response.statusCode();

        if (statusCode == 416) {
            log.info("HTTP 416: File is already fully downloaded or local size exceeds remote size.");
            return;
        }

        boolean appendMode;
        if (statusCode == 206) {
            log.info("HTTP 206: Resuming download from {} bytes...", existingFileSize);
            appendMode = true;
        } else if (statusCode == 200) {
            log.info("HTTP 200: Server starting download from scratch (resume unsupported or not needed).");
            existingFileSize = 0;
            appendMode = false;
        } else {
            throw new IOException("Unable to download database package. Server returned HTTP Status " + statusCode);
        }

        StandardOpenOption[] writeOptions = appendMode 
            ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND}
            : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

        try (InputStream bodyStream = response.body();
             ReadableByteChannel readableChannel = Channels.newChannel(bodyStream);
             FileChannel fileChannel = FileChannel.open(targetFile, writeOptions)) {
             
            fileChannel.transferFrom(readableChannel, existingFileSize, Long.MAX_VALUE);
        }
        log.info("Download operation complete.");
    }
}
