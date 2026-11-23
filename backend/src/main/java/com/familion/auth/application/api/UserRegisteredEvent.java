package com.familion.auth.application.api;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, UUID familyId, String email, String fullName) {}
