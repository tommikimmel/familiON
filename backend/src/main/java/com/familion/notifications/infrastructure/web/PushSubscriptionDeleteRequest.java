package com.familion.notifications.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record PushSubscriptionDeleteRequest(@NotBlank String endpoint) {}
