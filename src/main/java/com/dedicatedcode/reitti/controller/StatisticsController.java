package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping
    public String statistics(Model model) {
        // Add any model attributes for statistics here in the future
        return "statistics";
    }

    @GetMapping("/years-navigation")
    public String yearsNavigation(Model model) {
        model.addAttribute("years", statisticsService.getAvailableYears());
        return "fragments/statistics-years-navigation";
    }
}
