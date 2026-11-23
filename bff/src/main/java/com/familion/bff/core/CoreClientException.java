package com.familion.bff.core;

public class CoreClientException extends RuntimeException {

    private final int status;
    private final String problemJson;

    public CoreClientException(int status, String problemJson) {
        super("Core API returned HTTP " + status);
        this.status = status;
        this.problemJson = problemJson;
    }

    public int status() {
        return status;
    }

    public int getStatus() {
        return status;
    }

    public String problemJson() {
        return problemJson;
    }

    public String getProblemJson() {
        return problemJson;
    }
}
