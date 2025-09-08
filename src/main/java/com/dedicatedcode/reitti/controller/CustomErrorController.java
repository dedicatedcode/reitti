package com.dedicatedcode.reitti.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
public class CustomErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(CustomErrorController.class);
    private static final List<String> IGNORED_PATHS = List.of("/favicon.ico", "/js/", "/img/");

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        if (IGNORED_PATHS.stream().anyMatch(requestUri::startsWith)) {
            return null;
        }
        if (status != null) {
            Integer statusCode = Integer.valueOf(status.toString());
            model.addAttribute("status", statusCode);

            // Add specific error messages based on status code
            HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
            model.addAttribute("error", httpStatus.getReasonPhrase());

            // Log the error
            log.error("Error {} occurred for request URI: {}, message: {}",
                    statusCode, requestUri, errorMessage, (Throwable) exception);

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