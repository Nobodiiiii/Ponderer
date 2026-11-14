package com.nododiiiii.ponderer.runtime.core;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The exported attribution icon is built in, so resource-only hosts need no model registration code. */
public final class ResourceAttribution {
    private static final PartialModel MODEL = PartialModel.of(ResourceLocation.parse("pondererlib:block/ponderer_observer"));
    private ResourceAttribution() {}
    public static void initialize() {}
    public static ResourceLocation modelLocation() { return MODEL.modelLocation(); }
    public static void register(String namespace, PonderTagRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, "ponderer_exported");
        helper.registerTag(id).title("The Ponderer...")
            .description("This mod's Ponder scenes were created and exported with The Ponderer. Learn more: https://modrinth.com/mod/the-ponderer").register();
        ((PonderTagRegistry) PonderIndex.getTagAccess()).registerTag(new PonderTag(id, null, ItemStack.EMPTY, ItemStack.EMPTY) {
            @Override public void render(GuiGraphics graphics, int x, int y) {
                double ticks = AnimationTickHolder.getTicks(true) % 600 + AnimationTickHolder.getPartialTicksUI();
                GuiGameElement.of(MODEL).rotateBlock(30, 135 + ticks * 360 / 600, 0).scale(10).at(x + 3, y + 13, 100).render(graphics);
                RenderSystem.enableDepthTest();
            }
        });
    }
}
