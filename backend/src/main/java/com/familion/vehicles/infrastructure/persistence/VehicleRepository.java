package com.familion.vehicles.infrastructure.persistence;

import com.familion.vehicles.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByFamilyIdAndActiveTrueAndDeletedAtIsNullOrderByNicknameAsc(UUID familyId);

    Optional<Vehicle> findByIdAndFamilyIdAndDeletedAtIsNull(UUID id, UUID familyId);

    boolean existsByFamilyIdAndLicensePlateIgnoreCaseAndDeletedAtIsNull(UUID familyId, String licensePlate);

    boolean existsByFamilyIdAndLicensePlateIgnoreCaseAndIdNotAndDeletedAtIsNull(
            UUID familyId, String licensePlate, UUID id);

    List<Vehicle> findAllByActiveTrueAndDeletedAtIsNull();
}
