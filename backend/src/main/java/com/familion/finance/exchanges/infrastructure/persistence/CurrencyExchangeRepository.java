package com.familion.finance.exchanges.infrastructure.persistence;

import com.familion.finance.exchanges.domain.CurrencyExchange;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyExchangeRepository extends JpaRepository<CurrencyExchange, UUID> {

    Optional<CurrencyExchange> findByIdAndFamilyId(UUID id, UUID familyId);
}
