package com.familion.family.infrastructure.web;

import com.familion.common.security.FamilyVisibility;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberVisibilityRequest(
        @NotNull(message = "Family visibility is required")
        FamilyVisibility familyVisibility) {}
