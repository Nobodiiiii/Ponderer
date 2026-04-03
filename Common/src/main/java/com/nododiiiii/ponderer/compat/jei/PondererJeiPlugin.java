package com.nododiiiii.ponderer.compat.jei;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.ai.McmodApiClient;
import com.nododiiiii.ponderer.ui.AbstractStepEditorScreen;
import com.nododiiiii.ponderer.ui.AiGenerateScreen;
import com.nododiiiii.ponderer.ui.CommandParamScreen;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.JeiAwareScreen;
import com.nododiiiii.ponderer.ui.UiAnchorViewport;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
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
        return new ResourceLocation("ponderer", "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        if (!eventRegistered) {
            eventRegistered = true;
            // Platform-specific event registration is done in Forge/Fabric modules
            // via PondererJeiPlugin.handleMouseClick(Screen, double, double, int)
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        activeScreen = null;
        activeMode = null;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(AbstractStepEditorScreen.class, screen -> {
            if (activeScreen != screen) return null;
            return new JeiAwareGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(
                AbstractStepEditorScreen.class,
                new JeiAwareGhostHandler<>()
        );

        registration.addGuiScreenHandler(CommandParamScreen.class, screen -> {
            if (activeScreen != screen) return null;
            return new JeiAwareGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(
                CommandParamScreen.class,
                new JeiAwareGhostHandler<>()
        );

        registration.addGuiScreenHandler(AiGenerateScreen.class, screen -> {
            if (activeScreen != screen) return null;
            return new JeiAwareGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(
                AiGenerateScreen.class,
                new JeiAwareGhostHandler<>()
        );

        registration.addGuiScreenHandler(PonderUI.class, screen -> {
            if (!InterfaceSlotEditState.isActive() || !InterfaceSlotEditState.hasJeiViewport()) {
                return null;
            }
            return new PonderUiGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(
                PonderUI.class,
                new PonderUiGhostHandler()
        );
    }

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

    /**
     * Handle a mouse click on a JeiAwareScreen. Returns true if the event should be cancelled.
     * Called by platform-specific screen event handlers (Forge ScreenEvent / Fabric ScreenEvents).
     */
    public static boolean handleMouseClick(Screen screen, double mouseX, double mouseY, int button) {
        if (activeMode == null || runtime == null) return false;

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

        Optional<ItemStack> stackOpt = ingredient.get().getItemStack();
        if (stackOpt.isEmpty()) {
            return true;
        }
        ItemStack stack = stackOpt.get();

        if (activeMode == IdFieldMode.INGREDIENT) {
            String id = JeiIngredientHelper.resolveId(ingredient.get());
            if (id != null) {
                HintableTextFieldWidget field = aware.getJeiTargetField();
                if (field != null) {
                    field.setValue(id);
                }
                aware.deactivateJei();
                return true;
            } else {
                aware.showJeiIncompatibleWarning(activeMode);
                return true;
            }
        }

        String id = StepEditorGhostHandler.resolveId(stack, activeMode);
        if (id != null) {
            HintableTextFieldWidget field = aware.getJeiTargetField();
            if (field != null) {
                field.setValue(id);
            }

            if (screen instanceof AiGenerateScreen aiScreen) {
                generateAndAddMcmodUrl(aiScreen, stack);
            }

            aware.deactivateJei();
            return true;
        } else {
            aware.showJeiIncompatibleWarning(activeMode);
            return true;
        }
    }

    private static void generateAndAddMcmodUrl(AiGenerateScreen aiScreen, ItemStack stack) {
        try {
            ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (registryName != null) {
                Optional<String> urlOptional = McmodApiClient.getItemUrl(registryName.toString());
                if (urlOptional.isPresent()) {
                    aiScreen.updateAutoUrl(urlOptional.get(), registryName.toString());
                } else {
                    aiScreen.updateAutoUrl(null, registryName.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to generate and add MCMod URL: {}", e.getMessage());
        }
    }

    private static class JeiAwareGuiProperties implements IGuiProperties {
        private final Screen screen;
        private final JeiAwareScreen aware;

        JeiAwareGuiProperties(JeiAwareScreen aware) {
            this.screen = (Screen) aware;
            this.aware = aware;
        }

        @Override
        public Class<? extends Screen> getScreenClass() { return screen.getClass(); }

        @Override
        public int getGuiLeft() { return aware.getGuiLeft(); }

        @Override
        public int getGuiTop() { return aware.getGuiTop(); }

        @Override
        public int getGuiXSize() { return aware.getGuiWidth(); }

        @Override
        public int getGuiYSize() { return aware.getGuiHeight(); }

        @Override
        public int getScreenWidth() { return screen.width; }

        @Override
        public int getScreenHeight() { return screen.height; }
    }

    private static class ViewportGuiProperties implements IGuiProperties {
        private final Screen screen;
        private final UiAnchorViewport.Rect viewport;

        private ViewportGuiProperties(Screen screen, UiAnchorViewport.Rect viewport) {
            this.screen = screen;
            this.viewport = viewport;
        }

        @Override
        public Class<? extends Screen> getScreenClass() { return screen.getClass(); }

        @Override
        public int getGuiLeft() {
            return (int) viewport.left();
        }

        @Override
        public int getGuiTop() {
            return (int) viewport.top();
        }

        @Override
        public int getGuiXSize() {
            return (int) viewport.width();
        }

        @Override
        public int getGuiYSize() {
            return (int) viewport.height();
        }

        @Override
        public int getScreenWidth() { return screen.width; }

        @Override
        public int getScreenHeight() { return screen.height; }
    }

    private static class PonderUiGuiProperties extends ViewportGuiProperties {
        private PonderUiGuiProperties(PonderUI screen) {
            super(screen, InterfaceSlotEditState.getJeiViewport());
        }
    }
}
