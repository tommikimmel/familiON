package com.familion.vehicles.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.vehicles.application.FuelLogService;
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
@RequestMapping("/api/v1/vehicles/{id}/fuel-logs")
public class FuelLogController {

    private final FuelLogService fuelLogService;
    private final CurrentUserProvider currentUserProvider;

    public FuelLogController(FuelLogService fuelLogService, CurrentUserProvider currentUserProvider) {
        this.fuelLogService = fuelLogService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public Page<FuelLogResponse> listFuelLogs(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return fuelLogService.listFuelLogs(currentUserProvider.get(), id, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FuelLogResponse createFuelLog(
            @PathVariable UUID id,
            @Valid @RequestBody FuelLogCreateRequest request) {
        return fuelLogService.createFuelLog(currentUserProvider.get(), id, request);
    }

    @DeleteMapping("/{logId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFuelLog(
            @PathVariable UUID id,
            @PathVariable UUID logId) {
        fuelLogService.deleteFuelLog(currentUserProvider.get(), id, logId);
    }
}
