package com.familion.vehicles.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.vehicles.application.MaintenanceLogService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles/{id}/maintenance-logs")
public class MaintenanceLogController {

    private final MaintenanceLogService maintenanceLogService;
    private final CurrentUserProvider currentUserProvider;

    public MaintenanceLogController(
            MaintenanceLogService maintenanceLogService, CurrentUserProvider currentUserProvider) {
        this.maintenanceLogService = maintenanceLogService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public Page<MaintenanceLogResponse> listMaintenanceLogs(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return maintenanceLogService.listMaintenanceLogs(currentUserProvider.get(), id, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceLogResponse createMaintenanceLog(
            @PathVariable UUID id,
            @Valid @RequestBody MaintenanceLogCreateRequest request) {
        return maintenanceLogService.createMaintenanceLog(currentUserProvider.get(), id, request);
    }

    @DeleteMapping("/{logId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMaintenanceLog(
            @PathVariable UUID id,
            @PathVariable UUID logId) {
        maintenanceLogService.deleteMaintenanceLog(currentUserProvider.get(), id, logId);
    }
}
