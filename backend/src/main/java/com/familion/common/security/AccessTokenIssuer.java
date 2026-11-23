package com.familion.common.security;

import java.time.Duration;
import java.util.UUID;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(UUID userId, UUID familyId, Role role, FamilyVisibility visibility);

    String issueChallenge(UUID userId, Duration ttl);

    UUID parseChallenge(String challengeToken);
}
