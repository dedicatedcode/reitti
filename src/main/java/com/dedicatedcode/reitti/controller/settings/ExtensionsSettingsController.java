package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.extension.ExtensionRegistration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MapExtensionJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.extension.ExtensionCatalogService;
import com.dedicatedcode.reitti.service.extension.ExtensionException;
import com.dedicatedcode.reitti.service.extension.ExtensionManifestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/settings/extensions")
public class ExtensionsSettingsController {
    private final ExtensionCatalogService catalogService;
    private final ExtensionManifestService manifestService;
    private final MapExtensionJdbcService repository;
    private final I18nService i18n;
    private final boolean dataManagementEnabled;

    public ExtensionsSettingsController(ExtensionCatalogService catalogService, ExtensionManifestService manifestService,
                                        MapExtensionJdbcService repository, I18nService i18n,
                                        @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.catalogService = catalogService;
        this.manifestService = manifestService;
        this.repository = repository;
        this.i18n = i18n;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "extensions");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        addRegistrations(model);
        return "settings/extensions";
    }

    @GetMapping("/content")
    public String content(Model model) {
        addRegistrations(model);
        return "settings/extensions :: extensions-content";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model model) {
        model.addAttribute("registration", catalogService.required(id));
        return "settings/fragments/extensions :: edit-form";
    }

    @PostMapping
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String url,
                       @RequestParam(required = false) String bearerToken,
                       @RequestParam(defaultValue = "false") boolean clearCredentials,
                       Model model) {
        try {
            if (id == null) {
                catalogService.create(url, bearerToken);
                model.addAttribute("successMessage", i18n.translate("message.success.extension.created"));
            } else {
                catalogService.update(id, url, bearerToken, clearCredentials);
                model.addAttribute("successMessage", i18n.translate("message.success.extension.updated"));
            }
        } catch (ExtensionException e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.extension.save", e.getMessage()));
        }
        addRegistrations(model);
        return "settings/extensions :: extensions-content";
    }

    @PostMapping("/{id}/refresh")
    public String refresh(@PathVariable long id, Model model) {
        catalogService.refresh(id);
        addRegistrations(model);
        return "settings/extensions :: extensions-content";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable long id, Model model) {
        catalogService.toggle(id);
        addRegistrations(model);
        return "settings/extensions :: extensions-content";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, Model model) {
        catalogService.delete(id);
        addRegistrations(model);
        return "settings/extensions :: extensions-content";
    }

    private void addRegistrations(Model model) {
        List<RegistrationView> registrations = repository.findAll().stream()
                .map(registration -> new RegistrationView(registration,
                        manifestService.parseStored(registration.manifestJson()).unsupportedLayers()))
                .toList();
        model.addAttribute("registrations", registrations);
    }

    public record RegistrationView(ExtensionRegistration registration, List<String> unsupportedLayers) { }
}
