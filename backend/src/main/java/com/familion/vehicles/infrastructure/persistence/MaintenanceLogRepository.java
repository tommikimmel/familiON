package com.familion.vehicles.infrastructure.persistence;

import com.familion.vehicles.domain.MaintenanceLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, UUID> {

    Page<MaintenanceLog> findAllByVehicleIdOrderByMaintenanceDateDescCreatedAtDesc(
            UUID vehicleId, Pageable pageable);

    Optional<MaintenanceLog> findByIdAndVehicleId(UUID id, UUID vehicleId);

    Optional<MaintenanceLog> findByTransactionId(UUID transactionId);

    @Query("""
            SELECT m FROM MaintenanceLog m
            WHERE m.vehicleId = :vehicleId
              AND (m.nextDueKm IS NOT NULL OR m.nextDueDate IS NOT NULL)
            ORDER BY m.maintenanceDate DESC, m.createdAt DESC
            """)
    List<MaintenanceLog> findLogsWithDuesByVehicleId(@Param("vehicleId") UUID vehicleId);

    List<MaintenanceLog> findAllByVehicleIdAndMaintenanceDateBetweenOrderByMaintenanceDateAscCreatedAtAsc(
            UUID vehicleId, LocalDate from, LocalDate to);

    @Query("""
            SELECT m FROM MaintenanceLog m
            WHERE m.nextDueKm IS NOT NULL OR m.nextDueDate IS NOT NULL
            ORDER BY m.maintenanceDate DESC, m.createdAt DESC
            """)
    List<MaintenanceLog> findAllLogsWithDues();
}
