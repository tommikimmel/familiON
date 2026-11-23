package com.familion.auth.application.api;

import com.familion.family.application.api.UserAccount;
import java.util.Optional;

public interface SecondFactorGate {

    Optional<String> challengeIfRequired(UserAccount user, String deviceToken);
}
