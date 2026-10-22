package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.platform.services.PlatformHelper;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Forge implementation of PlatformHelper.
 */
public class ForgePlatformHelper implements PlatformHelper {

    @Override
    public String getPlatformName() {
        return "neoforge";
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public java.util.Map<String, String> loadedModVersions() {
        java.util.Map<String, String> versions = new java.util.TreeMap<>();
        ModList.get().getMods().forEach(mod -> versions.put(mod.getModId(), mod.getVersion().toString()));
        return versions;
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void executeOnClient(Supplier<Runnable> runnable) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            runnable.get().run();
        }
    }

    @Override
    public void openProjectorMenu(ServerPlayer player, ProjectorBlockEntity projector) {
        net.minecraftforge.network.NetworkHooks.openScreen(player, projector, projector::writeMenuData);
    }
}
