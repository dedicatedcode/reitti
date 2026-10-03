package com.dedicatedcode.reitti.service.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redirect and size handling of outgoing requests. The test server runs on loopback, which the validator always
 * rejects, so following a redirect to it with validation must fail.
 */
class OutboundHttpTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger secretHits = new AtomicInteger();
    private final OutboundUrlValidator validator = new OutboundUrlValidator(true, "", "", 6379, "");
    private final HttpClient client = OutboundHttp.newHttpClient();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/redirect-internal", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirect-local", exchange -> {
            exchange.getResponseHeaders().add("Location", "/secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/secret", exchange -> {
            secretHits.incrementAndGet();
            byte[] body = "secret".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/large", exchange -> {
            byte[] body = new byte[1024];
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void redirectToMetadataServiceIsRejected() {
        assertThatThrownBy(() -> OutboundHttp.get(client, URI.create(baseUrl + "/redirect-internal"), Map.of(),
                Duration.ofSeconds(5), 1024, OutboundHttp.MAX_REDIRECTS, validator))
                .isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void everyRedirectHopIsValidated() {
        assertThatThrownBy(() -> OutboundHttp.get(client, URI.create(baseUrl + "/redirect-local"), Map.of(),
                Duration.ofSeconds(5), 1024, OutboundHttp.MAX_REDIRECTS, validator))
                .isInstanceOf(UnsafeUrlException.class);
        assertThat(secretHits).hasValue(0);
    }

    @Test
    void redirectsAreNotFollowedWhenNotAllowed() throws Exception {
        OutboundHttp.Response response = OutboundHttp.get(client, URI.create(baseUrl + "/redirect-local"), Map.of(),
                Duration.ofSeconds(5), 1024, 0, validator);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(secretHits).hasValue(0);
    }

    @Test
    void redirectsOfAdminConfiguredEndpointsAreFollowed() throws Exception {
        OutboundHttp.Response response = OutboundHttp.get(client, URI.create(baseUrl + "/redirect-local"), Map.of(),
                Duration.ofSeconds(5), 1024, OutboundHttp.MAX_REDIRECTS, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("secret");
    }

    @Test
    void responseSizeIsCapped() {
        assertThatThrownBy(() -> OutboundHttp.get(client, URI.create(baseUrl + "/large"), Map.of(),
                Duration.ofSeconds(5), 100, 0, validator))
                .isInstanceOf(IOException.class);
    }

    @Test
    void restTemplateDoesNotFollowRedirects() {
        RestTemplate restTemplate = new RestTemplate(OutboundHttp.noRedirectRequestFactory());

        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/redirect-local", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(secretHits).hasValue(0);
    }
}
