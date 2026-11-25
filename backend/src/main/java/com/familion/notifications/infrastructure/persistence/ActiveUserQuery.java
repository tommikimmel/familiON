package com.familion.notifications.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActiveUserQuery {

    public record ActiveUser(UUID userId, UUID familyId) {}

    private final JdbcTemplate jdbcTemplate;

    public ActiveUserQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ActiveUser> findAllActiveUsers() {
        return jdbcTemplate.query(
                "SELECT id, family_id FROM users WHERE is_active = TRUE",
                (rs, rowNum) -> {
                    UUID userId = rs.getObject("id", UUID.class);
                    UUID familyId = rs.getObject("family_id", UUID.class);
                    return new ActiveUser(userId, familyId);
                });
    }
}
