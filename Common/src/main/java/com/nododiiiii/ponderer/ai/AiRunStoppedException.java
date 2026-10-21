package com.nododiiiii.ponderer.ai;

/** A recoverable terminal outcome, never a successful scene submission. */
public final class AiRunStoppedException extends RuntimeException {
    private final String status;

    public AiRunStoppedException(String status, String message) {
        super(message);
        this.status = status;
    }

    public String status() { return status; }
}
