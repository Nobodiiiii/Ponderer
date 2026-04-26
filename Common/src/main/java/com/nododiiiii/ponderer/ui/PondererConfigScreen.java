package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeConfigListScreen;
import com.nododiiiii.ponderer.ui.catnip.ConfigEntries;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

public class PondererConfigScreen extends AbstractDeclarativeConfigListScreen {

    public PondererConfigScreen(Screen parent) {
        super(parent,
            Ponderer.MODID,
            "ponderer.ui.scope.client",
            "ponderer.ui.mod_config.title",
            ModConfig.Type.CLIENT,
            Config.SPEC);
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(ConfigEntries.booleanEntry("ponderer.ui.mod_config.default_editable",
            "ponderer.ui.mod_config.default_editable.tooltip",
            Config.DEFAULT_EDITABLE));
        entries.add(ConfigEntries.booleanEntry("ponderer.ui.mod_config.developer_mode",
            "ponderer.ui.mod_config.developer_mode.tooltip",
            Config.DEVELOPER_MODE));
    }
}
