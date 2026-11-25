package com.familion.vehicles.infrastructure.persistence;

import com.familion.vehicles.domain.FuelLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuelLogRepository extends JpaRepository<FuelLog, UUID> {

    Page<FuelLog> findAllByVehicleIdOrderByFuelDateDescCreatedAtDesc(UUID vehicleId, Pageable pageable);

    Optional<FuelLog> findByIdAndVehicleId(UUID id, UUID vehicleId);

    Optional<FuelLog> findByTransactionId(UUID transactionId);

    @Query("SELECT MAX(f.odometerKm) FROM FuelLog f WHERE f.vehicleId = :vehicleId AND f.fuelDate < :fuelDate")
    Integer findMaxOdometerBefore(@Param("vehicleId") UUID vehicleId, @Param("fuelDate") LocalDate fuelDate);

    @Query("SELECT MIN(f.odometerKm) FROM FuelLog f WHERE f.vehicleId = :vehicleId AND f.fuelDate > :fuelDate")
    Integer findMinOdometerAfter(@Param("vehicleId") UUID vehicleId, @Param("fuelDate") LocalDate fuelDate);

    List<FuelLog> findAllByVehicleIdAndFuelDateBetweenOrderByFuelDateAscCreatedAtAsc(
            UUID vehicleId, LocalDate from, LocalDate to);

    void deleteByTransactionId(UUID transactionId);
}
