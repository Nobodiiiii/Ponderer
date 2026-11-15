package com.nododiiiii.ponderer.fabric.launch;

import com.nododiiiii.ponderer.bootstrap.launch.ProductMixinPlugin;
import com.nododiiiii.ponderer.runtime.api.RuntimeFeatures;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricProductMixinPlugin extends ProductMixinPlugin {
    @Override public void onLoad(String mixinPackage) {
        var loader = FabricLoader.getInstance();
        RuntimeFeatures.initialize(loader.getAllMods().stream().flatMap(mod -> mod.getRootPaths().stream()).toList(), loader.getGameDir());
    }
}
