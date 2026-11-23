package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.Pattern;

public record UpdatePreferencesRequest(
        ThemePreferenceOption themePreference,
        @Pattern(
                regexp = "^#[0-9A-Fa-f]{6}$",
                message = "Accent color must be a valid hex color (#RRGGBB)")
        String accentColor) {}
