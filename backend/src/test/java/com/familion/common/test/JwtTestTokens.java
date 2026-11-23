package com.familion.common.test;

import com.familion.common.security.AccessTokenIssuer;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JwtTestTokens {

    private final AccessTokenIssuer accessTokenIssuer;

    public JwtTestTokens(AccessTokenIssuer accessTokenIssuer) {
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public String bearer(UUID userId, UUID familyId, Role role, FamilyVisibility vis) {
        return "Bearer " + accessTokenIssuer.issue(userId, familyId, role, vis).token();
    }
}
