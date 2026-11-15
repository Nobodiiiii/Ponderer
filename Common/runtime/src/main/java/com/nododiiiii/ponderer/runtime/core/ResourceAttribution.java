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
import com.nododiiiii.ponderer.runtime.api.PonderAttribution;
import java.util.function.Supplier;
import com.nododiiiii.ponderer.scene.LocalizedText;

/** The exported attribution icon is built in, so resource-only hosts need no model registration code. */
public final class ResourceAttribution {
    private static final PartialModel MODEL = PartialModel.of(ResourceLocation.parse("pondererlib:block/ponderer_observer"));
    private ResourceAttribution() {}
    public static void initialize() {}
    public static ResourceLocation modelLocation() { return MODEL.modelLocation(); }
    public static ResourceLocation tag(String namespace) {
        return ResourceLocation.fromNamespaceAndPath("ponderer", PonderAttribution.TAG_PATH);
    }
    public static void register(String namespace, PonderTagRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation id = tag(namespace);
        var title = LocalizedText.ref("pondererlib:attribution", "title");
        var description = LocalizedText.ref("pondererlib:attribution", "description");
        helper.registerTag(id).title(title.resolve()).description(description.resolve()).register();
        ((PonderTagRegistry) PonderIndex.getTagAccess()).registerTag(create(id, title::resolve, description::resolve));
    }

    public static PonderTag create(ResourceLocation id, Supplier<String> title, Supplier<String> description) {
        return new PonderTag(id, null, ItemStack.EMPTY, ItemStack.EMPTY) {
            @Override public String getTitle() { return title == null ? super.getTitle() : title.get(); }
            @Override public String getDescription() { return description == null ? super.getDescription() : description.get(); }
            @Override public void render(GuiGraphics graphics, int x, int y) {
                double ticks = AnimationTickHolder.getTicks(true) % 600 + AnimationTickHolder.getPartialTicksUI();
                GuiGameElement.of(MODEL).rotateBlock(30, 135 + ticks * 360 / 600, 0).scale(10).at(x + 3, y + 13, 100).render(graphics);
                RenderSystem.enableDepthTest();
            }
        };
    }
}
