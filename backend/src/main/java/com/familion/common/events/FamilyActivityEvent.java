package com.familion.common.events;

import java.util.UUID;

/** A user-facing mutation that succeeded: who ({@code actorUserId}) did what ({@code method} on {@code path}) on what ({@code label}). */
public record FamilyActivityEvent(UUID familyId, UUID actorUserId, String method, String path, String label) {}
