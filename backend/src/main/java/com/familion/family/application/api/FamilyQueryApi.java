package com.familion.family.application.api;

import java.util.List;
import java.util.UUID;

public interface FamilyQueryApi {

    FamilySettings getSettings(UUID familyId);

    MemberRef requireMember(UUID familyId, UUID userId);

    List<MemberRef> listMembers(UUID familyId);
}
