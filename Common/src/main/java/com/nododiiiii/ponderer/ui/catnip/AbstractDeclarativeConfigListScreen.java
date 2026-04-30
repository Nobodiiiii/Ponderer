package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigHelper;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractDeclarativeConfigListScreen extends AbstractDeclarativeFormScreen {

    public enum ConfigScope {
        CLIENT,
        SERVER
    }

    private final String modId;
    protected final ConfigScope scope;
    protected final ModConfigSpec spec;

    protected AbstractDeclarativeConfigListScreen(@Nullable net.minecraft.client.gui.screens.Screen parent,
                                                  String modId, String scopeKey, String titleKey,
                                                  ConfigScope scope, ModConfigSpec spec) {
        super(parent, scopeKey, titleKey);
        this.modId = modId;
        this.scope = scope;
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
            ModConfigSpec.ConfigValue<Object> configValue = values.get(path);
            Object newValue = ConfigHelper.getValue(path, configValue);
            configValue.set(newValue);
            configValue.save();
        });
        spec.save();
        ConfigHelper.changes.clear();
        rebuildListPreservingScroll();
        return true;
    }

    @Override
    protected void discardEdits() {
        ConfigHelper.changes.clear();
        rebuildListPreservingScroll();
    }

    protected final void addStringConfigEntry(String labelKey,
                                              @Nullable String hintKey, @Nullable String tooltipKey,
                                              ModConfigSpec.ConfigValue<String> value) {
        appendEntry(new LocalizedStringConfigEntry(labelKey, hintKey, tooltipKey, value, specOf(value)));
    }

    protected final void addBooleanConfigEntry(String labelKey,
                                               @Nullable String tooltipKey,
                                               ModConfigSpec.ConfigValue<Boolean> value) {
        appendEntry(new LocalizedBooleanConfigEntry(labelKey, tooltipKey, value, specOf(value)));
    }

    protected final void addIntegerConfigEntry(String labelKey,
                                               @Nullable String hintKey, @Nullable String tooltipKey,
                                               ModConfigSpec.ConfigValue<Integer> value) {
        appendEntry(new LocalizedIntegerConfigEntry(labelKey, hintKey, tooltipKey, value, specOf(value)));
    }

    protected final void addChoiceConfigEntry(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                              ModConfigSpec.ConfigValue<String> value,
                                              List<String> optionLabelKeys, List<String> optionValues) {
        appendEntry(new LocalizedChoiceConfigEntry(
            labelKey, tooltipKey, buttonWidth, value, specOf(value), optionLabelKeys, optionValues));
    }

    private <T> ModConfigSpec.ValueSpec specOf(ModConfigSpec.ConfigValue<T> value) {
        return value.getSpec();
    }
}
