package com.nododiiiii.ponderer.ai;

final class AiHttpException extends RuntimeException {
    final int status;
    AiHttpException(int status, String message) { super("AI API HTTP " + status + ": " + message); this.status = status; }
    boolean retryable() { return status == 408 || status == 429 || status >= 500; }
}
