package com.nododiiiii.ponderer.application.ui.widgets;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.application.ui.PondererUiScaling;
import com.nododiiiii.ponderer.support.ui.UIText;
import net.createmod.catnip.config.ui.ConfigHelper;
import net.minecraft.client.Minecraft;

public class EditorUiScaleConfigEntry extends ButtonListEntry {

    public EditorUiScaleConfigEntry() {
        this(ConfigEntrySupport.metadataOf(Config.EDITOR_UI_SCALE, Config.EDITOR_UI_SCALE.getSpec()));
    }

    private EditorUiScaleConfigEntry(ConfigEntrySupport.Metadata metadata) {
        super("ponderer.ui.mod_config.editor_ui_scale", "ponderer.ui.mod_config.editor_ui_scale.tooltip",
            () -> cycleValue(metadata), () -> currentLabel(metadata));
        this.path = metadata.path();
        this.annotations.putAll(metadata.annotations());
    }

    private static void cycleValue(ConfigEntrySupport.Metadata metadata) {
        Minecraft mc = Minecraft.getInstance();
        String value = ConfigEntrySupport.currentValue(metadata, Config.EDITOR_UI_SCALE);
        int current = "native".equals(value) ? 0 : numericScale(mc, value);
        int maxScale = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
        String next = current >= maxScale ? "native" : Integer.toString(current + 1);
        ConfigHelper.setValue(metadata.path(), Config.EDITOR_UI_SCALE, next, metadata.annotations());
    }

    private static String currentLabel(ConfigEntrySupport.Metadata metadata) {
        String value = ConfigEntrySupport.currentValue(metadata, Config.EDITOR_UI_SCALE);
        return "native".equals(value)
            ? UIText.of("ponderer.ui.mod_config.editor_ui_scale.native")
            : Integer.toString(numericScale(Minecraft.getInstance(), value));
    }

    private static int numericScale(Minecraft mc, String value) {
        return "default".equals(value)
            ? PondererUiScaling.computeTargetScale(mc, value)
            : Integer.parseInt(value);
    }
}
