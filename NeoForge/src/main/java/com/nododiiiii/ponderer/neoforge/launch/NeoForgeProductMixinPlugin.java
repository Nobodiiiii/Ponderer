package com.nododiiiii.ponderer.neoforge.launch;

import com.nododiiiii.ponderer.bootstrap.launch.ProductMixinPlugin;
import com.nododiiiii.ponderer.runtime.api.RuntimeFeatures;
import net.neoforged.fml.loading.FMLLoader;

public final class NeoForgeProductMixinPlugin extends ProductMixinPlugin {
    @Override public void onLoad(String mixinPackage) {
        RuntimeFeatures.initialize(FMLLoader.getLoadingModList().getMods().stream()
            .map(mod -> mod.getOwningFile().getFile().findResource("")).toList(), FMLLoader.getGamePath());
    }
}
