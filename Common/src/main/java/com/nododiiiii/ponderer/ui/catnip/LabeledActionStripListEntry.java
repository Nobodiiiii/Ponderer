package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class LabeledActionStripListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private final String searchText;
    private final List<ActionStripListEntry.ButtonModel> buttons;

    public LabeledActionStripListEntry(String labelKey, @Nullable String tooltipKey,
                                       List<ActionStripListEntry.ButtonModel> buttons) {
        super(UIText.of(labelKey));
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        this.buttons = new ArrayList<>(buttons);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);
        for (ActionStripListEntry.ButtonModel button : this.buttons) {
            listeners.add(button.widget());
        }
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

    @Override
    public void tick() {
        super.tick();
        ActionStripListEntry.tickButtons(buttons);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
        int labelWidth = getLabelWidth(width);
        int controlWidth = EntryTextSupport.controlAreaWidth(width, labelWidth);
        int controlX = EntryTextSupport.rightAlignedControlX(x, width, controlWidth);
        ActionStripListEntry.renderButtonCluster(graphics, buttons, controlX, controlWidth, y, height,
            mouseX, mouseY, partialTicks);
    }

    @Override
    public Component getNarration() {
        return Component.empty();
    }
}
