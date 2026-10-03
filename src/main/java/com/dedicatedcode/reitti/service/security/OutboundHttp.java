package com.dedicatedcode.reitti.service.security;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Helpers for outgoing HTTP requests to (potentially) user supplied URLs: redirects are never followed implicitly and
 * response sizes are capped.
 */
public final class OutboundHttp {

    public static final long MAX_JSON_BYTES = 2L * 1024 * 1024;
    public static final long MAX_STYLE_JSON_BYTES = 5L * 1024 * 1024;
    public static final long MAX_TILE_BYTES = 20L * 1024 * 1024;
    public static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    public static final int MAX_REDIRECTS = 3;

    public record Response(int statusCode, HttpHeaders headers, byte[] body) {
    }

    private OutboundHttp() {
    }

    /**
     * A {@link ClientHttpRequestFactory} for {@code RestTemplate} that never follows redirects (the JDK default would
     * follow them for GET requests without any validation of the target) and uses finite timeouts.
     */
    public static ClientHttpRequestFactory noRedirectRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }

    /**
     * A {@link HttpClient} that does not follow redirects. Use {@link #get} to follow them with validation.
     */
    public static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Performs a GET request and reads at most {@code maxBytes} of the body.
     * <p>
     * Redirects are followed up to {@code maxRedirects} times. Every redirect target is checked with
     * {@code redirectValidator}; pass {@code null} only for endpoints configured by the administrator.
     * The given headers are sent on every hop, so do not follow redirects when sending credentials.
     */
    public static Response get(HttpClient client,
                               URI uri,
                               Map<String, String> headers,
                               Duration timeout,
                               long maxBytes,
                               int maxRedirects,
                               OutboundUrlValidator redirectValidator) throws IOException, InterruptedException {
        URI current = uri;
        for (int hop = 0; ; hop++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(current).timeout(timeout).GET();
            headers.forEach(builder::header);
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            Optional<String> location = response.headers().firstValue("Location");
            if (isRedirect(response.statusCode()) && location.isPresent() && hop < maxRedirects) {
                response.body().close();
                URI next = current.resolve(location.get().trim());
                if (redirectValidator != null) {
                    redirectValidator.validate(next);
                } else if (!"http".equalsIgnoreCase(next.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) {
                    throw new IOException("Unsupported redirect target");
                }
                current = next;
                continue;
            }
            try (InputStream body = response.body()) {
                return new Response(response.statusCode(), response.headers(), readAtMost(body, maxBytes));
            }
        }
    }

    /**
     * Reads the stream completely, failing if it holds more than {@code maxBytes}.
     */
    public static byte[] readAtMost(InputStream in, long maxBytes) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Response exceeds the allowed size of " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }
}
