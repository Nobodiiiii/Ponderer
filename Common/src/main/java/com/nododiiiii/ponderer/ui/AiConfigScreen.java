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
        if (!usesCustomConfig()) {
            return;
        }
        entries.add(ConfigEntries.choiceEntry("ponderer.ui.ai_config.provider",
            "ponderer.ui.ai_config.provider.tooltip",
            Config.AI_PROVIDER,
            100,
            List.of(
                "ponderer.ui.ai_config.provider.default_openai",
                "ponderer.ui.ai_config.provider.claude_anthropic"),
            List.of("openai", "anthropic")));
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
        entries.add(ConfigEntries.stringEntry("ponderer.ui.ai_config.proxy",
            "ponderer.ui.ai_config.proxy.hint",
            "ponderer.ui.ai_config.proxy.tooltip",
            Config.AI_PROXY));
        entries.add(ConfigEntries.integerEntry("ponderer.ui.ai_config.max_tokens",
            "ponderer.ui.ai_config.max_tokens.hint",
            "ponderer.ui.ai_config.max_tokens.tooltip",
            Config.AI_MAX_TOKENS));
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
