package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;

/**
 * The player handed to mod menu and screen code inside the sandbox.
 *
 * <p>Its GameProfile is necessarily the viewer's ({@code LocalPlayer} reads it from the
 * connection) and {@code clientLevel} stays the real level, but {@link #level()} is the
 * sandbox level. It never joins an entity list and never ticks. Everything that would talk
 * to the server, touch the real chat or spawn entities is routed back into the session.
 */
public class SandboxPlayer extends LocalPlayer {

    private final SandboxSession session;

    public SandboxPlayer(Minecraft minecraft, ClientLevel realLevel, ClientPacketListener realListener,
                         SandboxSession session, SandboxLevel sandboxLevel) {
        super(minecraft, realLevel, realListener, new StatsCounter(), copyRecipeBook(minecraft, realLevel), false, false);
        this.session = session;
        this.setLevel(sandboxLevel);
    }

    private static ClientRecipeBook copyRecipeBook(Minecraft minecraft, ClientLevel realLevel) {
        ClientRecipeBook book = new ClientRecipeBook();
        LocalPlayer real = minecraft.player;
        if (real != null) {
            try {
                book.copyOverData(real.getRecipeBook());
                book.setupCollections(realLevel.getRecipeManager().getRecipes(), realLevel.registryAccess());
            } catch (Throwable ignored) {
                // Recipe book contents are cosmetic for the sandbox; an empty book is fine.
            }
        }
        return book;
    }

    public SandboxSession session() {
        return session;
    }

    @Override
    public void closeContainer() {
        session.requestClose("player.closeContainer");
    }

    @Override
    public void clientSideCloseContainer() {
        session.requestClose("player.clientSideCloseContainer");
    }

    @Override
    public boolean drop(boolean fullStack) {
        return false;
    }

    @Nullable
    @Override
    public ItemEntity drop(ItemStack stack, boolean includeThrowerName) {
        session.returnToInventory(stack);
        return null;
    }

    @Nullable
    @Override
    public ItemEntity drop(ItemStack stack, boolean dropAround, boolean includeThrowerName) {
        session.returnToInventory(stack);
        return null;
    }

    @Override
    public void sendSystemMessage(Component component) {
        session.notice(component);
    }

    @Override
    public void displayClientMessage(Component chatComponent, boolean actionBar) {
        session.notice(chatComponent);
    }

    @Override
    public boolean isCreative() {
        return session.gameType() == GameType.CREATIVE;
    }

    @Override
    public boolean isSpectator() {
        return session.gameType() == GameType.SPECTATOR;
    }

    @Override
    public boolean hasPermissions(int permissionLevel) {
        return false;
    }

    @Override
    public void tick() {
    }

    @Override
    public void aiStep() {
    }
}
