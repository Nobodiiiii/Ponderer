package com.nododiiiii.ponderer.compat.resourcify;

import com.mojang.logging.LogUtils;
import dev.dediamondpro.resourcify.gui.browsepage.BrowseScreen;
import dev.dediamondpro.resourcify.services.ProjectType;
import dev.dediamondpro.resourcify.services.ServiceRegistry;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Helper that directly references Resourcify classes.
 * <p>
 * This class must ONLY be loaded after confirming Resourcify is present
 * (via {@link ResourcifyCompat#isLoaded()}), otherwise {@link NoClassDefFoundError} will occur.
 */
final class ResourcifyBrowseHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ResourcifyBrowseHelper() {}

    /**
     * Open Resourcify's resource pack browse screen with an initial search query.
     *
     * @param initialQuery the text to pre-fill in the search box (e.g. "[Ponderer]")
     */
    static void open(String initialQuery) {
        File resourcepacksDir = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("resourcepacks").toFile();

        BrowseScreen screen = new BrowseScreen(
            ProjectType.RESOURCE_PACK,
            resourcepacksDir,
            ServiceRegistry.INSTANCE.getDefaultService(ProjectType.RESOURCE_PACK)
        );

        // Set initial search query via reflection (searchBox is private in BrowseScreen)
        if (initialQuery != null && !initialQuery.isEmpty()) {
            try {
                Field searchBoxField = BrowseScreen.class.getDeclaredField("searchBox");
                searchBoxField.setAccessible(true);
                Object searchBox = searchBoxField.get(screen);
                if (searchBox != null) {
                    // UITextInput.setText(String) — triggers onUpdate callback which auto-fires loadPacks()
                    Method setText = searchBox.getClass().getMethod("setText", String.class);
                    setText.invoke(searchBox, initialQuery);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to set initial search query in Resourcify BrowseScreen", e);
            }
        }

        Minecraft.getInstance().setScreen(screen);
    }
}
