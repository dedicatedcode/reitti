package com.dedicatedcode.reitti.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebViewControllerTest {

    @Test
    void login_WithLocalLoginEnabled_ShouldShowLoginPage() {
        // Given
        WebViewController controller = new WebViewController(false, true, false);
        Model model = new ExtendedModelMap();

        // When
        String result = controller.login(model);

        // Then
        assertEquals("login", result);
        assertEquals(true, model.getAttribute("localLoginEnabled"));
        assertEquals(true, model.getAttribute("oidcEnabled"));
    }

    @Test
    void login_WithLocalLoginDisabledAndOidcEnabled_ShouldRedirectToOAuth() {
        // Given
        WebViewController controller = new WebViewController(true, true, true);
        Model model = new ExtendedModelMap();

        // When
        String result = controller.login( model);

        // Then
        assertEquals("redirect:/oauth2/authorization/oauth", result);
    }

    @Test
    void login_WithOnlyLocalLoginEnabled_ShouldShowLoginPage() {
        // Given
        WebViewController controller = new WebViewController(false, false, false);
        Model model = new ExtendedModelMap();

        // When
        String result = controller.login(model);

        // Then
        assertEquals("login", result);
        assertEquals(true, model.getAttribute("localLoginEnabled"));
        assertEquals(false, model.getAttribute("oidcEnabled"));
    }

    @Test
    void login_WithBothLoginMethodsDisabled_ShouldShowLoginPageWithoutOptions() {
        // Given
        WebViewController controller = new WebViewController(true, false, false);
        Model model = new ExtendedModelMap();

        // When
        String result = controller.login(model);

        // Then
        assertEquals("login", result);
        assertEquals(true, model.getAttribute("localLoginEnabled"));
        assertEquals(false, model.getAttribute("oidcEnabled"));
    }

}
