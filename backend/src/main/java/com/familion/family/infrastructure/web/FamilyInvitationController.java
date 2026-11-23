package com.familion.family.infrastructure.web;

import com.familion.common.security.CurrentUser;
import com.familion.common.security.CurrentUserProvider;
import com.familion.family.application.CreateInvitationCommand;
import com.familion.family.application.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/family/invitations")
public class FamilyInvitationController {

    private final InvitationService invitationService;
    private final CurrentUserProvider currentUserProvider;

    public FamilyInvitationController(
            InvitationService invitationService, CurrentUserProvider currentUserProvider) {
        this.invitationService = invitationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADULTO')")
    public List<InvitationResponse> listInvitations() {
        return invitationService
                .listPendingInvitations(currentUserProvider.get().familyId())
                .stream()
                .map(InvitationResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADULTO')")
    public InvitationCreatedResponse createInvitation(
            @Valid @RequestBody CreateInvitationRequest request) {
        CurrentUser currentUser = currentUserProvider.get();
        return InvitationCreatedResponse.from(invitationService.createInvitation(
                currentUser.familyId(),
                currentUser.userId(),
                new CreateInvitationCommand(
                        request.email(), request.role(), request.familyVisibility())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADULTO')")
    public void deleteInvitation(@PathVariable UUID id) {
        invitationService.deleteInvitation(currentUserProvider.get().familyId(), id);
    }
}
