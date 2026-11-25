package com.familion.vehicles.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.vehicles.application.VehicleService;
import com.familion.vehicles.application.VehicleSummaryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleSummaryService vehicleSummaryService;
    private final CurrentUserProvider currentUserProvider;

    public VehicleController(
            VehicleService vehicleService,
            VehicleSummaryService vehicleSummaryService,
            CurrentUserProvider currentUserProvider) {
        this.vehicleService = vehicleService;
        this.vehicleSummaryService = vehicleSummaryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<VehicleResponse> listVehicles() {
        return vehicleService.listVehicles(currentUserProvider.get());
    }

    @GetMapping("/{id}")
    public VehicleResponse getVehicle(@PathVariable UUID id) {
        return vehicleService.getVehicle(currentUserProvider.get(), id);
    }

    @GetMapping("/{id}/summary")
    public VehicleSummaryResponse getSummary(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "NOMINAL") com.familion.vehicles.domain.VehicleLens lens,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to) {
        return vehicleSummaryService.getSummary(currentUserProvider.get(), id, lens, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADULTO')")
    public VehicleResponse createVehicle(@Valid @RequestBody VehicleCreateRequest request) {
        return vehicleService.createVehicle(currentUserProvider.get(), request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADULTO')")
    public VehicleResponse updateVehicle(
            @PathVariable UUID id, @Valid @RequestBody VehicleUpdateRequest request) {
        return vehicleService.updateVehicle(currentUserProvider.get(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADULTO')")
    public void deleteVehicle(@PathVariable UUID id) {
        vehicleService.deleteVehicle(currentUserProvider.get(), id);
    }
}
