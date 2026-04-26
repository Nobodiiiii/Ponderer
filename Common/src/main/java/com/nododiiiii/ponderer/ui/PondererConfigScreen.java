package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeConfigListScreen;
import com.nododiiiii.ponderer.ui.catnip.ConfigEntries;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.config.ModConfig;

import javax.annotation.Nullable;
import java.util.List;

public class PondererConfigScreen extends AbstractDeclarativeConfigListScreen {

    @Nullable
    private final PonderScreenNavigation.ReturnState ponderReturnState;

    public PondererConfigScreen(Screen parent) {
        this(parent, null);
    }

    public PondererConfigScreen(Screen parent, @Nullable PonderScreenNavigation.ReturnState ponderReturnState) {
        super(parent,
            Ponderer.MODID,
            "ponderer.ui.scope.client",
            "ponderer.ui.mod_config.title",
            ModConfig.Type.CLIENT,
            Config.CLIENT_SPEC);
        this.ponderReturnState = ponderReturnState;
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

    @Override
    protected void attemptBackToParent() {
        if (ponderReturnState == null) {
            super.attemptBackToParent();
            return;
        }

        if (!hasUnsavedChanges()) {
            restoreAndOpenParent();
            return;
        }

        showLeavingPrompt(response -> {
            if (response == ConfirmationScreen.Response.Cancel) {
                return;
            }
            if (response == ConfirmationScreen.Response.Confirm) {
                if (!saveEdits()) {
                    return;
                }
            } else {
                discardEdits();
            }
            restoreAndOpenParent();
        });
    }

    private void restoreAndOpenParent() {
        PonderScreenNavigation.restoreReturnState(ponderReturnState);
        Minecraft.getInstance().setScreen(parent);
    }
}
