package com.nododiiiii.ponderer.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractJeiAwareFormScreen extends AbstractStatefulDeclarativeFormScreen
    implements JeiAwareScreen {

    private final Map<String, ItemTagDropdown> itemTagDropdowns = new LinkedHashMap<>();
    @Nullable
    private ItemTagDropdown activeItemTagDropdown;

    protected AbstractJeiAwareFormScreen(@Nullable Screen parent, String scopeKey, String titleKey,
                                         int preferredListWidth) {
        super(parent, scopeKey, titleKey, preferredListWidth);
    }

    protected AbstractJeiAwareFormScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        super(parent, scopeKey, titleKey);
    }

    public final void attachItemTagDropdown(String fieldId, PlainTextListEntry entry) {
        itemTagDropdowns.computeIfAbsent(fieldId, ignored -> new ItemTagDropdown(entry)).attach(entry);
    }

    public final boolean hasJeiIngredientFields() {
        return list != null && list.children().stream().anyMatch(entry ->
            entry instanceof PlainTextListEntry textEntry && textEntry.ingredientMode() != null);
    }

    @Override
    public List<IngredientTarget> getJeiIngredientTargets() {
        List<IngredientTarget> targets = new ArrayList<>();
        if (list == null) {
            return targets;
        }
        for (var entry : list.children()) {
            if (!(entry instanceof PlainTextListEntry textEntry) || textEntry.ingredientMode() == null) {
                continue;
            }
            Rect2i area = textEntry.ingredientSlotArea();
            if (area == null) {
                continue;
            }
            int left = Math.max(area.getX(), list.getX());
            int top = Math.max(area.getY(), list.getY());
            int right = Math.min(area.getX() + area.getWidth(), list.getRight());
            int bottom = Math.min(area.getY() + area.getHeight(), list.getBottom());
            if (right > left && bottom > top) {
                targets.add(new IngredientTarget(textEntry.field(), textEntry.ingredientMode(),
                    new Rect2i(left, top, right - left, bottom - top)));
            }
        }
        return targets;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Offscreen entries retain their widget coordinates; only freshly rendered slots may accept drops.
        if (list != null) {
            for (var entry : list.children()) {
                if (entry instanceof PlainTextListEntry textEntry) {
                    textEntry.clearRenderedAreas();
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (list != null && list.isMouseOver(mouseX, mouseY)) {
            for (var entry : list.children()) {
                if (entry instanceof PlainTextListEntry textEntry) {
                    textEntry.renderIngredientTooltip(graphics, mouseX, mouseY);
                }
            }
        }
        ItemTagDropdown dropdown = hoveredItemTagDropdown(mouseX, mouseY);
        if (dropdown != null) dropdown.render(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ItemTagDropdown dropdown = hoveredItemTagDropdown(mouseX, mouseY);
        return dropdown != null && dropdown.scroll(mouseX, mouseY, scrollY)
            || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ItemTagDropdown dropdown = hoveredItemTagDropdown(mouseX, mouseY);
        return dropdown != null && dropdown.click(mouseX, mouseY, button)
            || super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    private ItemTagDropdown hoveredItemTagDropdown(double mouseX, double mouseY) {
        if (activeItemTagDropdown != null && isDropdownFieldVisible(activeItemTagDropdown)
                && (activeItemTagDropdown.isOverPopup(mouseX, mouseY)
                    || isOverDropdownField(activeItemTagDropdown, mouseX, mouseY))
                && activeItemTagDropdown.refreshOptions()) {
            activeItemTagDropdown.layout(width, height);
            return activeItemTagDropdown;
        }
        activeItemTagDropdown = null;
        for (ItemTagDropdown dropdown : itemTagDropdowns.values()) {
            if (isDropdownFieldVisible(dropdown) && isOverDropdownField(dropdown, mouseX, mouseY)
                    && dropdown.refreshOptions()) {
                activeItemTagDropdown = dropdown;
                dropdown.open(width, height);
                return dropdown;
            }
        }
        return null;
    }

    private boolean isDropdownFieldVisible(ItemTagDropdown dropdown) {
        if (list == null || !list.children().contains(dropdown.entry())) return false;
        Rect2i area = dropdown.entry().renderedFieldArea();
        return area != null && area.getY() < list.getBottom() && area.getY() + area.getHeight() > list.getY();
    }

    private boolean isOverDropdownField(ItemTagDropdown dropdown, double mouseX, double mouseY) {
        return list != null && list.isMouseOver(mouseX, mouseY) && dropdown.isOverField(mouseX, mouseY);
    }

    @Override
    public int getGuiLeft() {
        return width / 2 - currentListWidthValue() / 2 - 40;
    }

    @Override
    public int getGuiTop() {
        return 35;
    }

    @Override
    public int getGuiWidth() {
        return currentListWidthValue() + 80;
    }

    @Override
    public int getGuiHeight() {
        return height - 60;
    }

}
