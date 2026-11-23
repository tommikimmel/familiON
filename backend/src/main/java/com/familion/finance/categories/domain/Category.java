package com.familion.finance.categories.domain;

import com.familion.common.persistence.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 15)
    private CategoryKind kind;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "system_key", length = 40)
    private String systemKey;

    protected Category() {}

    private Category(
            UUID familyId,
            UUID parentCategoryId,
            String name,
            CategoryKind kind,
            String icon,
            String colorHex,
            boolean system,
            boolean active,
            String systemKey) {
        this.familyId = familyId;
        this.parentCategoryId = parentCategoryId;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.icon = icon;
        this.colorHex = colorHex;
        this.system = system;
        this.active = active;
        this.systemKey = systemKey;
    }

    public static Category createCustom(
            UUID familyId,
            UUID parentCategoryId,
            String name,
            CategoryKind kind,
            String icon,
            String colorHex) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        return new Category(
                familyId,
                parentCategoryId,
                name.trim(),
                kind,
                normalizeOptional(icon),
                normalizeOptional(colorHex),
                false,
                true,
                null);
    }

    public void update(String name, String icon, String colorHex, Boolean active) {
        if (name != null) {
            this.name = name.trim();
        }
        if (icon != null) {
            this.icon = normalizeOptional(icon);
        }
        if (colorHex != null) {
            this.colorHex = normalizeOptional(colorHex);
        }
        if (active != null) {
            this.active = active;
        }
    }

    public boolean isVisibleTo(UUID targetFamilyId) {
        return this.system || Objects.equals(this.familyId, targetFamilyId);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getParentCategoryId() {
        return parentCategoryId;
    }

    public String getName() {
        return name;
    }

    public CategoryKind getKind() {
        return kind;
    }

    public String getIcon() {
        return icon;
    }

    public String getColorHex() {
        return colorHex;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isActive() {
        return active;
    }

    public String getSystemKey() {
        return systemKey;
    }
}
