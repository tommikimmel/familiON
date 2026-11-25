package com.familion.notifications.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotNull @Valid PushSubscriptionKeys keys,
        String userAgent) {}
