package com.nododiiiii.ponderer.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.ai.McmodApiClient;
import com.nododiiiii.ponderer.ui.AbstractStepEditorScreen;
import com.nododiiiii.ponderer.ui.AiGenerateScreen;
import com.nododiiiii.ponderer.ui.CommandParamScreen;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.JeiAwareScreen;
import com.nododiiiii.ponderer.ui.PonderRuntimeZLayers;
import com.nododiiiii.ponderer.ui.PonderUiInteractionHelper;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Set;
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
            if (!InterfaceSlotEditState.isActive()) {
                return null;
            }
            UiAnchorViewport.Rect viewport = resolvePonderUiViewport();
            if (viewport == null) {
                return null;
            }
            return new PonderUiGuiProperties(screen, viewport);
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

    static boolean shouldRenderPonderUiOverlayManually(Screen screen) {
        return runtime != null
            && screen instanceof PonderUI
            && InterfaceSlotEditState.isActive()
            && resolvePonderUiViewport() != null;
    }

    static void renderPonderUiOverlay(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!shouldRenderPonderUiOverlayManually(screen)) {
            return;
        }

        boolean pushed = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            syncPonderUiOverlayState(screen);

            graphics.flush();
            graphics.pose().pushPose();
            pushed = true;
            graphics.pose().translate(0, 0, PonderRuntimeZLayers.JEI_OVERLAY_LAYER);
            RenderSystem.disableDepthTest();
            invokeDrawOnForeground(runtime.getBookmarkOverlay(), graphics, mouseX, mouseY);
            invokeDrawOnForeground(runtime.getIngredientListOverlay(), graphics, mouseX, mouseY);
            invokeDrawScreen(runtime.getIngredientListOverlay(), mc, graphics, mouseX, mouseY, partialTicks);
            invokeDrawScreen(runtime.getBookmarkOverlay(), mc, graphics, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            LOGGER.debug("[jei] ponder ui overlay render failed: {}", t.toString());
        } finally {
            if (pushed) {
                graphics.pose().popPose();
            }
            graphics.flush();
        }
    }

    static void renderPonderUiTooltips(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shouldRenderPonderUiOverlayManually(screen)) {
            return;
        }

        boolean pushed = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            syncPonderUiOverlayState(screen);

            graphics.flush();
            graphics.pose().pushPose();
            pushed = true;
            graphics.pose().translate(0, 0, PonderRuntimeZLayers.TOOLTIP_LAYER);
            RenderSystem.disableDepthTest();
            invokeDrawTooltips(runtime.getIngredientListOverlay(), mc, graphics, mouseX, mouseY);
            invokeDrawTooltips(runtime.getBookmarkOverlay(), mc, graphics, mouseX, mouseY);
        } catch (Throwable t) {
            LOGGER.debug("[jei] ponder ui tooltip render failed: {}", t.toString());
        } finally {
            if (pushed) {
                graphics.pose().popPose();
            }
            graphics.flush();
        }
    }

    /**
     * Handle a mouse click on a JeiAwareScreen. Returns true if the event should be cancelled.
     * Called by platform-specific screen event handlers (Forge ScreenEvent / Fabric ScreenEvents).
     */
    public static boolean handleMouseClick(Screen screen, double mouseX, double mouseY, int button) {
        if (activeMode == null || runtime == null) return false;
        if (screen instanceof PonderUI && PonderUiInteractionHelper.hasPriorityPonderButtonAt(screen, mouseX, mouseY)) {
            return false;
        }

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

    private static void syncOverlayState(Object overlay, Screen screen) throws Exception {
        Method getUpdater = overlay.getClass().getMethod("getScreenPropertiesUpdater");
        Object updater = getUpdater.invoke(overlay);
        updater.getClass().getMethod("updateScreen", Screen.class).invoke(updater, screen);
        updater.getClass().getMethod("updateExclusionAreas", Set.class).invoke(updater, Set.of());
        updater.getClass().getMethod("update").invoke(updater);
    }

    private static void syncPonderUiOverlayState(Screen screen) throws Exception {
        syncOverlayState(runtime.getIngredientListOverlay(), screen);
        syncOverlayState(runtime.getBookmarkOverlay(), screen);
    }

    private static void invokeDrawOnForeground(Object overlay, GuiGraphics graphics, int mouseX, int mouseY) throws Exception {
        overlay.getClass()
            .getMethod("drawOnForeground", GuiGraphics.class, int.class, int.class)
            .invoke(overlay, graphics, mouseX, mouseY);
    }

    private static void invokeDrawScreen(Object overlay, Minecraft mc, GuiGraphics graphics,
                                         int mouseX, int mouseY, float partialTicks) throws Exception {
        overlay.getClass()
            .getMethod("drawScreen", Minecraft.class, GuiGraphics.class, int.class, int.class, float.class)
            .invoke(overlay, mc, graphics, mouseX, mouseY, partialTicks);
    }

    private static void invokeDrawTooltips(Object overlay, Minecraft mc, GuiGraphics graphics,
                                           int mouseX, int mouseY) throws Exception {
        overlay.getClass()
            .getMethod("drawTooltips", Minecraft.class, GuiGraphics.class, int.class, int.class)
            .invoke(overlay, mc, graphics, mouseX, mouseY);
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
        private PonderUiGuiProperties(PonderUI screen, UiAnchorViewport.Rect viewport) {
            super(screen, viewport);
        }
    }

    @Nullable
    private static UiAnchorViewport.Rect resolvePonderUiViewport() {
        Screen mirror = UiAnchorViewport.getEmbeddedMirrorScreen();
        if (mirror != null) {
            UiAnchorViewport.Rect liveViewport = UiAnchorViewport.resolveJeiForScreen(Minecraft.getInstance(), mirror);
            if (liveViewport.isValid()) {
                return liveViewport;
            }
        }

        UiAnchorViewport.Rect capturedViewport = InterfaceSlotEditState.getJeiViewport();
        if (capturedViewport != null && capturedViewport.isValid()) {
            return capturedViewport;
        }

        return null;
    }
}
