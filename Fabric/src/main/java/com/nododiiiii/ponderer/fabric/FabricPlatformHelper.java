package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.platform.services.PlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Fabric implementation of PlatformHelper.
 */
public class FabricPlatformHelper implements PlatformHelper {

    @Override
    public String getPlatformName() {
        return "fabric";
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public void executeOnClient(Supplier<Runnable> runnable) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            runnable.get().run();
        }
    }

    @Override
    public void openProjectorMenu(ServerPlayer player, ProjectorBlockEntity projector) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
                projector.writeMenuData(buf);
            }

            @Override
            public Component getDisplayName() {
                return projector.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return projector.createMenu(id, inventory, player);
            }
        });
    }
}
