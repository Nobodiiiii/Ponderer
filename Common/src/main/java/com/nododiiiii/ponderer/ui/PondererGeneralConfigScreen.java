package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeConfigListScreen;
import com.nododiiiii.ponderer.ui.catnip.ConfigEntries;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

public class PondererGeneralConfigScreen extends AbstractDeclarativeConfigListScreen {

    public PondererGeneralConfigScreen(Screen parent) {
        super(parent,
            Ponderer.MODID,
            "ponderer.ui.scope.config",
            "ponderer.ui.mod_config.general.title",
            ConfigScope.CLIENT,
            Config.CLIENT_SPEC);
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.sectionHeader(() -> UIText.of("ponderer.ui.scope.client")));
        entries.add(ConfigEntries.booleanEntry(
            "ponderer.ui.mod_config.developer_mode",
            "ponderer.ui.mod_config.developer_mode.tooltip",
            Config.DEVELOPER_MODE));
        entries.add(ConfigEntries.booleanEntry(
            "ponderer.ui.mod_config.default_editable",
            "ponderer.ui.mod_config.default_editable.tooltip",
            Config.DEFAULT_EDITABLE));
        entries.add(ConfigEntries.booleanEntry(
            "ponderer.ui.mod_config.pack_orphan_prompt",
            "ponderer.ui.mod_config.pack_orphan_prompt.tooltip",
            Config.PACK_ORPHAN_PROMPT));
    }
}
