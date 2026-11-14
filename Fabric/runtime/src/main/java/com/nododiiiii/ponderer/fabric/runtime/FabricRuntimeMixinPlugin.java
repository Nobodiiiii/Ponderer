package com.nododiiiii.ponderer.fabric.runtime;

import com.nododiiiii.ponderer.runtime.api.RuntimeFeatures;
import com.nododiiiii.ponderer.runtime.launch.RuntimeMixinPlugin;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricRuntimeMixinPlugin extends RuntimeMixinPlugin {
    @Override public void onLoad(String mixinPackage) {
        var loader = FabricLoader.getInstance();
        RuntimeFeatures.initialize(loader.getAllMods().stream().flatMap(mod -> mod.getRootPaths().stream()).toList(), loader.getGameDir());
    }
}
