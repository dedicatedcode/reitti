package com.dedicatedcode.reitti.service;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service
public class StatisticsService {
    
    public List<Integer> getAvailableYears() {
        // TODO: Replace with actual database query to get years with data
        List<Integer> years = new ArrayList<>();
        years.add(2024);
        years.add(2023);
        years.add(2022);
        return years;
    }
    
}
