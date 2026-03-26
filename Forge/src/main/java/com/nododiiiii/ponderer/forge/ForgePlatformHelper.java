package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.forge.sticksnapshot.client.ForgeShowInterfaceClient;
import com.nododiiiii.ponderer.platform.services.PlatformHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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
        return "forge";
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
    public void showInterfaceStep(DslScene.DslStep step) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> ForgeShowInterfaceClient.showInterfaceStep(step));
    }

    @Override
    public void closeInterfaceStep(String reason) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.nododiiiii.ponderer.forge.sticksnapshot.client.ClientInputHandler.closeEmbeddedMirrorFromPonder(reason));
    }
}
