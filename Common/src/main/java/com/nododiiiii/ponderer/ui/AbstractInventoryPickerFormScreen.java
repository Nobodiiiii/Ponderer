package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/** Shared inventory selection for scene forms and standalone item fields. */
public abstract class AbstractInventoryPickerFormScreen extends AbstractJeiAwareFormScreen
    implements HeldItemButtonHost {

    @Nullable
    private PlayerInventoryPicker inventoryPicker;
    @Nullable
    private InventoryPickerViewport inventoryPickerViewport;

    private record InventoryPickerViewport(ConfigScreenList list, int x, int y, int height, double scroll) {
        void restore() {
            list.updateSizeAndPosition(list.getWidth(), height, y);
            list.setX(x);
            list.setScrollAmount(scroll);
        }
    }

    protected AbstractInventoryPickerFormScreen(@Nullable Screen parent, String scopeKey, String titleKey,
                                                 int preferredListWidth) {
        super(parent, scopeKey, titleKey, preferredListWidth);
    }

    @Override
    protected void init() {
        closeInventoryPicker();
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (inventoryPicker != null && (minecraft == null || minecraft.player == null)) {
            closeInventoryPicker();
        }
        // Hide the underlying form's hover effects and tooltips while the popup takes input.
        super.render(graphics, inventoryPicker == null ? mouseX : -1,
            inventoryPicker == null ? mouseY : -1, partialTicks);
        if (inventoryPicker != null) {
            inventoryPicker.render(graphics, mouseX, mouseY, width);
        }
    }

    @Override
    public List<IngredientTarget> getJeiIngredientTargets() {
        return inventoryPicker == null ? super.getJeiIngredientTargets() : List.of();
    }

    @Override
    public List<Rect2i> getJeiExtraAreas() {
        List<Rect2i> areas = new java.util.ArrayList<>(super.getJeiExtraAreas());
        if (inventoryPicker != null) areas.add(inventoryPicker.occupiedArea(width));
        return areas;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inventoryPicker != null) {
            if (!inventoryPicker.contains(mouseX, mouseY)) {
                closeInventoryPicker();
            } else if (button == 0) {
                inventoryPicker.pick(mouseX, mouseY);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return inventoryPicker != null || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (inventoryPicker != null) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return inventoryPicker != null || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inventoryPicker != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeInventoryPicker();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return inventoryPicker != null || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (inventoryPicker != null) {
            closeInventoryPicker();
            return;
        }
        super.onClose();
    }

    @Override
    public final void openInventoryPickerFromButton(PlainTextListEntry entry, Consumer<ItemStack> onItemPicked) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        closeInventoryPicker();
        clearStatusMessages();
        makeRoomForInventoryPicker(entry);
        inventoryPicker = new PlayerInventoryPicker(mc.player.getInventory(), entry.field(), stack -> {
            closeInventoryPicker();
            onItemPicked.accept(stack);
            setInfoMessage(UIText.of("ponderer.ui.inventory_pick.filled", stack.getHoverName().getString()));
        });
    }

    private void makeRoomForInventoryPicker(PlainTextListEntry entry) {
        if (list == null || !list.children().contains(entry)) {
            return;
        }
        int fieldBottom = entry.field().getY() + entry.field().getHeight();
        int maxFieldBottom = height - PlayerInventoryPicker.SCREEN_MARGIN
            - PlayerInventoryPicker.HEIGHT - PlayerInventoryPicker.GAP;
        if (fieldBottom <= maxFieldBottom) {
            return;
        }
        inventoryPickerViewport = new InventoryPickerViewport(list, list.getX(), list.getY(),
            list.getHeight(), list.getScrollAmount());
        // Reserve space below the row even when the selected field is near the end of the list.
        int minimumHeight = getEntryHeight() + 11;
        int top = Math.min(list.getY(), Math.max(0, maxFieldBottom - minimumHeight));
        int viewportHeight = Math.max(minimumHeight, Math.min(list.getHeight(), maxFieldBottom + 8 - top));
        double scroll = list.getScrollAmount() + fieldBottom - maxFieldBottom + top - list.getY();
        list.updateSizeAndPosition(list.getWidth(), viewportHeight, top);
        list.setX(inventoryPickerViewport.x());
        list.setScrollAmount(scroll);
    }

    private void closeInventoryPicker() {
        inventoryPicker = null;
        if (inventoryPickerViewport != null) {
            inventoryPickerViewport.restore();
            inventoryPickerViewport = null;
        }
        setDragging(false);
    }
}
