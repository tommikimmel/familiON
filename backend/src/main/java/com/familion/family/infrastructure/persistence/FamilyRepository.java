package com.familion.family.infrastructure.persistence;

import com.familion.family.domain.Family;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, UUID> {}
