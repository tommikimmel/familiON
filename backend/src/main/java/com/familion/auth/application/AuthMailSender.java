package com.familion.auth.application;

public interface AuthMailSender {

    void sendVerificationEmail(String to, String fullName, String code);

    void sendTwoFactorCodeEmail(String to, String fullName, String code);

    void sendPasswordResetEmailAsync(String to, String fullName, String resetUrl);

    void sendPasswordChangedEmail(String to, String fullName);
}
