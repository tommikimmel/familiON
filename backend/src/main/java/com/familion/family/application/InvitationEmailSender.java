package com.familion.family.application;

public interface InvitationEmailSender {

    void sendInvitation(
            String recipientEmail, String familyName, String inviterFullName, String inviteUrl);
}
