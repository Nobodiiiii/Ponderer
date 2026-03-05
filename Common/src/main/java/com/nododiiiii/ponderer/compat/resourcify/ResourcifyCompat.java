package com.nododiiiii.ponderer.compat.resourcify;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * Integration with Resourcify mod for browsing Modrinth resource packs.
 * <p>
 * Uses a two-class pattern (like JEI compat) to avoid loading Resourcify classes
 * when the mod is not present.
 */
public final class ResourcifyCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Boolean loaded = null;

    private ResourcifyCompat() {}

    /**
     * Check if Resourcify mod is available at runtime.
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                Class.forName("dev.dediamondpro.resourcify.gui.browsepage.BrowseScreen");
                loaded = true;
            } catch (ClassNotFoundException e) {
                loaded = false;
            }
        }
        return loaded;
    }

    /**
     * Open Resourcify's BrowseScreen for resource packs with an initial search query.
     * If Resourcify is not installed, sends a chat message to the player.
     *
     * @param initialQuery the search text to auto-fill (e.g. "[Ponderer]")
     */
    public static void openBrowseScreen(String initialQuery) {
        if (!isLoaded()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                    Component.translatable("ponderer.ui.resourcify_not_installed"), false
                );
            }
            return;
        }
        try {
            ResourcifyBrowseHelper.open(initialQuery);
        } catch (Exception e) {
            LOGGER.error("Failed to open Resourcify browse screen", e);
        }
    }
}
