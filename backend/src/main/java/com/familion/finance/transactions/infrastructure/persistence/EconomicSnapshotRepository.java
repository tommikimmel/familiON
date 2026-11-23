package com.familion.finance.transactions.infrastructure.persistence;

import com.familion.finance.transactions.domain.EconomicSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicSnapshotRepository extends JpaRepository<EconomicSnapshot, UUID> {}
