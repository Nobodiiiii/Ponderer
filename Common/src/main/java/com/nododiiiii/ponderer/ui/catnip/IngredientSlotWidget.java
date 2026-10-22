package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.SceneItemBindings;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.UILayoutConstants;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** A client-side ingredient slot bound to an ID field. */
public class IngredientSlotWidget extends AbstractWidget {

    /** Allocated bounds include the same transparent padding as an input field. */
    public static final int OUTER_SIZE = UILayoutConstants.LIST_INPUT_OUTER_H;
    public static final int SIZE = OUTER_SIZE - 2 * UILayoutConstants.INPUT_OUTER_PADDING;
    private static final ResourceLocation SLOT_SPRITE = new ResourceLocation("ponderer", "textures/gui/interface_slot.png");
    private static final int TAG_CYCLE_TICKS = 20;

    private final IdFieldMode mode;
    private final Supplier<String> valueGetter;
    private final Slot slot = new Slot(new SimpleContainer(1), 0, 0, 0);
    @Nullable
    private final String tooltipText;
    @Nullable
    private String previewId;
    @Nullable
    private ScreenElement previewIngredient;
    private int tagPreviewIndex = -1;
    private int lastTagCycleTick;

    public IngredientSlotWidget(IdFieldMode mode, Supplier<String> valueGetter, @Nullable String tooltipText) {
        super(0, 0, SIZE, SIZE, CommonComponents.EMPTY);
        this.mode = mode;
        this.valueGetter = valueGetter;
        this.tooltipText = tooltipText;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(SLOT_SPRITE, getX(), getY(), 0, 0, SIZE, SIZE, SIZE, SIZE);
        updatePreview();
        ItemStack stack = slot.getItem();
        int itemX = getX() + (SIZE - 16) / 2;
        int itemY = getY() + (SIZE - 16) / 2;
        if (slot.hasItem()) {
            graphics.renderItem(stack, itemX, itemY);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, itemX, itemY);
        } else if (previewIngredient != null) {
            previewIngredient.render(graphics, itemX, itemY);
        }
        if (isHoveredOrFocused()) {
            AbstractContainerScreen.renderSlotHighlight(graphics, itemX, itemY, 0);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Keyboard focus remains on the ID field.
    }

    /** Called by the form after its list scissor has ended, like container screen tooltips. */
    public void renderSlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return;
        }
        updatePreview();
        if (slot.hasItem()) {
            graphics.renderTooltip(Minecraft.getInstance().font, slot.getItem(), mouseX, mouseY);
            return;
        }
        if (JeiCompat.renderIngredientTooltip(previewIngredient, graphics, mouseX, mouseY)) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (tooltipText != null) {
            lines.add(Component.literal(tooltipText));
        }
        String hint = JeiCompat.getIngredientListVisibilityHint();
        if (hint != null) {
            lines.add(Component.literal(hint));
        }
        if (!lines.isEmpty()) {
            graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
        }
    }

    private void updatePreview() {
        String id = valueGetter.get().trim();
        // ID fields may also contain block states, item components, or inline NBT.
        int suffix = id.length();
        for (char delimiter : new char[] {'[', '{'}) {
            int index = id.indexOf(delimiter);
            if (index >= 0) {
                suffix = Math.min(suffix, index);
            }
        }
        id = id.substring(0, suffix).trim();
        boolean tagBinding = (mode == IdFieldMode.ITEM || mode == IdFieldMode.INGREDIENT)
            && SceneItemBindings.isTag(id);
        boolean bindingChanged = !id.equals(previewId);
        if (!bindingChanged && !tagBinding) {
            return;
        }
        if (bindingChanged) {
            previewId = id;
            slot.set(ItemStack.EMPTY);
            previewIngredient = null;
            tagPreviewIndex = -1;
        }
        if (tagBinding) {
            updateTagPreview(id, bindingChanged);
            return;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return;
        }
        ItemStack previewStack = ItemStack.EMPTY;
        switch (mode) {
            case ITEM, INGREDIENT -> {
                if (BuiltInRegistries.ITEM.containsKey(key)) {
                    previewStack = new ItemStack(BuiltInRegistries.ITEM.get(key));
                }
            }
            case BLOCK -> {
                if (BuiltInRegistries.BLOCK.containsKey(key)) {
                    previewStack = new ItemStack(BuiltInRegistries.BLOCK.get(key).asItem());
                }
            }
            case ENTITY -> {
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                    SpawnEggItem egg = SpawnEggItem.byId(BuiltInRegistries.ENTITY_TYPE.get(key));
                    if (egg != null) {
                        previewStack = new ItemStack(egg);
                    } else if (BuiltInRegistries.ITEM.containsKey(key)) {
                        previewStack = new ItemStack(BuiltInRegistries.ITEM.get(key));
                    }
                }
            }
        }
        if (mode == IdFieldMode.INGREDIENT && previewStack.isEmpty()) {
            previewIngredient = JeiCompat.resolveIngredientById(id);
        }
        slot.set(previewStack);
    }

    private void updateTagPreview(String binding, boolean bindingChanged) {
        // Include paused ticks so previews keep cycling in single-player configuration screens.
        int now = AnimationTickHolder.getTicks(true);
        int elapsed = now - lastTagCycleTick;
        if (!bindingChanged && elapsed >= 0 && elapsed < TAG_CYCLE_TICKS) {
            return;
        }
        lastTagCycleTick = now;

        // Resolve again each cycle to pick up tag updates without rebuilding the screen.
        List<ResourceLocation> items = SceneItemBindings.resolve(List.of(binding));
        if (items.isEmpty()) {
            tagPreviewIndex = -1;
            slot.set(ItemStack.EMPTY);
            return;
        }
        tagPreviewIndex = (tagPreviewIndex + 1) % items.size();
        slot.set(new ItemStack(BuiltInRegistries.ITEM.get(items.get(tagPreviewIndex))));
    }
}
