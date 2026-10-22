package com.nododiiiii.ponderer.ai;

/** Defaults shared by the in-game configuration and external configuration readers. */
public final class AiDefaults {
    public static final String OPENAI_MODEL = "gpt-6-astra";
    public static final String ANTHROPIC_MODEL = "claude-sonnet-5-5";
    public static final int MAX_TOKENS = 65536;
    public static final int REQUEST_TIMEOUT_SECONDS = 300;
    public static final int MAX_AGENT_TURNS = 12;
    public static final int AGENT_TURN_LIMIT = 64;

    private AiDefaults() {
    }
}
