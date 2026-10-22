package com.nododiiiii.ponderer.ponder;

import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** The mandatory category shared by runtime scenes and standalone Java exports. */
public final class PondererAttribution {
    public static final String ATTRIBUTION_TAG_PATH = "ponderer_exported";
    public static final String TAG_ID = "ponderer:" + ATTRIBUTION_TAG_PATH;
    public static final String ICON_TEXTURE_PATH = "textures/ponder/tag/" + ATTRIBUTION_TAG_PATH + ".png";
    public static final String ATTRIBUTION_TITLE_EN_US = "The Ponderer...";
    public static final String ATTRIBUTION_TITLE_ZH_CN = "思索者...";
    public static final String ATTRIBUTION_DESCRIPTION_EN_US =
        "This mod's Ponder scenes were created and exported with The Ponderer. Learn more: https://modrinth.com/mod/the-ponderer";
    public static final String ATTRIBUTION_DESCRIPTION_ZH_CN =
        "以上思索由模组思索者（Ponderer）制作并导出，了解更多：https://modrinth.com/mod/the-ponderer";

    private PondererAttribution() {
    }

    public static boolean isTag(@Nullable String id) {
        return TAG_ID.equals(id);
    }

    /** Apply at use sites so older scenes and manually edited JSON also retain attribution. */
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
        return new PonderTag(ResourceLocation.parse(TAG_ID),
            ResourceLocation.fromNamespaceAndPath("ponderer", ICON_TEXTURE_PATH),
            ItemStack.EMPTY, Items.OBSERVER.getDefaultInstance()) {
            @Override
            public String getTitle() {
                return title.resolve();
            }

            @Override
            public String getDescription() {
                return description.resolve();
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
