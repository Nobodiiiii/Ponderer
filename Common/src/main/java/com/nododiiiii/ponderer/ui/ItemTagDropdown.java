package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.SceneItemBindings;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** A hover dropdown that retains the original carrier item while cycling through its tags. */
final class ItemTagDropdown {
    private static final int ROW_HEIGHT = 14;
    private static final int PADDING = 4;
    private static final int MAX_VISIBLE_ROWS = 8;

    private PlainTextListEntry entry;
    @Nullable
    private Item sourceItem;
    private String observedValue = "";
    private List<String> options = List.of();
    private Rect2i bounds = new Rect2i(0, 0, 0, 0);
    private int firstVisible;
    private int visibleRows;

    ItemTagDropdown(PlainTextListEntry entry) {
        this.entry = entry;
    }

    void attach(PlainTextListEntry entry) {
        this.entry = entry;
    }

    PlainTextListEntry entry() {
        return entry;
    }

    boolean refreshOptions() {
        String value = entry.field().getValue().trim();
        ResourceLocation id = SceneItemBindings.id(value);
        if (!value.equals(observedValue)) {
            observedValue = value;
            if (id == null) {
                sourceItem = null;
            } else if (!SceneItemBindings.isTag(value)) {
                sourceItem = BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).orElse(null);
            } else if (sourceItem == null || !sourceItem.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, id))) {
                List<ResourceLocation> resolved = SceneItemBindings.resolve(List.of(value));
                sourceItem = resolved.isEmpty() ? null : BuiltInRegistries.ITEM.get(resolved.getFirst());
            }
        }

        if (sourceItem == null) {
            options = List.of();
            return false;
        }
        List<String> refreshed = new ArrayList<>();
        refreshed.add(BuiltInRegistries.ITEM.getKey(sourceItem).toString());
        sourceItem.builtInRegistryHolder().tags()
            .map(tag -> "#" + tag.location()).sorted().forEach(refreshed::add);
        options = List.copyOf(refreshed);
        return true;
    }

    void open(int screenWidth, int screenHeight) {
        firstVisible = Math.max(0, selectedIndex() - MAX_VISIBLE_ROWS + 1);
        layout(screenWidth, screenHeight);
        revealSelection();
    }

    void layout(int screenWidth, int screenHeight) {
        var field = entry.field();
        var font = Minecraft.getInstance().font;
        int widest = field.getWidth();
        for (String option : options) {
            widest = Math.max(widest, font.width(option) + PADDING * 2 + 4);
        }
        int popupWidth = Math.min(widest, Math.max(1, screenWidth - PADDING * 2));
        int left = Math.max(PADDING, Math.min(field.getX() - 1, screenWidth - popupWidth - PADDING));
        int below = screenHeight - PADDING - (field.getY() + field.getHeight());
        int above = field.getY() - PADDING;
        boolean openBelow = below >= PADDING * 2 + Math.min(3, options.size()) * ROW_HEIGHT
            || below >= above;
        int available = openBelow ? below : above;
        visibleRows = Math.min(options.size(), Math.min(MAX_VISIBLE_ROWS,
            Math.max(1, (available - PADDING * 2) / ROW_HEIGHT)));
        int popupHeight = PADDING * 2 + visibleRows * ROW_HEIGHT;
        int top = openBelow ? field.getY() + field.getHeight() : field.getY() - popupHeight;
        bounds = new Rect2i(left, top, popupWidth, popupHeight);
        firstVisible = Math.max(0, Math.min(firstVisible, options.size() - visibleRows));
    }

    boolean isOverField(double mouseX, double mouseY) {
        return contains(entry.renderedFieldArea(), mouseX, mouseY);
    }

    boolean isOverPopup(double mouseX, double mouseY) {
        return contains(bounds, mouseX, mouseY);
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        int direction = delta > 0 ? -1 : 1;
        if (isOverField(mouseX, mouseY)) {
            select(Math.floorMod(selectedIndex() + direction, options.size()));
            revealSelection();
            return true;
        }
        if (isOverPopup(mouseX, mouseY)) {
            firstVisible = Math.max(0, Math.min(firstVisible + direction, options.size() - visibleRows));
            return true;
        }
        return false;
    }

    boolean click(double mouseX, double mouseY, int button) {
        if (!isOverPopup(mouseX, mouseY)) return false;
        if (button == 0) {
            int row = hoveredRow(mouseX, mouseY);
            if (row >= 0) select(firstVisible + row);
        }
        return true;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int left = bounds.getX();
        int top = bounds.getY();
        int right = left + bounds.getWidth();
        int rowTop = top + PADDING;
        int hovered = hoveredRow(mouseX, mouseY);
        int selected = selectedIndex();
        int textWidth = Math.max(1, bounds.getWidth() - PADDING * 2 - 4);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        new BoxElement()
            .withBackground(BoxElement.COLOR_BACKGROUND_FLAT)
            .gradientBorder(AbstractSimiWidget.COLOR_IDLE)
            .withBorderOffset(-2)
            .withBounds(bounds.getWidth(), bounds.getHeight())
            .at(left, top)
            .render(graphics);
        for (int row = 0; row < visibleRows; row++) {
            int index = firstVisible + row;
            int y = rowTop + row * ROW_HEIGHT;
            if (row == hovered) {
                graphics.fill(left + 1, y, right - 1, y + ROW_HEIGHT,
                    AbstractSimiWidget.COLOR_DISABLED.getFirst().getRGB());
            }
            graphics.drawString(font, font.plainSubstrByWidth(options.get(index), textWidth),
                left + PADDING + 2, y + 3, (index == selected
                    ? UIRenderHelper.COLOR_TEXT_STRONG_ACCENT : UIRenderHelper.COLOR_TEXT).getFirst().getRGB());
        }
        if (options.size() > visibleRows) {
            int trackHeight = visibleRows * ROW_HEIGHT;
            int thumbHeight = Math.max(8, trackHeight * visibleRows / options.size());
            int thumbTop = rowTop + (trackHeight - thumbHeight) * firstVisible / (options.size() - visibleRows);
            graphics.fillGradient(right - 3, rowTop, right - 1, rowTop + trackHeight,
                AbstractSimiWidget.COLOR_DISABLED.getFirst().getRGB(), AbstractSimiWidget.COLOR_DISABLED.getSecond().getRGB());
            graphics.fillGradient(right - 3, thumbTop, right - 1, thumbTop + thumbHeight,
                AbstractSimiWidget.COLOR_IDLE.getFirst().getRGB(), AbstractSimiWidget.COLOR_IDLE.getSecond().getRGB());
        }
        if (hovered >= 0 && font.width(options.get(firstVisible + hovered)) > textWidth) {
            graphics.renderTooltip(font, Component.literal(options.get(firstVisible + hovered)), mouseX, mouseY);
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    private void select(int index) {
        // The responder may rebuild the form and reattach this dropdown to a fresh entry.
        observedValue = options.get(index);
        entry.setValue(observedValue);
        entry.field().setFocused(true);
        entry.field().moveCursorToStart(false);
    }

    private int selectedIndex() {
        ResourceLocation id = SceneItemBindings.id(entry.field().getValue());
        String normalized = (SceneItemBindings.isTag(entry.field().getValue()) ? "#" : "") + id;
        return Math.max(0, options.indexOf(normalized));
    }

    private void revealSelection() {
        int selected = selectedIndex();
        if (selected < firstVisible) firstVisible = selected;
        if (selected >= firstVisible + visibleRows) firstVisible = selected - visibleRows + 1;
        firstVisible = Math.max(0, Math.min(firstVisible, options.size() - visibleRows));
    }

    private int hoveredRow(double mouseX, double mouseY) {
        if (!isOverPopup(mouseX, mouseY)) return -1;
        double y = mouseY - bounds.getY() - PADDING;
        int row = (int) (y / ROW_HEIGHT);
        return y >= 0 && row < visibleRows ? row : -1;
    }

    private static boolean contains(@Nullable Rect2i area, double mouseX, double mouseY) {
        return area != null && mouseX >= area.getX() && mouseY >= area.getY()
            && mouseX < area.getX() + area.getWidth() && mouseY < area.getY() + area.getHeight();
    }
}
