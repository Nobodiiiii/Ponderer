package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.core.PonderRuntimeEngine;
import com.nododiiiii.ponderer.runtime.spi.*;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** The full product supplies workspace, compatibility and registration adapters to runtime. */
public final class ProductRuntime {
    private ProductRuntime() {}
    private static final class Holder {
        static final RuntimeEngine ENGINE = new PonderRuntimeEngine("ponderer", new StructureSource() {
            @Override public StructureTemplate loadSchematic(ResourceLocation id) {
                return PonderSceneRegistry.loadSchematic(id);
            }
            @Override public InputStream open(String path, String pack) throws IOException {
                var file = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().resolveStructurePath(path, pack);
                return file == null || !Files.exists(file) ? null : Files.newInputStream(file);
            }
        }, new com.nododiiiii.ponderer.compat.jei.RuntimeJeiIngredients(), new ProductRegistration(), () -> Config.DEFAULT_EDITABLE.get());
    }
    public static RuntimeEngine engine() { return Holder.ENGINE; }
}
