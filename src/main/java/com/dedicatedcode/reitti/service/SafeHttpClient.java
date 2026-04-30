package com.dedicatedcode.reitti.service;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

@Service
public class SafeHttpClient {
    private static final int MAX_REDIRECTS = 5;

    private final RemoteTileUrlValidator remoteTileUrlValidator;

    public SafeHttpClient(RemoteTileUrlValidator remoteTileUrlValidator) {
        this.remoteTileUrlValidator = remoteTileUrlValidator;
    }

    public <T> HttpResponse<T> sendFollowingPublicRedirects(
            HttpClient httpClient,
            HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler,
            String fieldName) throws IOException, InterruptedException {
        HttpRequest currentRequest = request;
        URI currentUri = request.uri();

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpResponse<T> response = httpClient.send(currentRequest, bodyHandler);
            if (!isRedirect(response.statusCode())) {
                return response;
            }

            if (redirectCount == MAX_REDIRECTS) {
                throw new IOException("Too many redirects for " + fieldName + ".");
            }

            URI redirectUri = resolveRedirectUri(currentUri, response);
            remoteTileUrlValidator.requirePublicHttpUrl(redirectUri.toString(), fieldName + " redirect URL");
            currentUri = redirectUri;
            currentRequest = redirectRequest(currentRequest, response.statusCode(), redirectUri);
        }

        throw new IOException("Too many redirects for " + fieldName + ".");
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private <T> URI resolveRedirectUri(URI currentUri, HttpResponse<T> response) throws IOException {
        Optional<String> location = response.headers().firstValue(HttpHeaders.LOCATION);
        if (location.isEmpty() || location.get().isBlank()) {
            throw new IOException("Redirect response is missing a Location header.");
        }
        return currentUri.resolve(location.get().trim());
    }

    private HttpRequest redirectRequest(HttpRequest previousRequest, int statusCode, URI redirectUri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(redirectUri)
                .GET();

        previousRequest.timeout().ifPresent(builder::timeout);
        previousRequest.headers().map().forEach((name, values) -> {
            if (!HttpHeaders.HOST.equalsIgnoreCase(name)) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        if (statusCode == 303) {
            builder.GET();
        }
        return builder.build();
    }
}
