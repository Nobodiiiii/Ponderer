package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.config.ui.entries.StringEntry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;

public class LocalizedStringConfigEntry extends StringEntry implements SearchableListEntry {

    private final String searchText;

    public LocalizedStringConfigEntry(String labelKey, @Nullable String hintKey, @Nullable String tooltipKey,
                                      ForgeConfigSpec.ConfigValue<String> value, ForgeConfigSpec.ValueSpec spec) {
        super(com.nododiiiii.ponderer.ui.UIText.of(labelKey), value, spec);
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);
        listeners.remove(textField);
        ClippedConfigTextField clippedField = new ClippedConfigTextField(Minecraft.getInstance().font, 0, 0, 200, 20);
        clippedField.setValue(textField.getValue());
        clippedField.setResponder(this::setValue);
        clippedField.setTextColor(UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
        clippedField.moveCursorToStart();
        textField = clippedField;
        listeners.add(textField);
        EntryTextSupport.applyHint(textField, hintKey);
    }

    @Override
    public boolean matchesQuery(String query) {
        return searchText.contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    protected int getLabelWidth(int totalWidth) {
        return EntryTextSupport.compactLabelWidth(totalWidth);
    }
}
