package com.nododiiiii.ponderer.compat.jei;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.ai.McmodApiClient;
import com.nododiiiii.ponderer.ui.AbstractStepEditorScreen;
import com.nododiiiii.ponderer.ui.AiGenerateScreen;
import com.nododiiiii.ponderer.ui.CommandParamScreen;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.UiAnchorViewport;
import com.nododiiiii.ponderer.ui.JeiAwareScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.util.ArrayList;
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
    @Nullable
    private static ActiveGhostDrag activeGhostDrag = null;

    private static boolean eventRegistered = false;
    private static final double GHOST_DRAG_MIN_DISTANCE_SQ = 64.0;

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

        registration.addGuiScreenHandler(PonderUI.class, screen -> {
            if (!InterfaceSlotEditState.isActive()) {
                return null;
            }
            return new PonderUiGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(
                AbstractContainerScreen.class,
                new InterfaceSlotGhostHandler<>()
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

    static boolean beginGhostIngredientDrag(Screen targetScreen, double mouseX, double mouseY) {
        if (runtime == null) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.containerMenu.getCarried().isEmpty()) {
            return false;
        }

        Optional<ITypedIngredient<?>> ingredient = runtime.getIngredientListOverlay().getIngredientUnderMouse();
        if (ingredient.isEmpty()) {
            ingredient = runtime.getBookmarkOverlay().getIngredientUnderMouse();
        }
        if (ingredient.isEmpty()) {
            return false;
        }

        var handlers = runtime.getScreenHelper().getGhostIngredientHandlers(targetScreen);
        if (handlers.isEmpty()) {
            return false;
        }

        ArrayList<GhostTargetGroup> targetGroups = new ArrayList<>();
        for (IGhostIngredientHandler<Screen> handler : handlers) {
            var targets = getTargetsForIngredient(handler, targetScreen, ingredient.get());
            if (!targets.isEmpty()) {
                targetGroups.add(new GhostTargetGroup(handler, targets));
            }
        }

        if (targetGroups.isEmpty()) {
            return false;
        }

        cancelGhostIngredientDrag();
        activeGhostDrag = new ActiveGhostDrag(ingredient.get(), mouseX, mouseY, targetGroups);
        return true;
    }

    static boolean completeGhostIngredientDrag(Screen targetScreen, double mouseX, double mouseY) {
        ActiveGhostDrag drag = activeGhostDrag;
        if (drag == null) {
            return false;
        }
        activeGhostDrag = null;

        double dx = mouseX - drag.startMouseX();
        double dy = mouseY - drag.startMouseY();
        if (dx * dx + dy * dy <= GHOST_DRAG_MIN_DISTANCE_SQ) {
            notifyGhostDragComplete(drag.targetGroups());
            return false;
        }

        for (GhostTargetGroup group : drag.targetGroups()) {
            for (IGhostIngredientHandler.Target<?> target : group.targets()) {
                if (contains(target.getArea(), mouseX, mouseY)) {
                    acceptTarget(target, drag.ingredient());
                    group.handler().onComplete();
                    return true;
                }
            }
            group.handler().onComplete();
        }

        return false;
    }

    static void cancelGhostIngredientDrag() {
        ActiveGhostDrag drag = activeGhostDrag;
        if (drag == null) {
            return;
        }

        activeGhostDrag = null;
        notifyGhostDragComplete(drag.targetGroups());
    }

    private static void notifyGhostDragComplete(java.util.List<GhostTargetGroup> groups) {
        for (GhostTargetGroup group : groups) {
            group.handler().onComplete();
        }
    }

    private static <I> java.util.List<IGhostIngredientHandler.Target<?>> getTargetsForIngredient(
            IGhostIngredientHandler<Screen> handler,
            Screen targetScreen,
            ITypedIngredient<I> ingredient
    ) {
        java.util.List<IGhostIngredientHandler.Target<I>> typedTargets = handler.getTargetsTyped(targetScreen, ingredient, true);
        if (typedTargets.isEmpty()) {
            return java.util.List.of();
        }
        return new ArrayList<>(typedTargets);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void acceptTarget(IGhostIngredientHandler.Target<?> target, ITypedIngredient<?> ingredient) {
        ((IGhostIngredientHandler.Target) target).accept(ingredient.getIngredient());
    }

    private static boolean contains(Rect2i area, double mouseX, double mouseY) {
        return mouseX >= area.getX()
                && mouseY >= area.getY()
                && mouseX < area.getX() + area.getWidth()
                && mouseY < area.getY() + area.getHeight();
    }

    private record GhostTargetGroup(
            IGhostIngredientHandler<Screen> handler,
            java.util.List<IGhostIngredientHandler.Target<?>> targets
    ) {
    }

    private record ActiveGhostDrag(
            ITypedIngredient<?> ingredient,
            double startMouseX,
            double startMouseY,
            java.util.List<GhostTargetGroup> targetGroups
    ) {
    }

    // ---- Click interception (called from platform event handlers) ----

    /**
     * Handle a mouse click on a JeiAwareScreen. Returns true if the event should be cancelled.
     * Called by platform-specific screen event handlers (Forge ScreenEvent / Fabric ScreenEvents).
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

            // For AiGenerateScreen, automatically generate MCMod URL
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

    private static class PonderUiGuiProperties implements IGuiProperties {
        private final PonderUI screen;

        private PonderUiGuiProperties(PonderUI screen) {
            this.screen = screen;
        }

        @Override
        public Class<? extends Screen> getScreenClass() { return screen.getClass(); }

        @Override
        public int getGuiLeft() {
            return (int) UiAnchorViewport.resolve(Minecraft.getInstance()).left();
        }

        @Override
        public int getGuiTop() {
            return (int) UiAnchorViewport.resolve(Minecraft.getInstance()).top();
        }

        @Override
        public int getGuiXSize() {
            return (int) UiAnchorViewport.resolve(Minecraft.getInstance()).width();
        }

        @Override
        public int getGuiYSize() {
            return (int) UiAnchorViewport.resolve(Minecraft.getInstance()).height();
        }

        @Override
        public int getScreenWidth() { return screen.width; }

        @Override
        public int getScreenHeight() { return screen.height; }
    }
}
