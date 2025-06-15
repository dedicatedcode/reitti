package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping
    public String statistics(Model model) {
        return "statistics";
    }

    @GetMapping("/years-navigation")
    public String yearsNavigation(Model model) {
        model.addAttribute("years", statisticsService.getAvailableYears());
        return "fragments/statistics :: years-navigation";
    }

    @GetMapping("/overall")
    public String overallStatistics(Model model) {
        // TODO: Add overall statistics data to model
        model.addAttribute("statisticsType", "overall");
        model.addAttribute("title", "Overall Statistics");
        return "fragments/statistics :: statistics-content";
    }

    @GetMapping("/{year}")
    public String yearStatistics(@PathVariable Integer year, Model model) {
        // TODO: Add year-specific statistics data to model
        model.addAttribute("statisticsType", "year");
        model.addAttribute("year", year);
        model.addAttribute("title", "Statistics for " + year);
        return "fragments/statistics :: statistics-content";
    }
}
