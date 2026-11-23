package com.familion.auth.application;

public record LoginCommand(String email, String password, String deviceToken) {}
