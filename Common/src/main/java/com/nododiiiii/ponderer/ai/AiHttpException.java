package com.nododiiiii.ponderer.ai;

final class AiHttpException extends RuntimeException {
    final int status;
    final long retryAfterSeconds;
    private final boolean contextOverflow;
    AiHttpException(int status, String message, String retryAfter) {
        this(status, message, retryAfter, false);
    }
    private AiHttpException(int status, String message, String retryAfter, boolean contextOverflow) {
        super("AI API HTTP " + status + ": " + message); this.status = status;
        this.contextOverflow = contextOverflow;
        long seconds = 0;
        try { seconds = Long.parseLong(retryAfter); }
        catch (NumberFormatException invalidNumber) {
            try { seconds = java.time.Duration.between(java.time.Instant.now(), java.time.ZonedDateTime.parse(retryAfter, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds(); }
            catch (RuntimeException invalidDate) { /* Missing or invalid Retry-After: use backoff. */ }
        }
        retryAfterSeconds = Math.max(0, seconds);
    }
    boolean retryable() { return status == 408 || status == 429 || status >= 500; }
    boolean contextOverflow() { return contextOverflow; }

    /** Only a classification leaves this boundary; arbitrary gateway bodies may contain secrets. */
    static AiHttpException rejected(int status, String retryAfter, String body) {
        boolean overflow = false;
        try {
            var root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            var detail = root.get("error") instanceof com.google.gson.JsonObject error ? error : root;
            overflow = isContextOverflow(detail);
        } catch (RuntimeException ignored) { /* Unknown or non-JSON errors remain sanitized. */ }
        return new AiHttpException(status, overflow ? "context capacity exceeded" :
            "request rejected; check protocol, model, credentials and service availability", retryAfter,
            overflow && (status == 400 || status == 413 || status == 422));
    }

    private static boolean isContextOverflow(com.google.gson.JsonObject error) {
        for (String key : java.util.List.of("code", "type")) {
            var value = error.get(key);
            if (value != null && value.isJsonPrimitive() && java.util.Set.of("context_length_exceeded",
                "context_window_exceeded", "prompt_too_long").contains(value.getAsString())) return true;
        }
        // Anthropic uses a generic invalid_request_error with a specific prompt-length message.
        if (error.has("type") && "invalid_request_error".equals(error.get("type").getAsString()) && error.has("message"))
            return error.get("message").getAsString().toLowerCase(java.util.Locale.ROOT).startsWith("prompt is too long");
        return false;
    }
}
