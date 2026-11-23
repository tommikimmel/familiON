package com.familion.family.infrastructure.persistence;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyTransactionProbe {

    private final JdbcTemplate jdbcTemplate;

    public FamilyTransactionProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasTransactions(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM transactions WHERE family_id = ?)",
                Boolean.class,
                familyId);
        return Boolean.TRUE.equals(exists);
    }
}
