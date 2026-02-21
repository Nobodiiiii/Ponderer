package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.ui.AbstractStepEditorScreen;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;
import java.util.Optional;

@JeiPlugin
public class PondererJeiPlugin implements IModPlugin {

    @Nullable
    private static Screen activeScreen = null;
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
        if (!eventRegistered) {
            eventRegistered = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PondererJeiPlugin::onMouseClick);
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
            return new PondererGuiProperties((JeiAwareScreen) screen, screen);
        });

        registration.addGuiScreenHandler(CommandParamScreen.class, screen -> {
            if (activeScreen != screen) return null;
            return new PondererGuiProperties((JeiAwareScreen) screen, screen);
        });

        registration.addGhostIngredientHandler(
                AbstractStepEditorScreen.class,
                new StepEditorGhostHandler<>()
        );

        registration.addGhostIngredientHandler(
                CommandParamScreen.class,
                new StepEditorGhostHandler<>()
        );
    }

    // ---- State management (called from JeiCompat) ----

    static void setActiveEditor(AbstractStepEditorScreen screen, IdFieldMode mode) {
        activeScreen = screen;
        activeMode = mode;
    }

    static void setActiveScreen(Screen screen, IdFieldMode mode) {
        activeScreen = screen;
        activeMode = mode;
    }

    static void clearActiveEditor() {
        activeScreen = null;
        activeMode = null;
    }

    @Nullable
    static IdFieldMode getActiveMode() {
        return activeMode;
    }

    @Nullable
    static Screen getActiveScreen() {
        return activeScreen;
    }

    // ---- Click interception ----

    private static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof JeiAwareScreen aware)) return;
        if (activeMode == null || runtime == null) return;
        if (activeScreen != screen) return;

        IIngredientListOverlay overlay = runtime.getIngredientListOverlay();
        IBookmarkOverlay bookmarks = runtime.getBookmarkOverlay();

        Optional<ITypedIngredient<?>> ingredient = overlay.getIngredientUnderMouse();
        if (ingredient.isEmpty()) {
            ingredient = bookmarks.getIngredientUnderMouse();
        }
        if (ingredient.isEmpty()) return;

        Optional<ItemStack> stackOpt = ingredient.get().getItemStack();
        if (stackOpt.isEmpty()) {
            event.setCanceled(true);
            return;
        }
        ItemStack stack = stackOpt.get();

        String id = StepEditorGhostHandler.resolveId(stack, activeMode);
        if (id != null) {
            HintableTextFieldWidget field = aware.getJeiTargetField();
            if (field != null) {
                field.setValue(id);
            }
            aware.deactivateJei();
            event.setCanceled(true);
        } else {
            aware.showJeiIncompatibleWarning(activeMode);
            event.setCanceled(true);
        }
    }

    // ---- IGuiProperties implementation ----

    private static class PondererGuiProperties implements IGuiProperties {
        private final JeiAwareScreen aware;
        private final Screen screen;

        PondererGuiProperties(JeiAwareScreen aware, Screen screen) {
            this.aware = aware;
            this.screen = screen;
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
