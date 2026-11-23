package com.familion.family.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.family.application.FamilyMemberService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/family/members")
public class FamilyMemberController {

    private final FamilyMemberService familyMemberService;
    private final CurrentUserProvider currentUserProvider;

    public FamilyMemberController(
            FamilyMemberService familyMemberService, CurrentUserProvider currentUserProvider) {
        this.familyMemberService = familyMemberService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<MemberResponse> listMembers() {
        return familyMemberService
                .listMembers(currentUserProvider.get().familyId())
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PatchMapping("/{userId}/visibility")
    @PreAuthorize("hasRole('ADULTO')")
    public MemberResponse updateVisibility(
            @PathVariable UUID userId, @Valid @RequestBody UpdateMemberVisibilityRequest request) {
        return MemberResponse.from(familyMemberService.updateVisibility(
                currentUserProvider.get().familyId(), userId, request.familyVisibility()));
    }
}
