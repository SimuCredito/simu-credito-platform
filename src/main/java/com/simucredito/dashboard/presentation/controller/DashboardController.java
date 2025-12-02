package com.simucredito.dashboard.presentation.controller;

import com.simucredito.dashboard.application.dto.DashboardMetricsDTO;
import com.simucredito.dashboard.application.dto.RecentActivityDTO;
import com.simucredito.dashboard.application.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard management APIs")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/metrics")
    @Operation(summary = "Get dashboard metrics", description = "Retrieve comprehensive dashboard metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = DashboardMetricsDTO.class)))
    })
    public ResponseEntity<DashboardMetricsDTO> getMetrics() {
        DashboardMetricsDTO metrics = dashboardService.getDashboardMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/recent-activity")
    @Operation(summary = "Get recent activity", description = "Retrieve recent system activities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Recent activities retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecentActivityDTO.class)))
    })
    public ResponseEntity<List<RecentActivityDTO>> getRecentActivity(
            @RequestParam(defaultValue = "20") int limit) {
        List<RecentActivityDTO> activities = dashboardService.getRecentActivity(limit);
        return ResponseEntity.ok(activities);
    }
    @GetMapping("/activity")
    @Operation(summary = "Get simulation activity", description = "Retrieve simulation counts grouped by day for charts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activity data retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RecentActivityDTO.class)))
    })
    public ResponseEntity<List<com.simucredito.dashboard.application.dto.SimulationActivityDTO>> getSimulationActivity(
            @RequestParam(defaultValue = "week") String period) {
        return ResponseEntity.ok(dashboardService.getSimulationActivity(period));
    }
}