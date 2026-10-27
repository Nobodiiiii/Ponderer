package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.scene.LocalizedText;
import net.minecraft.client.Minecraft;

/** Product adapter for language-sensitive convenience methods on scene text. */
public final class ClientSceneLanguage {
    private ClientSceneLanguage() {}

    public static void install() {
        LocalizedText.useLanguage(() -> Minecraft.getInstance().getLanguageManager().getSelected());
    }
}
