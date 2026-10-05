package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.controller.error.PageNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class ErrorHandlingControllerAdvice {


    @ExceptionHandler(PageNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handlePageNotFound(Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("error", "Not Found");
        model.addAttribute("message", "The page you are looking for could not be found.");
        return "error";
    }

    @ExceptionHandler({IllegalAccessException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleIllegalAccessException(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("error", "Forbidden");
        model.addAttribute("message", "You are not allowed to access this resource.");
        return "error";
    }

    @ExceptionHandler({IllegalStateException.class})
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    public String handleIllegalStateException(Model model) {
        model.addAttribute("status", 417);
        model.addAttribute("error", "Precondition failed");
        model.addAttribute("message", "Received data is not valid.");
        return "error";
    }
}
