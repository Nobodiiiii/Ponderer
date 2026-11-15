package com.nododiiiii.ponderer.product.catalog;

import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.runtime.api.PonderAttribution;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** The mandatory category shared by runtime scenes and standalone Java exports. */
public final class PondererAttribution {
    public static final String ATTRIBUTION_TAG_PATH = PonderAttribution.TAG_PATH;
    public static final String TAG_ID = "ponderer:" + ATTRIBUTION_TAG_PATH;
    public static final String ICON_TEXTURE_PATH = "textures/block/ponderer_observer_front.png";
    public static final String ICON_MODEL_PATH = "block/ponderer_observer";
    public static final String ICON_MODEL_RESOURCE_PATH = "models/" + ICON_MODEL_PATH + ".json";
    public static final String ATTRIBUTION_TITLE_EN_US = PonderAttribution.TITLE_EN_US;
    public static final String ATTRIBUTION_TITLE_ZH_CN = PonderAttribution.TITLE_ZH_CN;
    public static final String ATTRIBUTION_DESCRIPTION_EN_US =
        PonderAttribution.DESCRIPTION_EN_US;
    public static final String ATTRIBUTION_DESCRIPTION_ZH_CN =
        PonderAttribution.DESCRIPTION_ZH_CN;

    private PondererAttribution() {
    }

    /** Called during client initialization, before Flywheel collects additional models. */
    public static ResourceLocation iconModelLocation() {
        return PonderAttribution.modelLocation();
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
        return PonderAttribution.preview("ponderer", title::resolve, description::resolve);
    }

    public static void register() {
        var registry = (PonderTagRegistry) PonderIndex.getTagAccess();
        PonderTag tag = preview();
        registry.registerTag(tag);
        registry.listTag(tag);
    }
}
