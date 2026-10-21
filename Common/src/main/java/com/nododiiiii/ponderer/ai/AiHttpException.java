package com.nododiiiii.ponderer.ai;

final class AiHttpException extends RuntimeException {
    final int status;
    final long retryAfterSeconds;
    AiHttpException(int status, String message, String retryAfter) {
        super("AI API HTTP " + status + ": " + message); this.status = status;
        long seconds = 0;
        try { seconds = Long.parseLong(retryAfter); }
        catch (NumberFormatException invalidNumber) {
            try { seconds = java.time.Duration.between(java.time.Instant.now(), java.time.ZonedDateTime.parse(retryAfter, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds(); }
            catch (RuntimeException invalidDate) { /* Missing or invalid Retry-After: use backoff. */ }
        }
        retryAfterSeconds = Math.max(0, seconds);
    }
    boolean retryable() { return status == 408 || status == 429 || status >= 500; }
}
