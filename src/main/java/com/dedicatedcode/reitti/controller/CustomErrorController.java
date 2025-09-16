package com.dedicatedcode.reitti.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CustomErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(CustomErrorController.class);
    private static final List<String> IGNORED_PATHS = List.of("/favicon.ico", "/js/", "/img/");
    private static final List<Integer> SILENT_CODES = List.of(400, 401, 403, 404);

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        if (IGNORED_PATHS.stream().anyMatch(requestUri::startsWith)) {
            return null;
        }

        // Check if this is an API request
        if (requestUri.startsWith("/api")) {
            return handleApiError(status, errorMessage, exception, requestUri);
        }

        // Handle web requests with HTML error page
        return handleWebError(status, errorMessage, exception, requestUri, request, model);
    }

    private ResponseEntity<Map<String, Object>> handleApiError(Object status, Object errorMessage, Object exception, String requestUri) {
        Integer statusCode = status != null ? Integer.valueOf(status.toString()) : 500;
        String message = errorMessage != null ? errorMessage.toString() : "An error occurred";

        // Log the error
        log.error("API Error {} occurred for request URI: {}, message: {}",
                statusCode, requestUri, errorMessage, (Throwable) exception);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", message);
        errorResponse.put("status", statusCode);

        // Add specific error messages for common status codes
        switch (statusCode) {
            case 404:
                errorResponse.put("error", "The requested resource could not be found.");
                break;
            case 403:
                errorResponse.put("error", "You don't have permission to access this resource.");
                break;
            case 401:
                errorResponse.put("error", "Authentication required.");
                break;
            case 405:
                errorResponse.put("error", "Method not allowed.");
                break;
            case 415:
                errorResponse.put("error", "Unsupported media type.");
                break;
            case 500:
                errorResponse.put("error", "An internal server error occurred.");
                break;
        }

        return ResponseEntity.status(statusCode).body(errorResponse);
    }

    private String handleWebError(Object status, Object errorMessage, Object exception, String requestUri,HttpServletRequest request, Model model) {
        if (status != null) {
            Integer statusCode = Integer.valueOf(status.toString());
            model.addAttribute("status", statusCode);

            // Add specific error messages based on status code
            HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
            model.addAttribute("error", httpStatus.getReasonPhrase());


            if (!SILENT_CODES.contains(statusCode)) {
                log.error("Error {} occurred for request URI: {}, message: {}",
                        statusCode, requestUri, errorMessage, (Throwable) exception);
            }
            // Add custom messages for common error codes
            switch (statusCode) {
                case 404:
                    model.addAttribute("message", "The page you are looking for could not be found.");
                    break;
                case 403:
                    model.addAttribute("message", "You don't have permission to access this resource.");
                    break;
                case 500:
                    model.addAttribute("message", "An internal server error occurred. Please try again later.");
                    break;
                default:
                    model.addAttribute("message", "An unexpected error occurred.");
                    break;
            }
        } else {
            model.addAttribute("status", 500);
            model.addAttribute("error", "Internal Server Error");
            model.addAttribute("message", "An unexpected error occurred. Please try again later.");

            log.error("Unknown error occurred for request URI: {}", requestUri, (Throwable) exception);
        }

        // Add stack trace for development environments (only if localhost)
        if (exception != null && request.getServerName().contains("localhost")) {
            model.addAttribute("trace", getStackTrace((Throwable) exception));
        }

        return "error";
    }

    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        if (throwable.getCause() != null) {
            sb.append("Caused by: ").append(getStackTrace(throwable.getCause()));
        }

        return sb.toString();
    }
}
