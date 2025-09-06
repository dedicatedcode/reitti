package com.dedicatedcode.reitti.controller;

import java.util.Arrays;

public class IllegalConfigurationException extends RuntimeException {
    public IllegalConfigurationException(String message, String ... suggestions) {
        super("\n\nIllegal Configuration detected!\n\n" + message + "\nPossible solutions:\n- " + String.join("\n- ", Arrays.asList(suggestions)));
        setStackTrace(new StackTraceElement[0]);
    }
}
