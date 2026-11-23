package com.familion.auth.application;

public record CodeVerificationResult(boolean valid, int remainingAttempts) {

    public static CodeVerificationResult matched() {
        return new CodeVerificationResult(true, 0);
    }

    public static CodeVerificationResult failed(int remainingAttempts) {
        return new CodeVerificationResult(false, Math.max(0, remainingAttempts));
    }
}
