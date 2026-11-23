package com.familion.auth.application.api;

import java.util.UUID;

public interface SessionIssuer {

    SessionTokens issueSession(UUID userId, String deviceInfo);

    void revokeAllSessions(UUID userId);
}
