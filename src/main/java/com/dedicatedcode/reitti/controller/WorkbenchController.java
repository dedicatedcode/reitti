package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/workbench")
public class WorkbenchController {

    private final DeviceJdbcService deviceJdbcService;
    private final boolean dataManagementEnabled;

    public WorkbenchController(DeviceJdbcService deviceJdbcService, @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.deviceJdbcService = deviceJdbcService;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String workbench(@AuthenticationPrincipal User user, Model model, @RequestParam(required = false) LocalDate date) {
        model.addAttribute("devices", this.deviceJdbcService.getAll(user).stream().filter(Device::enabled).toList());
        model.addAttribute("date", date != null ? date.format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);

        return "workbench";
    }
}
