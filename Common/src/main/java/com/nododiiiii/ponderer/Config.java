package com.nododiiiii.ponderer;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> BLUEPRINT_CARRIER_ITEM = BUILDER
        .comment("Which item activates the Blueprint selection tool.",
                 "Set to a different item (e.g. 'create:schematic_and_quill') to piggyback on it.",
                 "When set to anything other than 'ponderer:blueprint', or when Create is loaded,",
                 "the built-in Blueprint item will not appear in the creative tab.")
        .define("blueprintCarrierItem", "minecraft:paper");

    // -- AI Scene Generation --

    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER = BUILDER
        .comment("LLM provider type: 'anthropic' or 'openai' (OpenAI-compatible).",
                 "Use 'openai' for OpenAI, DeepSeek, Groq, Ollama, LM Studio, etc.")
        .define("ai.provider", "anthropic");

    public static final ForgeConfigSpec.ConfigValue<String> AI_API_BASE_URL = BUILDER
        .comment("API base URL. Leave empty to use provider defaults.",
                 "Anthropic default: https://api.anthropic.com",
                 "OpenAI default: https://api.openai.com",
                 "For compatible APIs, set to e.g. https://api.deepseek.com")
        .define("ai.apiBaseUrl", "");

    public static final ForgeConfigSpec.ConfigValue<String> AI_API_KEY = BUILDER
        .comment("API key for the selected provider.")
        .define("ai.apiKey", "");

    public static final ForgeConfigSpec.ConfigValue<String> AI_MODEL = BUILDER
        .comment("Model name. Leave empty to use provider defaults.",
                 "Anthropic default: claude-sonnet-4-20250514",
                 "OpenAI default: gpt-4o")
        .define("ai.model", "");

    public static final ForgeConfigSpec.ConfigValue<String> AI_PROXY = BUILDER
        .comment("HTTP proxy for AI API calls. Format: host:port (e.g. 127.0.0.1:7890).",
                 "Leave empty for no proxy.")
        .define("ai.proxy", "");

    public static final ForgeConfigSpec.BooleanValue AI_TRUST_ALL_SSL = BUILDER
        .comment("Trust all SSL certificates (disable verification).",
                 "Enable this if you use a proxy that does SSL interception.",
                 "WARNING: only enable when using a trusted local proxy.")
        .define("ai.trustAllSsl", false);

    public static final ForgeConfigSpec.BooleanValue AI_WEB_USE_PROXY = BUILDER
        .comment("Use the AI proxy for web page fetching (reference URLs).",
                 "When enabled, reference URL requests go through the proxy configured above.",
                 "When disabled, reference URLs are fetched with a direct connection.")
        .define("ai.webUseProxy", false);

    public static final ForgeConfigSpec.IntValue AI_MAX_TOKENS = BUILDER
        .comment("Maximum number of tokens the LLM can generate per request.",
                 "Increase this if complex scenes are being cut off.",
                 "WARNING: Some models have lower limits:",
                 "  - Claude 3.5 Haiku: max 8192 tokens",
                 "  - Other Claude models: max 4096 tokens",
                 "  - GPT-4o / GPT-4o mini: max 4096 tokens",
                 "Default: 16384. Range: 1024-65536. Adjust based on your model limits.")
        .defineInRange("ai.maxTokens", 16384, 1024, 65536);

    // -- Pack Management --

    public static final ForgeConfigSpec.BooleanValue PACK_ORPHAN_PROMPT = BUILDER
        .comment("Show a chat prompt when a registered pack's scripts have all been deleted.",
                 "The prompt offers to unregister the pack from the registry.",
                 "Set to false to suppress these prompts.")
        .define("pack.orphanPrompt", true);

    /** Resolve the effective base URL (use default if config is empty). */
    public static String getEffectiveBaseUrl() {
        String url = AI_API_BASE_URL.get().trim();
        if (!url.isEmpty()) {
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            // Auto-add https:// if no scheme is present
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            // Strip trailing /v1 if present — providers will add it themselves
            if (url.endsWith("/v1")) {
                url = url.substring(0, url.length() - 3);
            }
            return url;
        }
        return "anthropic".equals(AI_PROVIDER.get()) ? "https://api.anthropic.com" : "https://api.openai.com";
    }

    /** Resolve the effective model name (use default if config is empty). */
    public static String getEffectiveModel() {
        String model = AI_MODEL.get().trim();
        if (!model.isEmpty()) return model;
        return "anthropic".equals(AI_PROVIDER.get()) ? "claude-sonnet-4-20250514" : "gpt-4o";
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
