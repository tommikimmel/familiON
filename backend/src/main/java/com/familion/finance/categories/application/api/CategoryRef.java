package com.familion.finance.categories.application.api;

import java.util.UUID;

public record CategoryRef(UUID id, String name, String kind, String icon, String colorHex) {}
