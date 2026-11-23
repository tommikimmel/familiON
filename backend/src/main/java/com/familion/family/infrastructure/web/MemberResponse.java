package com.familion.family.infrastructure.web;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.family.application.FamilyMemberView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        String fullName,
        String email,
        Role role,
        FamilyVisibility familyVisibility,
        String accentColor,
        @JsonProperty("isActive") boolean isActive) {

    public static MemberResponse from(FamilyMemberView view) {
        return new MemberResponse(
                view.id(),
                view.fullName(),
                view.email(),
                view.role(),
                view.familyVisibility(),
                view.accentColor(),
                view.active());
    }
}
