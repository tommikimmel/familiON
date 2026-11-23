package com.familion.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditableEntityTest {

    static class SampleCreatedEntity extends CreatedAtEntity {}

    static class SampleAuditableEntity extends AuditableEntity {}

    @Test
    void setsCreatedAtOnPrePersist() {
        SampleCreatedEntity entity = new SampleCreatedEntity();
        assertThat(entity.getCreatedAt()).isNull();

        entity.onCreate();

        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void setsCreatedAtAndUpdatedAtOnPrePersistAndRefreshesUpdatedAtOnPreUpdate() {
        SampleAuditableEntity entity = new SampleAuditableEntity();
        entity.onCreate();

        Instant initialCreatedAt = entity.getCreatedAt();
        Instant initialUpdatedAt = entity.getUpdatedAt();
        assertThat(initialCreatedAt).isNotNull();
        assertThat(initialUpdatedAt).isEqualTo(initialCreatedAt);

        entity.setUpdatedAt(initialCreatedAt.minusSeconds(10));
        entity.onUpdate();

        assertThat(entity.getCreatedAt()).isEqualTo(initialCreatedAt);
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(initialCreatedAt);
    }
}
