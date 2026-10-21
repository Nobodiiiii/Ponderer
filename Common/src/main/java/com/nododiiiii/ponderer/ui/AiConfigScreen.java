package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeConfigListScreen;
import com.nododiiiii.ponderer.ui.catnip.ConfigEntries;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.createmod.catnip.config.ui.ConfigHelper;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

public class AiConfigScreen extends AbstractDeclarativeConfigListScreen {

    public AiConfigScreen() {
        this(new FunctionScreen());
    }

    public AiConfigScreen(Screen parent) {
        super(parent,
            Ponderer.MODID,
            "ponderer.ui.scope.client",
            "ponderer.ui.ai_config.title",
            ConfigScope.CLIENT,
            Config.CLIENT_SPEC);
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.sectionHeader(() -> UIText.of("ponderer.ui.scope.client")));
        entries.add(ConfigEntries.choiceEntry("ponderer.ui.ai_config.source",
            "ponderer.ui.ai_config.source.tooltip",
            Config.AI_CONFIG_SOURCE,
            120,
            List.of(
                "ponderer.ui.ai_config.source.codex",
                "ponderer.ui.ai_config.source.claude_code",
                "ponderer.ui.ai_config.source.custom"),
            List.of("codex", "claude_code", "custom"),
            this::rebuildListPreservingScroll));
        entries.add(ConfigEntries.choiceEntry("ponderer.ui.ai_config.reasoning_effort",
            "ponderer.ui.ai_config.reasoning_effort.tooltip",
            Config.AI_REASONING_EFFORT,
            120,
            List.of(
                "ponderer.ui.ai_config.reasoning_effort.default",
                "ponderer.ui.ai_config.reasoning_effort.none",
                "ponderer.ui.ai_config.reasoning_effort.minimal",
                "ponderer.ui.ai_config.reasoning_effort.low",
                "ponderer.ui.ai_config.reasoning_effort.medium",
                "ponderer.ui.ai_config.reasoning_effort.high",
                "ponderer.ui.ai_config.reasoning_effort.xhigh",
                "ponderer.ui.ai_config.reasoning_effort.max"),
            List.of("", "none", "minimal", "low", "medium", "high", "xhigh", "max")));
        if (usesCustomConfig()) {
            entries.add(ConfigEntries.choiceEntry("ponderer.ui.ai_config.wire_api",
                "ponderer.ui.ai_config.wire_api.tooltip",
                Config.AI_WIRE_API,
                120,
                List.of(
                    "ponderer.ui.ai_config.wire_api.auto",
                    "ponderer.ui.ai_config.wire_api.responses",
                    "ponderer.ui.ai_config.wire_api.chat",
                    "ponderer.ui.ai_config.wire_api.anthropic"),
                List.of("auto", "responses", "chat", "anthropic")));
            entries.add(ConfigEntries.stringEntry("ponderer.ui.ai_config.base_url",
                "ponderer.ui.ai_config.base_url.hint",
                "ponderer.ui.ai_config.base_url.tooltip",
                Config.AI_API_BASE_URL));
            entries.add(ConfigEntries.stringEntry("ponderer.ui.ai_config.api_key",
                "ponderer.ui.ai_config.api_key.hint",
                "ponderer.ui.ai_config.api_key.tooltip",
                Config.AI_API_KEY));
            entries.add(ConfigEntries.stringEntry("ponderer.ui.ai_config.model",
                "ponderer.ui.ai_config.model.hint",
                "ponderer.ui.ai_config.model.tooltip",
                Config.AI_MODEL));
        }
        entries.add(ConfigEntries.choiceEntry("ponderer.ui.ai_config.tool_calling", "ponderer.ui.ai_config.tool_calling.tooltip",
            Config.AI_TOOL_CALLING, 120,
            List.of("ponderer.ui.ai_config.tool_calling.auto", "ponderer.ui.ai_config.tool_calling.native", "ponderer.ui.ai_config.tool_calling.json"),
            List.of("auto", "native", "json")));
        entries.add(ConfigEntries.stringEntry("ponderer.ui.ai_config.proxy",
            "ponderer.ui.ai_config.proxy.hint",
            "ponderer.ui.ai_config.proxy.tooltip",
            Config.AI_PROXY));
        entries.add(ConfigEntries.integerEntry("ponderer.ui.ai_config.max_tokens",
            "ponderer.ui.ai_config.max_tokens.hint",
            "ponderer.ui.ai_config.max_tokens.tooltip",
            Config.AI_MAX_TOKENS));
        entries.add(ConfigEntries.integerEntry("ponderer.ui.ai_config.request_timeout",
            "ponderer.ui.ai_config.request_timeout.hint",
            "ponderer.ui.ai_config.request_timeout.tooltip",
            Config.AI_REQUEST_TIMEOUT_SECONDS));
        entries.add(ConfigEntries.integerEntry("ponderer.ui.ai_config.max_agent_turns",
            "ponderer.ui.ai_config.max_agent_turns.hint",
            "ponderer.ui.ai_config.max_agent_turns.tooltip",
            Config.AI_MAX_AGENT_TURNS));
        entries.add(ConfigEntries.integerEntry("ponderer.ui.ai_config.run_seconds", "ponderer.ui.ai_config.run_seconds.hint", "ponderer.ui.ai_config.run_seconds.tooltip", Config.AI_MAX_RUN_SECONDS));
        entries.add(ConfigEntries.integerEntry("ponderer.ui.ai_config.run_tokens", "ponderer.ui.ai_config.run_tokens.hint", "ponderer.ui.ai_config.run_tokens.tooltip", Config.AI_MAX_RUN_TOKENS));
        entries.add(ConfigEntries.integerEntry("ponderer.ui.ai_config.context_tokens", "ponderer.ui.ai_config.context_tokens.hint", "ponderer.ui.ai_config.context_tokens.tooltip", Config.AI_CONTEXT_TOKENS));
        entries.add(ConfigEntries.integerEntry("ponderer.ui.ai_config.no_progress", "ponderer.ui.ai_config.no_progress.hint", "ponderer.ui.ai_config.no_progress.tooltip", Config.AI_NO_PROGRESS_TURNS));
        entries.add(ConfigEntries.booleanEntry("ponderer.ui.ai_config.trust_ssl",
            "ponderer.ui.ai_config.trust_ssl.tooltip",
            Config.AI_TRUST_ALL_SSL));
        entries.add(ConfigEntries.booleanEntry("ponderer.ui.ai_config.web_use_proxy",
            "ponderer.ui.ai_config.web_use_proxy.tooltip",
            Config.AI_WEB_USE_PROXY));
    }

    private static boolean usesCustomConfig() {
        String source = ConfigHelper.getValue("ai.configSource", Config.AI_CONFIG_SOURCE);
        return source == null || source.isBlank() || "custom".equalsIgnoreCase(source);
    }

}
