package com.example.bakir_khata.service;


import com.example.bakir_khata.dto.DashboardSummaryDTO;
import com.example.bakir_khata.model.User;

public interface DashboardService {
    DashboardSummaryDTO buildSummary(User user);
}
