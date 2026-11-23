package com.familion.family.application.api;

import java.util.UUID;

public record RegisteredUser(UUID userId, UUID familyId, String email, String fullName) {}
