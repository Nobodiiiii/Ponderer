package com.nododiiiii.ponderer.application.ui.widgets;

import com.nododiiiii.ponderer.application.ui.UILayoutConstants;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class DynamicActionRowListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {
    private static final int ICON_SLOT_WIDTH = 24;
    private static final int ICON_SIZE = 16;

    private final Supplier<String> labelGetter;
    private final Supplier<String> detailGetter;
    private final Supplier<String> searchTextGetter;
    private final List<ActionStripListEntry.ButtonModel> buttons;
    @Nullable
    private final Supplier<ItemStack> iconStackGetter;
    private final BooleanSupplier highlightGetter;

    public DynamicActionRowListEntry(Supplier<String> labelGetter, Supplier<String> detailGetter,
                                     @Nullable Supplier<String> searchTextGetter,
                                     List<ActionStripListEntry.ButtonModel> buttons) {
        this(labelGetter, detailGetter, searchTextGetter, buttons, null, () -> false);
    }

    public DynamicActionRowListEntry(Supplier<String> labelGetter, Supplier<String> detailGetter,
                                     @Nullable Supplier<String> searchTextGetter,
                                     List<ActionStripListEntry.ButtonModel> buttons,
                                     @Nullable Supplier<ItemStack> iconStackGetter,
                                     BooleanSupplier highlightGetter) {
        super("");
        this.labelGetter = labelGetter;
        this.detailGetter = detailGetter;
        this.searchTextGetter = searchTextGetter == null ? this::defaultSearchText : searchTextGetter;
        this.buttons = new ArrayList<>(buttons);
        this.iconStackGetter = iconStackGetter;
        this.highlightGetter = highlightGetter;
        for (ActionStripListEntry.ButtonModel button : this.buttons) {
            listeners.add(button.widget());
        }
    }

    @Override
    public boolean matchesQuery(String query) {
        return searchTextGetter.get().toLowerCase(Locale.ROOT).contains(query);
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
        int controlWidth = controlWidth(width);
        int controlX = EntryTextSupport.rightAlignedControlX(x, width, controlWidth);
        int textX = EntryTextSupport.contentLeft(x);
        if (iconStackGetter != null) {
            renderIcon(graphics, textX, y + Math.max(0, (height - ICON_SIZE) / 2));
            textX += ICON_SLOT_WIDTH;
        }
        int textWidth = Math.max(20, controlX - textX - 8);
        var font = Minecraft.getInstance().font;
        boolean highlighted = annotations.containsKey("highlight") || highlightGetter.getAsBoolean();
        int titleColor = highlighted ? 0xFFF3D46B : 0xFFE8E8E8;
        int detailColor = highlighted ? 0xFFFFD75F : 0xFF999999;
        String title = font.plainSubstrByWidth(labelGetter.get(), textWidth);
        String detail = font.plainSubstrByWidth(detailGetter.get(), textWidth);
        boolean compact = height <= UILayoutConstants.COMPACT_LIST_ENTRY_H;
        int titleY = compact ? y + Math.max(0, (height - font.lineHeight) / 2) : y + 6;
        graphics.drawString(font, title, textX, titleY, titleColor);
        if (!compact && !detail.isBlank()) {
            graphics.drawString(font, detail, textX, y + 18, detailColor);
        }
        ActionStripListEntry.renderMultiControlCluster(graphics, buttons, controlX, controlWidth, y, height,
            mouseX, mouseY, partialTicks);
    }

    @Override
    public Component getNarration() {
        return Component.empty();
    }

    protected int controlWidth(int width) {
        boolean fixedButtons = buttons.stream().allMatch(button -> button.fixedWidth() > 0);
        return fixedButtons
            ? buttons.size() * EntryTextSupport.squareSize()
                + Math.max(0, buttons.size() - 1) * EntryTextSupport.multiControlGap()
            : Math.min(150, Math.max(96, width / 2));
    }

    private String defaultSearchText() {
        return labelGetter.get() + " " + detailGetter.get();
    }

    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        ItemStack stack = getIconStack();
        if (!stack.isEmpty()) {
            try {
                graphics.renderItem(stack, x, y);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        renderMissingIcon(graphics, x, y);
    }

    private ItemStack getIconStack() {
        if (iconStackGetter == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = iconStackGetter.get();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private static void renderMissingIcon(GuiGraphics graphics, int x, int y) {
        var font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x66000000);
        graphics.drawCenteredString(font, "?", x + ICON_SIZE / 2, y + 4, 0xFFFFD75F);
    }
}
