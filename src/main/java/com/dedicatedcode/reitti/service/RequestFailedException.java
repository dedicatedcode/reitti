package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.ReittiRemoteInfo;
import org.springframework.http.HttpStatusCode;

public class RequestFailedException extends Throwable {
    public RequestFailedException(String url, HttpStatusCode statusCode, Object message) {
        super("Request failed: " + url + " with status code " + statusCode + " and message " + message);
    }
}
