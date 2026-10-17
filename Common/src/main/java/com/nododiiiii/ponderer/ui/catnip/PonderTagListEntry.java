package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** A Ponder category with its native texture/item icon and a multi-selection control. */
public class PonderTagListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {
    private final String id;
    @Nullable
    private final PonderTag tag;
    private final BooleanSupplier selected;
    private final List<ActionStripListEntry.ButtonModel> buttons;

    public PonderTagListEntry(String id, @Nullable PonderTag tag, BooleanSupplier selected, Runnable onToggle) {
        super("");
        this.id = id;
        this.tag = tag;
        this.selected = selected;
        this.buttons = List.of(ActionStripListEntry.button(
            () -> UIText.of(selected.getAsBoolean()
                ? "ponderer.ui.scene_tags.selected" : "ponderer.ui.scene_tags.select"),
            this::tooltip,
            onToggle,
            () -> selected.getAsBoolean() ? 0x80FF80 : 0xFFFFFF,
            () -> true));
        listeners.add(buttons.getFirst().widget());
    }

    @Override
    public boolean matchesQuery(String query) {
        return (title() + " " + id + " " + (tag == null ? "" : tag.getDescription()))
            .toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    public void tick() {
        super.tick();
        ActionStripListEntry.tickButtons(buttons);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        if (selected.getAsBoolean()) {
            graphics.fill(x, y, x + width, y + height, 0x303F8F3F);
        }
        if (tag != null) {
            tag.render(graphics, x + 6, y + (height - 16) / 2);
        } else {
            graphics.drawString(Minecraft.getInstance().font, "?", x + 10, y + 12, 0xFFFFAA55);
        }
        int controlWidth = 80;
        int controlX = x + width - controlWidth - 4;
        int textX = x + 32;
        int textWidth = Math.max(8, controlX - textX - 8);
        var font = Minecraft.getInstance().font;
        int titleColor = selected.getAsBoolean() ? 0xFF80FF80
            : annotations.containsKey("highlight") ? 0xFFF3D46B : 0xFFE8E8E8;
        graphics.drawString(font, font.plainSubstrByWidth(title(), textWidth), textX, y + 6, titleColor);
        graphics.drawString(font, font.plainSubstrByWidth(id, textWidth), textX, y + 18, 0xFF999999);
        ActionStripListEntry.renderButtonCluster(graphics, buttons, controlX, controlWidth, y, height,
            mouseX, mouseY, partialTicks);
    }

    @Override
    public Component getNarration() {
        return Component.literal(title() + ", " + id);
    }

    private String title() {
        return tag == null ? UIText.of("ponderer.ui.scene_tags.unavailable") : tag.getTitle();
    }

    private List<Component> tooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(title()));
        lines.add(Component.literal(id));
        lines.addAll(FontHelper.cutTextComponent(Component.literal(tag == null
            ? UIText.of("ponderer.ui.scene_tags.unavailable.tooltip") : tag.getDescription()), Palette.ALL_GRAY));
        return lines;
    }
}
