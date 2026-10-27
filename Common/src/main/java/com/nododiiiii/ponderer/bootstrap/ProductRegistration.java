package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import com.nododiiiii.ponderer.ponder.PondererAttribution;
import com.nododiiiii.ponderer.ponder.CustomPonderTags;
import com.nododiiiii.ponderer.runtime.spi.RegistrationContribution;

/** Product-owned registration contributions; absent for standalone runtime hosts. */
public final class ProductRegistration implements RegistrationContribution {
    @Override public void beforeTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PondererAttribution.register();
        CustomPonderTags.register();
    }
    @Override public void afterTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        Item carrierItem = BlueprintFeature.resolveCarrierItem();
        if (carrierItem != Items.AIR) helper.addTagToComponent(BuiltInRegistries.ITEM.getKey(carrierItem), ResourceLocation.parse(PondererAttribution.TAG_ID));
    }
    @Override public void afterScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) { registerBlueprintGuideScene(helper); }
    @Override public List<String> tagsFor(List<String> tags) { return PondererAttribution.withTag(tags); }

    private void registerBlueprintGuideScene(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        Item carrierItem = BlueprintFeature.resolveCarrierItem();
        if (carrierItem == Items.AIR) {
            return;
        }
        ResourceLocation carrier = BuiltInRegistries.ITEM.getKey(carrierItem);
        if (carrier == null) {
            return;
        }

        helper.forComponents(carrier)
            .addStoryBoard(
                ResourceLocation.fromNamespaceAndPath("ponderer", "basic"),
                (scene, util) -> {
                    scene.title("blueprint_usage", I18n.get("ponderer.guide.blueprint.title"));
                    scene.showBasePlate();

                    scene.addKeyframe();
                    scene.idle(20);

                    scene.overlay().showText(160)
                        .text(I18n.get("ponderer.guide.blueprint.intro"))
                        .pointAt(new Vec3(2.5, 1.0, 2.5))
                        .placeNearTarget();
                    scene.idle(170);

                    Vec3 firstPoint = new Vec3(1.5, 1.0, 1.5);
                    Vec3 secondPoint = new Vec3(3.5, 4.0, 3.5);
                    Vec3 resizePoint = new Vec3(2.5, 2.5, 1.5);
                    Selection firstSelection = util.select().fromTo(1, 1, 1, 3, 3, 3);
                    Selection resizedSelection = util.select().fromTo(1, 1, 2, 3, 3, 3);
                    ItemStack carrierStack = BlueprintFeature.getCarrierStack();

                    scene.overlay().showControls(firstPoint, Pointing.DOWN, 40)
                        .rightClick()
                        .withItem(carrierStack);
                    scene.addKeyframe();
                    scene.overlay().showText(40)
                        .text(I18n.get("ponderer.guide.blueprint.step1"))
                        .pointAt(firstPoint)
                        .placeNearTarget();
                    scene.idle(50);

                    scene.overlay().showControls(secondPoint, Pointing.UP, 40)
                        .rightClick()
                        .withItem(carrierStack);
                    scene.addKeyframe();
                    scene.overlay().showText(40)
                        .text(I18n.get("ponderer.guide.blueprint.step2"))
                        .pointAt(secondPoint)
                        .placeNearTarget();
                    scene.overlay().showOutline(PonderPalette.BLUE, new Object(), firstSelection, 50);
                    scene.idle(50);

                    scene.overlay().showControls(resizePoint, Pointing.RIGHT, 40)
                        .scroll()
                        .whileCTRL()
                        .withItem(carrierStack);
                    scene.addKeyframe();
                    scene.overlay().showText(40)
                        .text(I18n.get("ponderer.guide.blueprint.step3_resize"))
                        .pointAt(resizePoint)
                        .placeNearTarget();
                    scene.overlay().showOutline(PonderPalette.BLUE, new Object(), resizedSelection, 50);
                    scene.idle(50);

                    scene.overlay().showControls(resizePoint, Pointing.RIGHT, 40)
                        .rightClick()
                        .whileSneaking()
                        .withItem(carrierStack);
                    scene.addKeyframe();
                    scene.overlay().showText(40)
                        .text(I18n.get("ponderer.guide.blueprint.step5_discard"))
                        .pointAt(resizePoint)
                        .placeNearTarget();
                    scene.overlay().showOutline(PonderPalette.RED, new Object(), resizedSelection, 40);
                }, ResourceLocation.parse(PondererAttribution.TAG_ID));
    }
}
