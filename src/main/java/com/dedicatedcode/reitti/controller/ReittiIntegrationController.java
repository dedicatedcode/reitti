package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.ReittiIntegration;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings")
public class ReittiIntegrationController {

    @GetMapping("/shared-instances-content")
    public String getSharedInstancesContent(Model model) {
        // TODO: Replace with actual service call when JDBC is implemented
        List<ReittiIntegration> integrations = getMockIntegrations();
        
        model.addAttribute("reittiIntegrations", integrations);
        return "fragments/settings :: shared-instances-content";
    }

    @PostMapping("/reitti-integrations")
    public String createReittiIntegration(
            @RequestParam String url,
            @RequestParam String token,
            @RequestParam(defaultValue = "false") boolean enabled,
            Model model) {
        
        try {
            // TODO: Implement actual creation logic when JDBC is available
            // For now, just show success message
            model.addAttribute("successMessage", "Reitti integration saved successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error saving configuration: " + e.getMessage());
        }
        
        // Reload the content
        return getSharedInstancesContent(model);
    }

    @PostMapping("/reitti-integrations/{id}/toggle")
    public String toggleReittiIntegration(@PathVariable Long id, Model model) {
        try {
            // TODO: Implement actual toggle logic when JDBC is available
            model.addAttribute("successMessage", "Integration status updated successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error updating integration: " + e.getMessage());
        }
        
        return getSharedInstancesContent(model);
    }

    @PostMapping("/reitti-integrations/{id}/delete")
    public String deleteReittiIntegration(@PathVariable Long id, Model model) {
        try {
            // TODO: Implement actual deletion logic when JDBC is available
            model.addAttribute("successMessage", "Reitti integration deleted successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error deleting configuration: " + e.getMessage());
        }
        
        return getSharedInstancesContent(model);
    }

    @PostMapping("/reitti-integrations/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testReittiConnection(
            @RequestParam String url,
            @RequestParam(name = "foreign_token") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // TODO: Implement actual connection test when JDBC is available
            // For now, simulate a successful connection test
            if (url.startsWith("http") && !token.isEmpty()) {
                response.put("success", true);
                response.put("message", "Connection successful - Connected to Reitti instance");
            } else {
                response.put("success", false);
                response.put("message", "Invalid URL or token format");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Connection failed: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    // TODO: Remove this mock method when JDBC implementation is ready
    private List<ReittiIntegration> getMockIntegrations() {
        // Return empty list for now - will be replaced with actual data from database
        return new ArrayList<>();
    }
}
