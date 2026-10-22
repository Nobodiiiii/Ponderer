package com.nododiiiii.ponderer.mixin;

import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InputWindowElement.class)
public interface InputWindowElementAccessor {

    @Accessor("sceneSpace")
    Vec3 ponderer$getSceneSpace();

    @Accessor(value = "direction", remap = false)
    Pointing ponderer$getDirection();

    @Accessor("key")
    @Nullable
    ResourceLocation ponderer$getKey();

    @Accessor(value = "icon", remap = false)
    @Nullable
    ScreenElement ponderer$getIcon();

    @Accessor("item")
    ItemStack ponderer$getItem();
}
