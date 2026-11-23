package com.familion.family.infrastructure.web;

import com.familion.auth.application.api.SessionTokens;
import com.familion.family.application.InvitationService;
import com.familion.family.application.RegisterInviteCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class InvitationRegistrationController {

    private final InvitationService invitationService;

    public InvitationRegistrationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/invitations/{token}")
    public InvitationPreviewResponse previewInvitation(@PathVariable String token) {
        return InvitationPreviewResponse.from(invitationService.previewInvitation(token));
    }

    @PostMapping("/register-invite")
    public SessionTokens registerInvite(
            @Valid @RequestBody RegisterInviteRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return invitationService.registerFromInvitation(
                new RegisterInviteCommand(request.token(), request.fullName(), request.password()),
                userAgent);
    }
}
