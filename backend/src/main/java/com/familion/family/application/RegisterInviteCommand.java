package com.familion.family.application;

public record RegisterInviteCommand(String token, String fullName, String password) {}
