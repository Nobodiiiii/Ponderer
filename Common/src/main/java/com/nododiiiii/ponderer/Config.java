package com.nododiiiii.ponderer;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_BLUEPRINT_ITEM = SERVER_BUILDER
        .comment("Enable Ponderer's built-in Blueprint item.",
                 "This is a server-side setting synced to clients.",
                 "When disabled, clients may still use their configured carrier item,",
                 "but ponderer:blueprint will not appear in creative tabs and cannot be used as the carrier.")
        .define("enableBlueprintItem", false);

    public static final ForgeConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> BLUEPRINT_CARRIER_ITEM = CLIENT_BUILDER
        .comment("Which client-side item activates the Blueprint selection tool.",
                 "Set to a different item (e.g. 'create:schematic_and_quill') to piggyback on it.",
                 "If set to 'ponderer:blueprint', it only works when the server enables the built-in Blueprint item.")
        .define("blueprintCarrierItem", "minecraft:paper");

    public static final ForgeConfigSpec.BooleanValue DEVELOPER_MODE = CLIENT_BUILDER
        .comment("Allow editing scenes that have their 'editable' flag disabled.",
                 "This is intended for pack authors and advanced maintenance.")
        .define("developerMode", false);

    public static final ForgeConfigSpec.BooleanValue DEFAULT_EDITABLE = CLIENT_BUILDER
        .comment("Default editable value for newly created scenes and scenes without an explicit editable flag.")
        .define("defaultEditable", true);

    // -- AI Scene Generation --

    public static final ForgeConfigSpec.ConfigValue<String> AI_CONFIG_SOURCE = CLIENT_BUILDER
        .comment("AI configuration source: 'custom', 'codex', or 'claude_code'.",
                 "Custom uses the fields below. Codex and Claude Code read local tool configuration.")
        .define("ai.configSource", "custom");

    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER = CLIENT_BUILDER
        .comment("LLM provider type: 'anthropic' or 'openai' (OpenAI-compatible).",
                 "Use 'openai' for OpenAI, DeepSeek, Groq, Ollama, LM Studio, etc.")
        .define("ai.provider", "openai");

    public static final ForgeConfigSpec.ConfigValue<String> AI_API_BASE_URL = CLIENT_BUILDER
        .comment("API base URL. Leave empty to use provider defaults.",
                 "Anthropic default: https://api.anthropic.com",
                 "OpenAI default: https://api.openai.com",
                 "For compatible APIs, set to e.g. https://api.deepseek.com")
        .define("ai.apiBaseUrl", "");

    public static final ForgeConfigSpec.ConfigValue<String> AI_API_KEY = CLIENT_BUILDER
        .comment("API key for the selected provider.")
        .define("ai.apiKey", "");

    public static final ForgeConfigSpec.ConfigValue<String> AI_MODEL = CLIENT_BUILDER
        .comment("Model name. Leave empty to use provider defaults.",
                 "Anthropic default: claude-sonnet-4-20250514",
                 "OpenAI default: gpt-4o")
        .define("ai.model", "");

    public static final ForgeConfigSpec.ConfigValue<String> AI_PROXY = CLIENT_BUILDER
        .comment("HTTP proxy for AI API calls. Format: host:port (e.g. 127.0.0.1:7890).",
                 "Leave empty for no proxy.")
        .define("ai.proxy", "");

    public static final ForgeConfigSpec.BooleanValue AI_TRUST_ALL_SSL = CLIENT_BUILDER
        .comment("Trust all SSL certificates (disable verification).",
                 "Enable this if you use a proxy that does SSL interception.",
                 "WARNING: only enable when using a trusted local proxy.")
        .define("ai.trustAllSsl", false);

    public static final ForgeConfigSpec.BooleanValue AI_WEB_USE_PROXY = CLIENT_BUILDER
        .comment("Use the AI proxy for web page fetching (reference URLs).",
                 "When enabled, reference URL requests go through the proxy configured above.",
                 "When disabled, reference URLs are fetched with a direct connection.")
        .define("ai.webUseProxy", false);

    public static final ForgeConfigSpec.IntValue AI_MAX_TOKENS = CLIENT_BUILDER
        .comment("Maximum number of tokens the LLM can generate per request.",
                 "Increase this if complex scenes are being cut off.",
                 "WARNING: Some models have lower limits:",
                 "  - Claude 3.5 Haiku: max 8192 tokens",
                 "  - Other Claude models: max 4096 tokens",
                 "  - GPT-4o / GPT-4o mini: max 4096 tokens",
                 "Default: 16384. Range: 1024-65536. Adjust based on your model limits.")
        .defineInRange("ai.maxTokens", 16384, 1024, 65536);

    // -- Pack Management --

    public static final ForgeConfigSpec.BooleanValue PACK_ORPHAN_PROMPT = CLIENT_BUILDER
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

    public static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}
