package com.familion.finance.categories.infrastructure.persistence;

import com.familion.finance.categories.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySystemKeyAndSystemTrueAndActiveTrue(String systemKey);

    @Query(
            value = """
                    SELECT c.*
                    FROM categories c
                    LEFT JOIN categories p ON p.id = c.parent_category_id
                    WHERE (c.is_system = TRUE OR c.family_id = :familyId)
                      AND c.is_active = TRUE
                    ORDER BY c.kind ASC,
                             CASE WHEN c.parent_category_id IS NULL THEN 0 ELSE 1 END ASC,
                             COALESCE(LOWER(p.name), '') ASC,
                             LOWER(c.name) ASC,
                             c.id ASC
                    """,
            nativeQuery = true)
    List<Category> findActiveVisibleToFamilyOrdered(@Param("familyId") UUID familyId);
}
