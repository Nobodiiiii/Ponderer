package com.nododiiiii.ponderer.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractJeiAwareFormScreen extends AbstractStatefulDeclarativeFormScreen
    implements JeiAwareScreen {

    protected AbstractJeiAwareFormScreen(@Nullable Screen parent, String scopeKey, String titleKey,
                                         int preferredListWidth) {
        super(parent, scopeKey, titleKey, preferredListWidth);
    }

    protected AbstractJeiAwareFormScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        super(parent, scopeKey, titleKey);
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
                    textEntry.clearIngredientSlotArea();
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
