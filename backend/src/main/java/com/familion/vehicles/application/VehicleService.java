package com.familion.vehicles.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.vehicles.domain.AlertCalculator;
import com.familion.vehicles.domain.MaintenanceLog;
import com.familion.vehicles.domain.MaintenanceType;
import com.familion.vehicles.domain.Vehicle;
import com.familion.vehicles.domain.VehicleAlert;
import com.familion.vehicles.infrastructure.persistence.MaintenanceLogRepository;
import com.familion.vehicles.infrastructure.persistence.VehicleRepository;
import com.familion.vehicles.infrastructure.web.VehicleCreateRequest;
import com.familion.vehicles.infrastructure.web.VehicleResponse;
import com.familion.vehicles.infrastructure.web.VehicleUpdateRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final FamilyQueryApi familyQueryApi;
    private final Clock clock;

    public VehicleService(
            VehicleRepository vehicleRepository,
            MaintenanceLogRepository maintenanceLogRepository,
            FamilyQueryApi familyQueryApi,
            Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceLogRepository = maintenanceLogRepository;
        this.familyQueryApi = familyQueryApi;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listVehicles(CurrentUser user) {
        Objects.requireNonNull(user, "user must not be null");
        List<Vehicle> vehicles = vehicleRepository
                .findAllByFamilyIdAndActiveTrueAndDeletedAtIsNullOrderByNicknameAsc(user.familyId());

        LocalDate today = resolveToday(user.familyId());
        return vehicles.stream()
                .map(v -> toResponse(v, resolveAlerts(v, today)))
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(CurrentUser user, UUID vehicleId) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");

        Vehicle vehicle = vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        LocalDate today = resolveToday(user.familyId());
        return toResponse(vehicle, resolveAlerts(vehicle, today));
    }

    @Transactional
    public VehicleResponse createVehicle(CurrentUser user, VehicleCreateRequest req) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(req, "req must not be null");

        String plate = req.licensePlate().trim().toUpperCase(Locale.ROOT);
        if (vehicleRepository.existsByFamilyIdAndLicensePlateIgnoreCaseAndDeletedAtIsNull(user.familyId(), plate)) {
            throw new ConflictException("License plate is already registered for this family");
        }

        if (req.secondaryFuelType() != null && !req.secondaryFuelType().isValidSecondary()) {
            throw new BusinessRuleException("Secondary fuel type cannot be HIBRIDO or ELECTRICO");
        }

        int initialOdometer = req.currentOdometerKm() != null ? req.currentOdometerKm() : 0;

        Vehicle vehicle = new Vehicle(
                user.familyId(),
                req.nickname().trim(),
                req.brand().trim(),
                req.model().trim(),
                req.modelYear(),
                plate,
                req.primaryFuelType(),
                req.secondaryFuelType(),
                initialOdometer,
                req.vtvExpirationDate(),
                req.insuranceExpiration(),
                req.insuranceCompany() != null ? req.insuranceCompany().trim() : null,
                req.notes() != null ? req.notes().trim() : null);

        Vehicle saved = vehicleRepository.save(vehicle);
        LocalDate today = resolveToday(user.familyId());
        return toResponse(saved, resolveAlerts(saved, today));
    }

    @Transactional
    public VehicleResponse updateVehicle(CurrentUser user, UUID vehicleId, VehicleUpdateRequest req) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(req, "req must not be null");

        Vehicle vehicle = vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        if (req.licensePlate() != null && !req.licensePlate().isBlank()) {
            String plate = req.licensePlate().trim().toUpperCase(Locale.ROOT);
            if (vehicleRepository.existsByFamilyIdAndLicensePlateIgnoreCaseAndIdNotAndDeletedAtIsNull(
                    user.familyId(), plate, vehicleId)) {
                throw new ConflictException("License plate is already registered for this family");
            }
            vehicle.setLicensePlate(plate);
        }

        if (req.nickname() != null && !req.nickname().isBlank()) {
            vehicle.setNickname(req.nickname().trim());
        }
        if (req.brand() != null && !req.brand().isBlank()) {
            vehicle.setBrand(req.brand().trim());
        }
        if (req.model() != null && !req.model().isBlank()) {
            vehicle.setModel(req.model().trim());
        }
        if (req.modelYear() != null) {
            vehicle.setModelYear(req.modelYear());
        }
        if (req.primaryFuelType() != null) {
            vehicle.setPrimaryFuelType(req.primaryFuelType());
        }
        if (req.secondaryFuelType() != null) {
            if (!req.secondaryFuelType().isValidSecondary()) {
                throw new BusinessRuleException("Secondary fuel type cannot be HIBRIDO or ELECTRICO");
            }
            vehicle.setSecondaryFuelType(req.secondaryFuelType());
        }
        if (req.currentOdometerKm() != null) {
            vehicle.updateOdometerIfGreater(req.currentOdometerKm());
        }
        if (req.vtvExpirationDate() != null) {
            vehicle.setVtvExpirationDate(req.vtvExpirationDate());
        }
        if (req.insuranceExpiration() != null) {
            vehicle.setInsuranceExpiration(req.insuranceExpiration());
        }
        if (req.insuranceCompany() != null) {
            vehicle.setInsuranceCompany(req.insuranceCompany().trim());
        }
        if (req.notes() != null) {
            vehicle.setNotes(req.notes().trim());
        }

        Vehicle saved = vehicleRepository.save(vehicle);
        LocalDate today = resolveToday(user.familyId());
        return toResponse(saved, resolveAlerts(saved, today));
    }

    @Transactional
    public void deleteVehicle(CurrentUser user, UUID vehicleId) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");

        Vehicle vehicle = vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        vehicle.softDelete(clock.instant());
        vehicleRepository.save(vehicle);
    }

    public List<VehicleAlert> resolveAlerts(Vehicle vehicle, LocalDate today) {
        List<MaintenanceLog> logs = maintenanceLogRepository.findLogsWithDuesByVehicleId(vehicle.getId());
        Map<MaintenanceType, AlertCalculator.LatestMaintenanceDue> dues = new LinkedHashMap<>();
        for (MaintenanceLog log : logs) {
            dues.putIfAbsent(
                    log.getMaintenanceType(),
                    new AlertCalculator.LatestMaintenanceDue(
                            log.getMaintenanceType(), log.getNextDueKm(), log.getNextDueDate()));
        }
        return AlertCalculator.computeAlerts(vehicle, today, new ArrayList<>(dues.values()));
    }

    public LocalDate resolveToday(UUID familyId) {
        try {
            FamilySettings settings = familyQueryApi.getSettings(familyId);
            ZoneId zoneId = settings.timezone() != null ? settings.timezone() : clock.getZone();
            return LocalDate.now(clock.withZone(zoneId));
        } catch (Exception e) {
            return LocalDate.now(clock);
        }
    }

    public static VehicleResponse toResponse(Vehicle v, List<VehicleAlert> alerts) {
        return new VehicleResponse(
                v.getId(),
                v.getNickname(),
                v.getBrand(),
                v.getModel(),
                v.getModelYear(),
                v.getLicensePlate(),
                v.getPrimaryFuelType(),
                v.getSecondaryFuelType(),
                v.getCurrentOdometerKm(),
                v.getVtvExpirationDate(),
                v.getInsuranceExpiration(),
                v.getInsuranceCompany(),
                v.getNotes(),
                v.isActive(),
                alerts != null ? alerts : List.of());
    }
}
