package com.nododiiiii.ponderer.neoforge.runtime;

import com.nododiiiii.ponderer.runtime.api.RuntimeFeatures;
import com.nododiiiii.ponderer.runtime.launch.RuntimeMixinPlugin;
import net.neoforged.fml.loading.FMLLoader;

public final class NeoForgeRuntimeMixinPlugin extends RuntimeMixinPlugin {
    @Override public void onLoad(String mixinPackage) {
        RuntimeFeatures.initialize(FMLLoader.getLoadingModList().getMods().stream()
            .map(mod -> mod.getOwningFile().getFile().findResource("")).toList(), FMLLoader.getGamePath());
    }
}
