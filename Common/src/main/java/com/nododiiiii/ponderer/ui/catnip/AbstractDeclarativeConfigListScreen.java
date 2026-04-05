package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigHelper;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.net.ServerboundConfigPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractDeclarativeConfigListScreen extends AbstractDeclarativeFormScreen {

    private final String modId;
    protected final ModConfig.Type type;
    protected final ForgeConfigSpec spec;

    protected AbstractDeclarativeConfigListScreen(@Nullable net.minecraft.client.gui.screens.Screen parent,
                                                  String modId, String scopeKey, String titleKey,
                                                  ModConfig.Type type, ForgeConfigSpec spec) {
        super(parent, scopeKey, titleKey);
        this.modId = modId;
        this.type = type;
        this.spec = spec;
        ConfigScreen.modID = modId;
        ConfigHelper.changes.clear();
    }

    @Override
    protected void init() {
        ConfigScreen.modID = modId;
        super.init();
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return !ConfigHelper.changes.isEmpty();
    }

    @Override
    protected int getUnsavedChangeCount() {
        return ConfigHelper.changes.size();
    }

    @Override
    protected boolean saveEdits() {
        var values = spec.getValues();
        ConfigHelper.changes.forEach((path, change) -> {
            ForgeConfigSpec.ConfigValue<Object> configValue = values.get(path);
            Object newValue = ConfigHelper.getValue(path, configValue);
            configValue.set(newValue);

            if (type == ModConfig.Type.SERVER) {
                CatnipServices.NETWORK.sendToServer(new ServerboundConfigPacket<>(ConfigScreen.modID, path, newValue));
            }
        });
        ConfigHelper.changes.clear();
        rebuildEntries(currentListScroll());
        return true;
    }

    @Override
    protected void discardEdits() {
        ConfigHelper.changes.clear();
        rebuildEntries(currentListScroll());
    }

    protected final void addStringConfigEntry(String labelKey,
                                              @Nullable String hintKey, @Nullable String tooltipKey,
                                              ForgeConfigSpec.ConfigValue<String> value) {
        appendEntry(new LocalizedStringConfigEntry(labelKey, hintKey, tooltipKey, value, specOf(value)));
    }

    protected final void addBooleanConfigEntry(String labelKey,
                                               @Nullable String tooltipKey,
                                               ForgeConfigSpec.ConfigValue<Boolean> value) {
        appendEntry(new LocalizedBooleanConfigEntry(labelKey, tooltipKey, value, specOf(value)));
    }

    protected final void addIntegerConfigEntry(String labelKey,
                                               @Nullable String hintKey, @Nullable String tooltipKey,
                                               ForgeConfigSpec.ConfigValue<Integer> value) {
        appendEntry(new LocalizedIntegerConfigEntry(labelKey, hintKey, tooltipKey, value, specOf(value)));
    }

    protected final void addChoiceConfigEntry(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                              ForgeConfigSpec.ConfigValue<String> value,
                                              List<String> optionLabelKeys, List<String> optionValues) {
        appendEntry(new LocalizedChoiceConfigEntry(
            labelKey, tooltipKey, buttonWidth, value, specOf(value), optionLabelKeys, optionValues));
    }

    private <T> ForgeConfigSpec.ValueSpec specOf(ForgeConfigSpec.ConfigValue<T> value) {
        return spec.getRaw(value.getPath());
    }
}
