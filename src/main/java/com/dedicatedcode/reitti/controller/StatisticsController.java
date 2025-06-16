package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.StatisticsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping
    public String statistics(Model model) {
        return "statistics";
    }

    @GetMapping("/years-navigation")
    public String yearsNavigation(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("years", statisticsService.getAvailableYears(user));
        return "fragments/statistics :: years-navigation";
    }

    @GetMapping("/overall")
    public String overallStatistics(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("statisticsType", "overall");
        model.addAttribute("title", "Overall Statistics");
        model.addAttribute("topVisits", statisticsService.getOverallTopVisits(user));
        
        try {
            String transportStatsJson = objectMapper.writeValueAsString(statisticsService.getOverallTransportStatistics(user));
            model.addAttribute("transportStats", transportStatsJson);
        } catch (JsonProcessingException e) {
            model.addAttribute("transportStats", "[]");
        }
        
        return "fragments/statistics :: statistics-content";
    }

    @GetMapping("/{year}")
    public String yearStatistics(@PathVariable Integer year, @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("statisticsType", "year");
        model.addAttribute("year", year);
        model.addAttribute("title", "Statistics for " + year);
        model.addAttribute("topVisits", statisticsService.getYearTopVisits(user, year));
        
        try {
            String transportStatsJson = objectMapper.writeValueAsString(statisticsService.getYearTransportStatistics(user, year));
            model.addAttribute("transportStats", transportStatsJson);
            
            String breakdownTransportJson = objectMapper.writeValueAsString(statisticsService.getMonthlyTransportBreakdown(user, year));
            model.addAttribute("breakdownTransportData", breakdownTransportJson);
        } catch (JsonProcessingException e) {
            model.addAttribute("transportStats", "[]");
            model.addAttribute("breakdownTransportData", "[]");
        }
        
        // Add months for the year
        java.util.List<Integer> months = java.util.Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        model.addAttribute("months", months);
        
        return "fragments/statistics :: statistics-content";
    }
    
    @GetMapping("/{year}/{month}")
    public String monthStatistics(@PathVariable Integer year, @PathVariable Integer month, 
                                 @AuthenticationPrincipal User user, Model model) {
        model.addAttribute("statisticsType", "month");
        model.addAttribute("year", year);
        model.addAttribute("month", month);
        model.addAttribute("title", java.time.Month.of(month).name() + " " + year);
        model.addAttribute("topVisits", statisticsService.getMonthTopVisits(user, year, month));
        
        try {
            List<StatisticsService.TransportStatistic> monthTransportStatistics = statisticsService.getMonthTransportStatistics(user, year, month);
            List<StatisticsService.DailyTransportData> dailyTransportBreakdown = statisticsService.getDailyTransportBreakdown(user, year, month);

            model.addAttribute("dataAvailable", !monthTransportStatistics.isEmpty() || dailyTransportBreakdown.stream().noneMatch(dt -> dt.getTransportStats().isEmpty()));
            model.addAttribute("transportStats", objectMapper.writeValueAsString(monthTransportStatistics));
            model.addAttribute("breakdownTransportData", objectMapper.writeValueAsString(dailyTransportBreakdown));
        } catch (JsonProcessingException e) {
            model.addAttribute("transportStats", "[]");
            model.addAttribute("breakdownTransportData", "[]");
        }
        
        return "fragments/statistics :: month-content";
    }
}
