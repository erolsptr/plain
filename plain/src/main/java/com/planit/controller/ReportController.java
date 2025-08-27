package com.planit.controller;

import com.planit.model.dto.UserReportDTO;
import com.planit.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/user-summary")
    public ResponseEntity<UserReportDTO> getUserSummaryReport(Authentication authentication) {
        String userEmail = authentication.getName();
        UserReportDTO report = reportService.generateUserReport(userEmail);
        return ResponseEntity.ok(report);
    }
}