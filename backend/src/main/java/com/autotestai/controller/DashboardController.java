package com.autotestai.controller;

import com.autotestai.dto.dashboard.DashboardSummaryResponse;
import com.autotestai.security.CurrentUserId;
import com.autotestai.service.DashboardService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/summary")
    public DashboardSummaryResponse get(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.get(CurrentUserId.from(jwt));
    }
}
