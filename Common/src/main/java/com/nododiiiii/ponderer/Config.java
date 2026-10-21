package com.nododiiiii.ponderer;

import com.nododiiiii.ponderer.ai.AiConfigMigration;
import com.nododiiiii.ponderer.ai.AiDefaults;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.net.URI;

public class Config {
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_BLUEPRINT_ITEM = SERVER_BUILDER
        .comment("Enable Ponderer's built-in Blueprint item.",
                 "This setting is captured when the server starts; restart the game/server for changes to take effect.",
                 "When disabled, clients may still use their configured carrier item,",
                 "but ponderer:blueprint will not appear in creative tabs, cannot be used as the carrier,",
                 "its recipe will not load,",
                 "and existing built-in Blueprint items disappear when used.",
                 "[@cui:RequiresReload:server]")
        .define("enableBlueprintItem", true);

    public static final ModConfigSpec.BooleanValue ENABLE_PROJECTOR = SERVER_BUILDER
        .comment("Enable Ponderer's Projector blocks.",
                 "This setting is captured when the server starts; restart the game/server for changes to take effect.",
                 "When disabled, projector blocks will not appear in creative tabs and cannot be placed, configured, or played.",
                 "Their recipes will not load.",
                 "Existing projector blocks turn into named chests containing their internal source item,",
                 "and existing projector items disappear when used.",
                 "[@cui:RequiresReload:server]")
        .define("enableProjector", true);

    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> BLUEPRINT_CARRIER_ITEM = CLIENT_BUILDER
        .comment("Which client-side item activates the Blueprint selection tool.",
                 "Set to a different item (e.g. 'create:schematic_and_quill') to piggyback on it.",
                 "If set to 'ponderer:blueprint', it only works when the server enables the built-in Blueprint item.")
        .define("blueprintCarrierItem", "minecraft:paper");

    public static final ModConfigSpec.BooleanValue DEVELOPER_MODE = CLIENT_BUILDER
        .comment("Allow editing scenes that have their 'editable' flag disabled.",
                 "This is intended for pack authors and advanced maintenance.")
        .define("developerMode", false);

    public static final ModConfigSpec.BooleanValue DEFAULT_EDITABLE = CLIENT_BUILDER
        .comment("Default editable value for newly created scenes and scenes without an explicit editable flag.")
        .define("defaultEditable", true);

    public static final ModConfigSpec.BooleanValue DEFAULT_TEXT_PROGRESS = CLIENT_BUILDER
        .comment("Show the text progress panel by default when opening a Ponder scene.",
                 "Right-click the Comfy Reading button to toggle this default; left-click toggles the current panel.")
        .translation("ponderer.ui.mod_config.default_text_progress")
        .define("defaultTextProgress", true);

    public static final ModConfigSpec.ConfigValue<String> AUTO_SYNC_SERVER_PONDERS = CLIENT_BUILDER
        .comment("Automatically sync server Ponder scenes and their structures: 'auto', 'on', or 'off'.",
                 "Auto disables syncing in singleplayer and enables it in multiplayer, including LAN worlds.",
                 "Off stays disabled across reconnects. Manual pulls and conflict resolution remain available.")
        .translation("ponderer.ui.mod_config.auto_sync_server_ponders")
        .define("autoSyncServerPonders", "auto", value -> "auto".equals(value) || "on".equals(value) || "off".equals(value));

    // -- AI Scene Generation --

    private static final ModConfigSpec.IntValue AI_CONFIG_VERSION = CLIENT_BUILDER
        .comment("Internal AI configuration migration version. Do not edit.")
        .defineInRange("ai.configVersion", AiConfigMigration.CURRENT_VERSION, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> AI_CONFIG_SOURCE = CLIENT_BUILDER
        .comment("AI configuration source: 'custom', 'codex', or 'claude_code'.",
                 "Custom uses the fields below. Codex and Claude Code read local tool configuration.")
        .define("ai.configSource", "custom");

    public static final ModConfigSpec.ConfigValue<String> AI_WIRE_API = CLIENT_BUILDER
        .comment("Custom API protocol: 'auto', 'responses', 'chat', or 'anthropic' (Messages).",
                 "Auto detects official OpenAI/Anthropic URLs and uses Chat Completions for other services.",
                 "Legacy provider settings are migrated once. Choose a protocol explicitly for gateways.",
                 "The protocol determines the default API URL and model. Imported configs keep their own protocol.")
        .define("ai.wireApi", "auto", value -> "auto".equals(value)
            || "responses".equals(value) || "chat".equals(value) || "anthropic".equals(value));

    public static final ModConfigSpec.ConfigValue<String> AI_API_BASE_URL = CLIENT_BUILDER
        .comment("API base URL. Leave empty to use the selected protocol's default.",
                 "Anthropic default: https://api.anthropic.com/v1",
                 "OpenAI default: https://api.openai.com/v1",
                 "Include any required path prefix, such as /v1; it is not added automatically.")
        .define("ai.apiBaseUrl", "");

    public static final ModConfigSpec.ConfigValue<String> AI_API_KEY = CLIENT_BUILDER
        .comment("API key for the selected service.")
        .define("ai.apiKey", "");

    public static final ModConfigSpec.ConfigValue<String> AI_MODEL = CLIENT_BUILDER
        .comment("Model name. Leave empty to use the selected protocol's default.",
                 "Anthropic default: " + AiDefaults.ANTHROPIC_MODEL,
                 "OpenAI default: " + AiDefaults.OPENAI_MODEL)
        .define("ai.model", "");

    public static final ModConfigSpec.ConfigValue<String> AI_REASONING_EFFORT = CLIENT_BUILDER
        .comment("Reasoning effort: '', 'none', 'minimal', 'low', 'medium', 'high', 'xhigh', or 'max'.",
                 "Leave empty to inherit the selected source's effort, or use the API default if unset.",
                 "An explicit value overrides every configuration source. Choose a level supported by your model.",
                 "Anthropic supports low/medium/high/xhigh/max on compatible models; none/minimal are OpenAI-only.")
        .define("ai.reasoningEffort", "", value -> "".equals(value) || "none".equals(value)
            || "minimal".equals(value) || "low".equals(value) || "medium".equals(value)
            || "high".equals(value) || "xhigh".equals(value) || "max".equals(value));

    public static final ModConfigSpec.ConfigValue<String> AI_PROXY = CLIENT_BUILDER
        .comment("HTTP proxy for AI API calls. Format: host:port (e.g. 127.0.0.1:7890).",
                 "Leave empty for no proxy.")
        .define("ai.proxy", "");

    public static final ModConfigSpec.BooleanValue AI_TRUST_ALL_SSL = CLIENT_BUILDER
        .comment("Trust all SSL certificates (disable verification).",
                 "Enable this if you use a proxy that does SSL interception.",
                 "WARNING: only enable when using a trusted local proxy.")
        .define("ai.trustAllSsl", false);

    public static final ModConfigSpec.BooleanValue AI_WEB_USE_PROXY = CLIENT_BUILDER
        .comment("Use the AI proxy for web page fetching (reference URLs).",
                 "When enabled, reference URL requests go through the proxy configured above.",
                 "When disabled, reference URLs are fetched with a direct connection.")
        .define("ai.webUseProxy", false);

    public static final ModConfigSpec.IntValue AI_MAX_TOKENS = CLIENT_BUILDER
        .comment("Maximum output tokens per request, including reasoning tokens on thinking models.",
                 "This is a ceiling, not a target or a total budget for the entire agent run.",
                 "Leave room for both reasoning and the complete scene JSON. Higher limits can increase cost.",
                 "Default: 65536. Range: 1024-262144. Stay within your model's supported output limit.",
                 "Applies to all configuration sources. On first upgrade, lower saved limits are raised to 65536.",
                 "After migration, manually chosen lower limits are preserved.")
        .defineInRange("ai.maxTokens", AiDefaults.MAX_TOKENS, 1024, 262144);

    public static final ModConfigSpec.IntValue AI_REQUEST_TIMEOUT_SECONDS = CLIENT_BUILDER
        .comment("Timeout in seconds for each AI API request, including reasoning and output generation.",
                 "Default: 300. Range: 30-1800. Applies to all configuration sources.")
        .defineInRange("ai.requestTimeoutSeconds", AiDefaults.REQUEST_TIMEOUT_SECONDS, 30, 1800);

    public static final ModConfigSpec.ConfigValue<String> AI_TOOL_CALLING = CLIENT_BUILDER
        .comment("Tool calling transport: auto uses native tools for official OpenAI, Responses and Anthropic; native forces tools; json keeps text protocol compatibility.")
        .defineInList("ai.toolCalling", "auto", java.util.List.of("auto", "native", "json"));

    public static final ModConfigSpec.IntValue AI_MAX_RUN_SECONDS = CLIENT_BUILDER
        .comment("Wall-clock budget for one AI run, including references, tools and retries.")
        .defineInRange("maxRunSeconds", 900, 30, 7200);
    public static final ModConfigSpec.IntValue AI_MAX_RUN_TOKENS = CLIENT_BUILDER
        .comment("Total input plus output tokens across the run. Missing usage is estimated.")
        .defineInRange("maxRunTokens", 524288, 4096, 8000000);
    public static final ModConfigSpec.IntValue AI_CONTEXT_TOKENS = CLIENT_BUILDER
        .comment("Estimated per-request context ceiling; set to the selected model's actual context capacity.")
        .defineInRange("contextTokens", 131072, 4096, 2000000);
    public static final ModConfigSpec.IntValue AI_NO_PROGRESS_TURNS = CLIENT_BUILDER
        .comment("Stop after this many consecutive turns without new evidence or draft changes.")
        .defineInRange("noProgressTurns", 4, 2, 12);

    public static final ModConfigSpec.IntValue AI_MAX_AGENT_TURNS = CLIENT_BUILDER
        .comment("Maximum number of LLM turns in one AI scene-agent run.",
                 "Read-only tools may be batched within a turn.",
                 "Up to four tools may run per turn; there is no separate 16-call cap.",
                 "Default: 12. Range: 2-64. More turns can increase latency and cost.")
        .defineInRange("ai.maxAgentTurns", AiDefaults.MAX_AGENT_TURNS, 2, AiDefaults.AGENT_TURN_LIMIT);

    // -- Pack Management --

    public static final ModConfigSpec.BooleanValue PACK_ORPHAN_PROMPT = CLIENT_BUILDER
        .comment("Show a chat prompt when a registered pack's scripts have all been deleted.",
                 "The prompt offers to unregister the pack from the registry.",
                 "Set to false to suppress these prompts.")
        .define("pack.orphanPrompt", true);

    // -- Projector Text Scaling --

    public static final ModConfigSpec.DoubleValue PROJECTOR_MINIATURE_TEXT_SCALE = CLIENT_BUILDER
        .comment("Text scale multiplier for miniature projectors.",
                 "Higher values make overlay text (text windows, input bubbles) larger.",
                 "Default: 2.5. Range: 0.5-5.0.")
        .defineInRange("projector.miniatureTextScale", 2.5D, 0.5D, 5.0D);

    public static final ModConfigSpec.DoubleValue PROJECTOR_LIFE_SIZE_TEXT_SCALE = CLIENT_BUILDER
        .comment("Text scale multiplier for life-size projectors.",
                 "Higher values make overlay text (text windows, input bubbles) larger.",
                 "Default: 1.25. Range: 0.5-5.0.")
        .defineInRange("projector.lifeSizeTextScale", 1.25D, 0.5D, 5.0D);

    /** Resolve auto mode from the URL after legacy provider settings have been migrated. */
    public static String getEffectiveWireApi() {
        String protocol = AI_WIRE_API.get();
        if (!"auto".equals(protocol)) return protocol;

        String url = AI_API_BASE_URL.get().trim();
        if (url.isEmpty()) return "responses";
        String host = URI.create(normalizeBaseUrl(url)).getHost();
        if ("api.anthropic.com".equalsIgnoreCase(host)) return "anthropic";
        return "api.openai.com".equalsIgnoreCase(host) ? "responses" : "chat";
    }

    /** Resolve the effective base URL (use the protocol's default if config is empty). */
    public static String getEffectiveBaseUrl() {
        String url = AI_API_BASE_URL.get().trim();
        if (!url.isEmpty()) return normalizeBaseUrl(url);
        return "anthropic".equals(getEffectiveWireApi()) ? "https://api.anthropic.com/v1" : "https://api.openai.com/v1";
    }

    private static String normalizeBaseUrl(String url) {
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        return url;
    }

    /** Resolve the effective model name (use the protocol's default if config is empty). */
    public static String getEffectiveModel() {
        String model = AI_MODEL.get().trim();
        if (!model.isEmpty()) return model;
        return "anthropic".equals(getEffectiveWireApi()) ? AiDefaults.ANTHROPIC_MODEL : AiDefaults.OPENAI_MODEL;
    }

    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}
