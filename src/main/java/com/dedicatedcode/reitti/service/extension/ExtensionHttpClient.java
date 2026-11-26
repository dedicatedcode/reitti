package com.dedicatedcode.reitti.service.extension;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class ExtensionHttpClient {
    public enum ResourceType { MANIFEST, DATA, ASSET }

    public record Resource(byte[] body, String contentType) { }

    private final CloseableHttpClient httpClient;
    private final ExecutorService executor;
    private final Duration requestTimeout;
    private final int manifestMaxBytes;
    private final int dataMaxBytes;
    private final int assetMaxBytes;
    private final Set<String> allowedHosts;

    public ExtensionHttpClient(
            @Value("${reitti.extensions.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${reitti.extensions.request-timeout-ms:10000}") int requestTimeoutMs,
            @Value("${reitti.extensions.manifest-max-bytes:1048576}") int manifestMaxBytes,
            @Value("${reitti.extensions.data-max-bytes:5242880}") int dataMaxBytes,
            @Value("${reitti.extensions.asset-max-bytes:10485760}") int assetMaxBytes,
            @Value("${reitti.extensions.allowed-hosts:}") String allowedHosts) {
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.manifestMaxBytes = manifestMaxBytes;
        this.dataMaxBytes = dataMaxBytes;
        this.assetMaxBytes = assetMaxBytes;
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        DnsResolver dnsResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return resolveAllowed(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(requestTimeoutMs))
                .setRedirectsEnabled(false)
                .build();
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(dnsResolver)
                .setMaxConnTotal(16)
                .setMaxConnPerRoute(4)
                .build();
        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
        this.executor = new ThreadPoolExecutor(8, 8, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), runnable -> {
            Thread thread = new Thread(runnable, "reitti-extension-http");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public URI validateUrl(String value) {
        if (value == null || value.isBlank() || value.length() > 2048 || value.chars().anyMatch(Character::isISOControl)) {
            throw new ExtensionException("The extension URL is invalid");
        }
        final URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw new ExtensionException("The extension URL is invalid");
        }
        validateUrl(uri);
        return uri;
    }

    public void validateUrl(URI uri) {
        if (uri.getHost() == null || !Set.of("http", "https").contains(uri.getScheme())
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new ExtensionException("Extension URLs must be HTTP(S) URLs without credentials or fragments");
        }
        try {
            resolveAllowed(uri.getHost());
        } catch (UnknownHostException e) {
            throw new ExtensionException("The extension host cannot be resolved", e);
        }
    }

    private InetAddress[] resolveAllowed(String host) throws UnknownHostException {
        String hostname = host.toLowerCase(Locale.ROOT);
        InetAddress[] addresses = InetAddress.getAllByName(hostname);
        if (allowedHosts.contains(hostname)) {
            return addresses;
        }
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || isUniqueLocal(address)
                    || isOtherNonPublicAddress(address)) {
                throw new ExtensionException("The extension host resolves to a private or local address: " + hostname);
            }
        }
        return addresses;
    }

    public Resource fetch(URI uri, String bearerToken, ResourceType type, String format) {
        int maxBytes = switch (type) {
            case MANIFEST -> manifestMaxBytes;
            case DATA -> dataMaxBytes;
            case ASSET -> assetMaxBytes;
        };
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept", acceptHeader(type, format));
        request.setHeader("Cache-Control", "no-cache");
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.setHeader("Authorization", "Bearer " + bearerToken);
        }
        try {
            Future<Resource> future = executor.submit(() -> {
                validateUrl(uri);
                return httpClient.execute(request, response -> {
                    if (response.getCode() < 200 || response.getCode() >= 300) {
                        throw new UpstreamStatusException(response.getCode());
                    }
                    String contentType = response.getFirstHeader("Content-Type") != null
                            ? response.getFirstHeader("Content-Type").getValue() : "";
                    if (!isAllowedContentType(contentType, type, format)) {
                        throw new UnsupportedExtensionMediaTypeException();
                    }
                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        throw new ExtensionException("The extension upstream returned an empty response");
                    }
                    try (InputStream body = entity.getContent()) {
                        byte[] bytes = body.readNBytes(maxBytes + 1);
                        if (bytes.length > maxBytes) {
                            throw new ExtensionPayloadTooLargeException();
                        }
                        return new Resource(bytes, contentType);
                    }
                });
            });
            return future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            request.abort();
            throw new UpstreamTimeoutException(e);
        } catch (RejectedExecutionException e) {
            request.abort();
            throw new ExtensionException("Too many extension requests are already running", e);
        } catch (InterruptedException e) {
            request.abort();
            Thread.currentThread().interrupt();
            throw new ExtensionException("The extension request was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ExtensionException extensionException) throw extensionException;
            if (cause instanceof SocketTimeoutException socketTimeoutException) {
                throw new UpstreamTimeoutException(socketTimeoutException);
            }
            if (cause instanceof IOException ioException) {
                throw new ExtensionException("The extension upstream could not be reached", ioException);
            }
            throw new ExtensionException("The extension upstream request failed", cause);
        }
    }

    private String acceptHeader(ResourceType type, String format) {
        return switch (type) {
            case MANIFEST -> "application/json, application/*+json";
            case ASSET -> "image/*, model/gltf-binary, model/gltf+json, application/octet-stream";
            case DATA -> "image".equals(format) ? "image/*" : "gpx".equals(format)
                    ? "application/gpx+xml, application/xml, text/xml" : "application/json, application/geo+json, application/*+json";
        };
    }

    private boolean isAllowedContentType(String contentType, ResourceType type, String format) {
        String mime = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mime.isEmpty() || mime.contains("\r") || mime.contains("\n")) {
            return false;
        }
        return switch (type) {
            case MANIFEST -> mime.equals("application/json") || (mime.startsWith("application/") && mime.endsWith("+json"));
            case ASSET -> mime.startsWith("image/") || Set.of("model/gltf-binary", "model/gltf+json",
                    "application/octet-stream").contains(mime);
            case DATA -> "image".equals(format) ? mime.startsWith("image/") : "gpx".equals(format)
                    ? Set.of("application/gpx+xml", "application/xml", "text/xml").contains(mime)
                    : mime.equals("application/json") || mime.equals("application/geo+json")
                    || (mime.startsWith("application/") && mime.endsWith("+json"));
        };
    }

    private boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private boolean isOtherNonPublicAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            return bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == (byte) 0xb8;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 0
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 198 && (second == 18 || second == 19))
                || first >= 240;
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
        try {
            httpClient.close();
        } catch (IOException _) {
            // Shutdown is best effort.
        }
    }

    public static class UpstreamStatusException extends ExtensionException {
        private final int status;

        public UpstreamStatusException(int status) {
            super("The extension upstream returned HTTP " + status);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    public static class UpstreamTimeoutException extends ExtensionException {
        public UpstreamTimeoutException(Throwable cause) {
            super("The extension upstream timed out", cause);
        }
    }

    public static class UnsupportedExtensionMediaTypeException extends ExtensionException {
        public UnsupportedExtensionMediaTypeException() {
            super("The extension upstream returned an unsupported content type");
        }
    }

    public static class ExtensionPayloadTooLargeException extends ExtensionException {
        public ExtensionPayloadTooLargeException() {
            super("The extension upstream response is too large");
        }
    }
}
