package com.familion.notifications.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebPushProperties {

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushProperties(
            @Value("${VAPID_PUBLIC_KEY:}") String publicKey,
            @Value("${VAPID_PRIVATE_KEY:}") String privateKey,
            @Value("${VAPID_SUBJECT:mailto:admin@familion.local}") String subject) {
        this.publicKey = publicKey != null && !publicKey.isBlank() ? publicKey.trim() : null;
        this.privateKey = privateKey != null && !privateKey.isBlank() ? privateKey.trim() : null;
        this.subject = subject != null && !subject.isBlank() ? subject.trim() : "mailto:admin@familion.local";
    }

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getSubject() {
        return subject;
    }
}
