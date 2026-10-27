package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.interfaces.platform.InterfacePlatform;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Lets JEI's recipe/usage keys (R/U) work on the item under the cursor in the embedded interface. */
public class PonderUiIngredientHandler implements IGlobalGuiHandler {

    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        IJeiRuntime runtime = PondererJeiPlugin.getRuntime();
        SandboxSession session = InterfaceHost.sessionFor(Minecraft.getInstance().screen);
        Screen screen = session == null ? null : session.currentScreen();
        int[] rect = session == null ? null : session.guiRect();
        if (runtime == null || screen == null || rect == null) {
            return Optional.empty();
        }
        Slot slot = InterfacePlatform.get().hoveredSlot(screen);
        if (slot == null || slot.getItem().isEmpty()) {
            return Optional.empty();
        }
        ItemStack stack = slot.getItem().copy();
        Rect2i area = new Rect2i(rect[0] + slot.x, rect[1] + slot.y, 16, 16);
        return runtime.getIngredientManager()
            .createClickableIngredient(VanillaTypes.ITEM_STACK, stack, area, false)
            .map(clickable -> clickable);
    }
}
