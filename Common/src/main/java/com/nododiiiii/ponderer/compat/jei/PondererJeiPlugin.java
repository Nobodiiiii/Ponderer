package com.nododiiiii.ponderer.compat.jei;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.ai.McmodApiClient;
import com.nododiiiii.ponderer.ui.AbstractStepEditorScreen;
import com.nododiiiii.ponderer.ui.AiGenerateScreen;
import com.nododiiiii.ponderer.ui.CommandParamScreen;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.JeiAwareScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.util.Locale;
import java.util.Optional;

@JeiPlugin
public class PondererJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static JeiAwareScreen activeScreen = null;
    @Nullable
    private static IdFieldMode activeMode = null;
    @Nullable
    private static IJeiRuntime runtime = null;

    private static boolean eventRegistered = false;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("ponderer", "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        activeScreen = null;
        activeMode = null;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // AbstractStepEditorScreen
        registration.addGuiScreenHandler(AbstractStepEditorScreen.class, screen -> {
            if (activeScreen != screen) return null;
            return new JeiAwareGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(
                AbstractStepEditorScreen.class,
                new JeiAwareGhostHandler<>()
        );

        // CommandParamScreen
        registration.addGuiScreenHandler(CommandParamScreen.class, screen -> {
            if (activeScreen != screen) return null;
            return new JeiAwareGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(
                CommandParamScreen.class,
                new JeiAwareGhostHandler<>()
        );

        // AiGenerateScreen
        registration.addGuiScreenHandler(AiGenerateScreen.class, screen -> {
            if (activeScreen != screen) return null;
            return new JeiAwareGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(
                AiGenerateScreen.class,
                new JeiAwareGhostHandler<>()
        );
    }

    // ---- State management (called from JeiCompat) ----

    static void setActiveEditor(AbstractStepEditorScreen screen, IdFieldMode mode) {
        activeScreen = screen;
        activeMode = mode;
    }

    static void setActiveScreen(JeiAwareScreen screen, IdFieldMode mode) {
        activeScreen = screen;
        activeMode = mode;
    }

    static void clearActiveEditor() {
        activeScreen = null;
        activeMode = null;
    }

    @Nullable
    static IJeiRuntime getRuntime() {
        return runtime;
    }

    @Nullable
    static IdFieldMode getActiveMode() {
        return activeMode;
    }

    @Nullable
    static JeiAwareScreen getActiveScreen() {
        return activeScreen;
    }

    // ---- Click interception (called from platform entry points) ----

    /**
     * Handle a mouse click on a JEI-aware screen.
     * Returns true if the click was consumed (should be canceled by the platform event handler).
     */
    public static boolean handleMouseClick(Screen screen, double mouseX, double mouseY, int button) {
        if (activeMode == null || runtime == null) return false;

        // Fabric can report screen click events with wrappers/indirections.
        // Prefer the explicitly activated editor screen when available.
        JeiAwareScreen aware;
        if (activeScreen != null) {
            aware = activeScreen;
        } else if (screen instanceof JeiAwareScreen s) {
            aware = s;
        } else {
            return false;
        }

        IIngredientListOverlay overlay = runtime.getIngredientListOverlay();
        IBookmarkOverlay bookmarks = runtime.getBookmarkOverlay();

        Optional<ITypedIngredient<?>> ingredient = overlay.getIngredientUnderMouse();
        if (ingredient.isEmpty()) {
            ingredient = bookmarks.getIngredientUnderMouse();
        }
        if (ingredient.isEmpty()) return false;

        // Other modes: only accept items
        Optional<ItemStack> stackOpt = ingredient.get().getItemStack();
        if (stackOpt.isEmpty()) {
            return true;
        }
        ItemStack stack = stackOpt.get();

        // INGREDIENT mode: accept any JEI ingredient type
        if (activeMode == IdFieldMode.INGREDIENT) {
            String id = JeiIngredientHelper.resolveId(ingredient.get());
            if (id != null) {
                HintableTextFieldWidget field = aware.getJeiTargetField();
                if (field != null) {
                    field.setValue(id);
                }
                aware.deactivateJei();
            } else {
                aware.showJeiIncompatibleWarning(activeMode);
            }
            return true;
        }

        String id = StepEditorGhostHandler.resolveId(stack, activeMode);
        if (id != null) {
            HintableTextFieldWidget field = aware.getJeiTargetField();
            if (field != null) {
                field.setValue(id);
            }

            // For AiGenerateScreen, automatically generate MCMod URL
            if (screen instanceof AiGenerateScreen aiScreen) {
                generateAndAddMcmodUrl(aiScreen, stack);
            }

            aware.deactivateJei();
        } else {
            aware.showJeiIncompatibleWarning(activeMode);
        }
        return true;
    }

    /**
     * Generate MCMod URL from item stack and add it to AiGenerateScreen
     */
    private static void generateAndAddMcmodUrl(AiGenerateScreen aiScreen, ItemStack stack) {
        try {
            // Get item registry name
            ResourceLocation registryName = BuiltInRegistries.ITEM
                    .getKey(stack.getItem());
            if (registryName != null) {
                // Call MCMod API to get item URL
                Optional<String> urlOptional = McmodApiClient.getItemUrl(registryName.toString());
                if (urlOptional.isPresent()) {
                    aiScreen.updateAutoUrl(urlOptional.get(), registryName.toString());
                } else {
                    // Remove existing auto-added URLs when no URL is found
                    aiScreen.updateAutoUrl(null, registryName.toString());
                }
            }
        } catch (Exception e) {
            // Log error and continue
            LOGGER.warn("Failed to generate and add MCMod URL: {}", e.getMessage());
        }
    }

    // ---- IGuiProperties implementation for any JeiAwareScreen ----

    private static class JeiAwareGuiProperties implements IGuiProperties {
        private final Screen screen;
        private final JeiAwareScreen aware;

        JeiAwareGuiProperties(JeiAwareScreen aware) {
            this.screen = (Screen) aware;
            this.aware = aware;
        }

        @Override
        public Class<? extends Screen> screenClass() { return screen.getClass(); }

        @Override
        public int guiLeft() { return aware.getGuiLeft(); }

        @Override
        public int guiTop() { return aware.getGuiTop(); }

        @Override
        public int guiXSize() { return aware.getGuiWidth(); }

        @Override
        public int guiYSize() { return aware.getGuiHeight(); }

        @Override
        public int screenWidth() { return screen.width; }

        @Override
        public int screenHeight() { return screen.height; }
    }
}
