package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.scene.LocalizedText;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** The mandatory category shared by runtime scenes and standalone Java exports. */
public final class PondererAttribution {
    private static final int ICON_ROTATION_TICKS = 20 * 30;
    public static final String ATTRIBUTION_TAG_PATH = "ponderer_exported";
    public static final String TAG_ID = "ponderer:" + ATTRIBUTION_TAG_PATH;
    public static final String ICON_TEXTURE_PATH = "textures/block/ponderer_observer_front.png";
    public static final String ICON_MODEL_PATH = "block/ponderer_observer";
    public static final String ICON_MODEL_RESOURCE_PATH = "models/" + ICON_MODEL_PATH + ".json";
    public static final String ATTRIBUTION_TITLE_EN_US = "The Ponderer...";
    public static final String ATTRIBUTION_TITLE_ZH_CN = "思索者...";
    public static final String ATTRIBUTION_DESCRIPTION_EN_US =
        "This mod's Ponder scenes were created and exported with The Ponderer. Learn more: https://modrinth.com/mod/the-ponderer";
    public static final String ATTRIBUTION_DESCRIPTION_ZH_CN =
        "以上思索由模组思索者（Ponderer）制作并导出，了解更多：https://modrinth.com/mod/the-ponderer";

    private PondererAttribution() {
    }

    /** Called during client initialization, before Flywheel collects additional models. */
    public static ResourceLocation iconModelLocation() {
        return Icon.MODEL.modelLocation();
    }

    private static final class Icon {
        private static final PartialModel MODEL = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath("ponderer", ICON_MODEL_PATH));
    }

    public static boolean isTag(@Nullable String id) {
        return TAG_ID.equals(id);
    }

    /** Add the mandatory category when registering native Ponder scenes. */
    public static List<String> withTag(@Nullable List<String> tags) {
        var result = new LinkedHashSet<String>();
        if (tags != null) {
            for (String id : tags) {
                if (id != null) {
                    result.add(id);
                }
            }
        }
        result.add(TAG_ID);
        return new ArrayList<>(result);
    }

    public static PonderTag preview() {
        var title = LocalizedText.ofMap(Map.of(
            "en_us", ATTRIBUTION_TITLE_EN_US, "zh_cn", ATTRIBUTION_TITLE_ZH_CN));
        var description = LocalizedText.ofMap(Map.of(
            "en_us", ATTRIBUTION_DESCRIPTION_EN_US, "zh_cn", ATTRIBUTION_DESCRIPTION_ZH_CN));
        return new PonderTag(ResourceLocation.parse(TAG_ID), null, ItemStack.EMPTY, ItemStack.EMPTY) {
            @Override
            public String getTitle() {
                return title.resolve();
            }

            @Override
            public String getDescription() {
                return description.resolve();
            }

            @Override
            public void render(GuiGraphics graphics, int x, int y) {
                // UI time keeps the icon rotating while scene playback or the game is paused.
                double ticks = AnimationTickHolder.getTicks(true) % ICON_ROTATION_TICKS
                    + AnimationTickHolder.getPartialTicksUI();
                GuiGameElement.of(Icon.MODEL)
                    .rotateBlock(30, 135 + ticks * 360 / ICON_ROTATION_TICKS, 0)
                    .scale(10)
                    .at(x + 3, y + 13, 100)
                    .render(graphics);
                // Model batch cleanup disables depth testing; following native widgets expect it enabled.
                RenderSystem.enableDepthTest();
            }
        };
    }

    public static void register() {
        var registry = (PonderTagRegistry) PonderIndex.getTagAccess();
        PonderTag tag = preview();
        registry.registerTag(tag);
        registry.listTag(tag);
    }
}
