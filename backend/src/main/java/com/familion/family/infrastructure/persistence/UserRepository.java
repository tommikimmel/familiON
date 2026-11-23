package com.familion.family.infrastructure.persistence;

import com.familion.family.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndFamilyIdAndActiveTrue(UUID id, UUID familyId);

    List<User> findAllByFamilyIdAndActiveTrueOrderByCreatedAtAsc(UUID familyId);
}
