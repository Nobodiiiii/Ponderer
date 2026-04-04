package com.nododiiiii.ponderer.ui;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeConfigSubMenuScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

public class AiConfigScreen extends AbstractDeclarativeConfigSubMenuScreen {

    public AiConfigScreen() {
        this(new FunctionScreen());
    }

    public AiConfigScreen(Screen parent) {
        super(parent,
            Ponderer.MODID,
            "ponderer.ui.scope.client",
            "ponderer.ui.ai_config.title",
            ModConfig.Type.CLIENT,
            Config.SPEC,
            findConfigGroup(Config.SPEC, "ai"));
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        addStringConfigEntry(entries, "ponderer.ui.ai_config.provider",
            "ponderer.ui.ai_config.provider.hint",
            "ponderer.ui.ai_config.provider.tooltip",
            Config.AI_PROVIDER);
        addStringConfigEntry(entries, "ponderer.ui.ai_config.base_url",
            "ponderer.ui.ai_config.base_url.hint",
            "ponderer.ui.ai_config.base_url.tooltip",
            Config.AI_API_BASE_URL);
        addStringConfigEntry(entries, "ponderer.ui.ai_config.api_key",
            "ponderer.ui.ai_config.api_key.hint",
            "ponderer.ui.ai_config.api_key.tooltip",
            Config.AI_API_KEY);
        addStringConfigEntry(entries, "ponderer.ui.ai_config.model",
            "ponderer.ui.ai_config.model.hint",
            "ponderer.ui.ai_config.model.tooltip",
            Config.AI_MODEL);
        addStringConfigEntry(entries, "ponderer.ui.ai_config.proxy",
            "ponderer.ui.ai_config.proxy.hint",
            "ponderer.ui.ai_config.proxy.tooltip",
            Config.AI_PROXY);
        addIntegerConfigEntry(entries, "ponderer.ui.ai_config.max_tokens",
            "ponderer.ui.ai_config.max_tokens.hint",
            "ponderer.ui.ai_config.max_tokens.tooltip",
            Config.AI_MAX_TOKENS);
        addBooleanConfigEntry(entries, "ponderer.ui.ai_config.trust_ssl",
            "ponderer.ui.ai_config.trust_ssl.tooltip",
            Config.AI_TRUST_ALL_SSL);
        addBooleanConfigEntry(entries, "ponderer.ui.ai_config.web_use_proxy",
            "ponderer.ui.ai_config.web_use_proxy.tooltip",
            Config.AI_WEB_USE_PROXY);
    }

    @Override
    protected String getResetLabelKey() {
        return "ponderer.ui.ai_config.reset";
    }

    @Override
    protected String getResetTooltipKey() {
        return "ponderer.ui.ai_config.reset.tooltip";
    }

    @Override
    protected String getResetConfirmKey() {
        return "ponderer.ui.ai_config.reset.confirm";
    }

    private static UnmodifiableConfig findConfigGroup(ForgeConfigSpec spec, String... path) {
        UnmodifiableConfig current = spec.getValues();
        for (String segment : path) {
            Object next = current.valueMap().get(segment);
            if (next instanceof UnmodifiableConfig nested) {
                current = nested;
                continue;
            }
            throw new IllegalStateException("Missing config group: " + String.join(".", path));
        }
        return current;
    }
}
