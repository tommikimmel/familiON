package com.familion.auth.application;

public record RegisterCommand(String email, String password, String fullName, String familyName) {}
