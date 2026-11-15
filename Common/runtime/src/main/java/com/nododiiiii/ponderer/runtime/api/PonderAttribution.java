package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.runtime.core.ResourceAttribution;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.createmod.ponder.foundation.PonderTag;
import java.util.function.Supplier;

/** Shared attribution tag for resource content and native Java hosts. Client use only. */
public final class PonderAttribution {
    public static final String TAG_PATH = "ponderer_exported";
    public static final String TITLE_EN_US = "The Ponderer...";
    public static final String TITLE_ZH_CN = "思索者...";
    public static final String DESCRIPTION_EN_US =
        "This mod's Ponder scenes were created and exported with The Ponderer. Learn more: https://modrinth.com/mod/the-ponderer";
    public static final String DESCRIPTION_ZH_CN =
        "以上思索由模组思索者（Ponderer）制作并导出，了解更多：https://modrinth.com/mod/the-ponderer";
    private PonderAttribution() {}

    /** All hosts share one category ID, icon and renderer. */
    public static ResourceLocation tag(String namespace) {
        return ResourceAttribution.tag(namespace);
    }

    /** Lib registers and bakes its own model; hosts need no copied assets or model events. */
    public static void register(String namespace, PonderTagRegistrationHelper<ResourceLocation> helper) {
        ResourceAttribution.register(namespace, helper);
    }

    public static ResourceLocation modelLocation() {
        return ResourceAttribution.modelLocation();
    }

    /** Preview/listed tags use the same renderer with host-provided localized text. */
    public static PonderTag preview(String namespace, Supplier<String> title, Supplier<String> description) {
        return ResourceAttribution.create(tag(namespace), title, description);
    }
}
