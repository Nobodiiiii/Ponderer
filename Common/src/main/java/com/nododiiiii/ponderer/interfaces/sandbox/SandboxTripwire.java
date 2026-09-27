package com.nododiiiii.ponderer.interfaces.sandbox;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Invariant check around every sandbox scope (I4, I6): the real player's menu, inventory,
 * cursor stack, experience and abilities, and the host screen, must be unchanged afterwards.
 * A violation is logged and degrades the session to read-only; it never throws.
 */
public final class SandboxTripwire {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private AbstractContainerMenu menu;
    private int menuStateId;
    private int inventoryTimesChanged;
    private ItemStack carried = ItemStack.EMPTY;
    private int experienceLevel;
    private boolean instabuild;
    private boolean mayBuild;
    @Nullable
    private Screen screen;
    private boolean armed;
    private int violations;

    void arm(@Nullable LocalPlayer realPlayer, @Nullable Screen hostScreen) {
        screen = hostScreen;
        armed = realPlayer != null;
        if (realPlayer == null) {
            return;
        }
        menu = realPlayer.containerMenu;
        menuStateId = menu == null ? 0 : menu.getStateId();
        inventoryTimesChanged = realPlayer.getInventory().getTimesChanged();
        carried = menu == null ? ItemStack.EMPTY : menu.getCarried().copy();
        experienceLevel = realPlayer.experienceLevel;
        instabuild = realPlayer.getAbilities().instabuild;
        mayBuild = realPlayer.getAbilities().mayBuild;
    }

    void verify(SandboxSession session, @Nullable LocalPlayer realPlayer, @Nullable Screen hostScreen) {
        String violation = null;
        if (hostScreen != screen) {
            violation = "host screen replaced by " + (hostScreen == null ? "null" : hostScreen.getClass().getName());
        } else if (armed && realPlayer != null) {
            AbstractContainerMenu current = realPlayer.containerMenu;
            if (current != menu) {
                violation = "real containerMenu replaced";
            } else if (current != null && current.getStateId() != menuStateId) {
                violation = "real containerMenu stateId changed";
            } else if (realPlayer.getInventory().getTimesChanged() != inventoryTimesChanged) {
                violation = "real inventory changed";
            } else if (current != null && !ItemStack.matches(current.getCarried(), carried)) {
                violation = "real cursor stack changed";
            } else if (realPlayer.experienceLevel != experienceLevel) {
                violation = "real experience changed";
            } else if (realPlayer.getAbilities().instabuild != instabuild || realPlayer.getAbilities().mayBuild != mayBuild) {
                violation = "real abilities changed";
            }
        }
        menu = null;
        screen = null;
        carried = ItemStack.EMPTY;
        armed = false;
        if (violation != null) {
            violations++;
            LOGGER.warn("[Ponderer] Interface sandbox tripwire: {} ({})", violation, session.describe());
            session.degradeToReadOnly("tripwire: " + violation);
        }
    }

    public int violations() {
        return violations;
    }
}
