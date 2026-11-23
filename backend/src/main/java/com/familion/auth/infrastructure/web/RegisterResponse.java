package com.familion.auth.infrastructure.web;

import java.util.UUID;

public record RegisterResponse(UUID userId, String email) {}
