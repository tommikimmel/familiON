package com.familion.vehicles.application;

import com.familion.vehicles.application.api.VehicleAlertQueryApi;
import com.familion.vehicles.application.api.VehicleAlertRef;
import com.familion.vehicles.domain.AlertCalculator;
import com.familion.vehicles.domain.MaintenanceLog;
import com.familion.vehicles.domain.MaintenanceType;
import com.familion.vehicles.domain.Vehicle;
import com.familion.vehicles.domain.VehicleAlert;
import com.familion.vehicles.infrastructure.persistence.MaintenanceLogRepository;
import com.familion.vehicles.infrastructure.persistence.VehicleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleAlertQueryService implements VehicleAlertQueryApi {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final Clock clock;

    public VehicleAlertQueryService(
            VehicleRepository vehicleRepository,
            MaintenanceLogRepository maintenanceLogRepository,
            Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceLogRepository = maintenanceLogRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleAlertRef> activeAlerts() {
        List<Vehicle> activeVehicles = vehicleRepository.findAllByActiveTrueAndDeletedAtIsNull();
        List<MaintenanceLog> allDuesLogs = maintenanceLogRepository.findAllLogsWithDues();

        Map<String, Map<MaintenanceType, AlertCalculator.LatestMaintenanceDue>> duesByVehicle = new LinkedHashMap<>();
        for (MaintenanceLog log : allDuesLogs) {
            String vid = log.getVehicleId().toString();
            Map<MaintenanceType, AlertCalculator.LatestMaintenanceDue> vehicleDues =
                    duesByVehicle.computeIfAbsent(vid, k -> new LinkedHashMap<>());
            vehicleDues.putIfAbsent(
                    log.getMaintenanceType(),
                    new AlertCalculator.LatestMaintenanceDue(
                            log.getMaintenanceType(), log.getNextDueKm(), log.getNextDueDate()));
        }

        LocalDate today = LocalDate.now(clock);
        List<VehicleAlertRef> result = new ArrayList<>();

        for (Vehicle v : activeVehicles) {
            Map<MaintenanceType, AlertCalculator.LatestMaintenanceDue> vDues =
                    duesByVehicle.getOrDefault(v.getId().toString(), Map.of());
            List<VehicleAlert> alerts =
                    AlertCalculator.computeAlerts(v, today, new ArrayList<>(vDues.values()));

            for (VehicleAlert alert : alerts) {
                if ("WARNING".equals(alert.severity()) || "OVERDUE".equals(alert.severity())) {
                    result.add(new VehicleAlertRef(
                            v.getFamilyId(),
                            v.getId(),
                            v.getNickname(),
                            alert.kind(),
                            alert.title(),
                            alert.dueDate(),
                            alert.dueKm(),
                            alert.severity()));
                }
            }
        }

        return result;
    }
}
