package com.familion.family.infrastructure.web;

import com.familion.family.application.InviterView;
import java.util.UUID;

public record InviterResponse(UUID id, String fullName) {

    public static InviterResponse from(InviterView view) {
        return new InviterResponse(view.id(), view.fullName());
    }
}
