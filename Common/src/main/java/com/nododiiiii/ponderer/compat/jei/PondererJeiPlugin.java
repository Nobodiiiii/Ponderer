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
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.ui.UiAnchorViewport;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
    private static boolean overlayRenderFailureLogged = false;

    private static final ClassValue<OverlayMethods> OVERLAY_METHODS = new ClassValue<>() {
        @Override
        protected OverlayMethods computeValue(Class<?> type) {
            return new OverlayMethods(type);
        }
    };

    private static final ClassValue<UpdaterMethods> UPDATER_METHODS = new ClassValue<>() {
        @Override
        protected UpdaterMethods computeValue(Class<?> type) {
            return new UpdaterMethods(type);
        }
    };

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("ponderer", "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        overlayRenderFailureLogged = false;
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
        overlayRenderFailureLogged = false;
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

        registration.addGuiContainerHandler(ProjectorConfigScreen.class, new ProjectorGuiContainerHandler());

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
            drawPonderUiOverlays(mc, graphics, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            logOverlayRenderFailure("overlay", t);
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
            logOverlayRenderFailure("tooltip", t);
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
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (registryName == null) {
            return;
        }
        String itemId = registryName.toString();
        CompletableFuture
            .supplyAsync(() -> McmodApiClient.getItemUrl(itemId))
            .exceptionally(error -> {
                LOGGER.warn("Failed to generate and add MCMod URL: {}", error.getMessage());
                return Optional.empty();
            })
            .thenAcceptAsync(urlOptional -> aiScreen.updateAutoUrl(urlOptional.orElse(null), itemId),
                Minecraft.getInstance());
    }

    private static void syncOverlayState(Object overlay, Screen screen, IGuiProperties guiProperties) throws Exception {
        OverlayMethods overlayMethods = OVERLAY_METHODS.get(overlay.getClass());
        Object updater = overlayMethods.getScreenPropertiesUpdater.invoke(overlay);
        UPDATER_METHODS.get(updater.getClass()).update(updater, screen, guiProperties);
    }

    private static void syncPonderUiOverlayState(Screen screen) throws Exception {
        UiAnchorViewport.Rect viewport = resolvePonderUiViewport();
        if (!(screen instanceof PonderUI ponderUi) || viewport == null) {
            throw new IllegalStateException("PonderUI JEI overlay state is unavailable");
        }

        IGuiProperties guiProperties = new PonderUiGuiProperties(ponderUi, viewport);
        syncOverlayState(runtime.getIngredientListOverlay(), screen, guiProperties);
        syncOverlayState(runtime.getBookmarkOverlay(), screen, guiProperties);
    }

    private static void drawPonderUiOverlays(Minecraft mc, GuiGraphics graphics,
                                              int mouseX, int mouseY, float partialTicks) throws Exception {
        Object ingredientOverlay = runtime.getIngredientListOverlay();
        Object bookmarkOverlay = runtime.getBookmarkOverlay();
        OverlayMethods ingredientMethods = OVERLAY_METHODS.get(ingredientOverlay.getClass());
        OverlayMethods bookmarkMethods = OVERLAY_METHODS.get(bookmarkOverlay.getClass());

        if (ingredientMethods.hasSplitDrawPass() && bookmarkMethods.hasSplitDrawPass()) {
            ingredientMethods.drawBackground.invoke(ingredientOverlay, graphics);
            bookmarkMethods.drawBackground.invoke(bookmarkOverlay, graphics);
            bookmarkMethods.drawOnForeground.invoke(bookmarkOverlay, graphics, mouseX, mouseY);
            ingredientMethods.drawOnForeground.invoke(ingredientOverlay, graphics, mouseX, mouseY);
            ingredientMethods.drawForeground.invoke(
                ingredientOverlay, mc, graphics, mouseX, mouseY, partialTicks
            );
            bookmarkMethods.drawForeground.invoke(
                bookmarkOverlay, mc, graphics, mouseX, mouseY, partialTicks
            );
            return;
        }

        bookmarkMethods.drawOnForeground.invoke(bookmarkOverlay, graphics, mouseX, mouseY);
        ingredientMethods.drawOnForeground.invoke(ingredientOverlay, graphics, mouseX, mouseY);
        ingredientMethods.drawScreen.invoke(ingredientOverlay, mc, graphics, mouseX, mouseY, partialTicks);
        bookmarkMethods.drawScreen.invoke(bookmarkOverlay, mc, graphics, mouseX, mouseY, partialTicks);
    }

    private static void invokeDrawTooltips(Object overlay, Minecraft mc, GuiGraphics graphics,
                                           int mouseX, int mouseY) throws Exception {
        OVERLAY_METHODS.get(overlay.getClass()).drawTooltips
            .invoke(overlay, mc, graphics, mouseX, mouseY);
    }

    private static void logOverlayRenderFailure(String phase, Throwable error) {
        if (overlayRenderFailureLogged) {
            return;
        }
        overlayRenderFailureLogged = true;
        LOGGER.warn("[jei] PonderUI {} render failed; disabling repeated error reports", phase, error);
    }

    private static Method optionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static final class OverlayMethods {
        private final Method getScreenPropertiesUpdater;
        private final Method drawOnForeground;
        private final Method drawScreen;
        @Nullable
        private final Method drawBackground;
        @Nullable
        private final Method drawForeground;
        private final Method drawTooltips;

        private OverlayMethods(Class<?> type) {
            try {
                getScreenPropertiesUpdater = type.getMethod("getScreenPropertiesUpdater");
                drawOnForeground = type.getMethod(
                    "drawOnForeground", GuiGraphics.class, int.class, int.class
                );
                drawScreen = type.getMethod(
                    "drawScreen", Minecraft.class, GuiGraphics.class, int.class, int.class, float.class
                );
                drawTooltips = type.getMethod(
                    "drawTooltips", Minecraft.class, GuiGraphics.class, int.class, int.class
                );
                drawBackground = optionalMethod(type, "drawBackground", GuiGraphics.class);
                drawForeground = optionalMethod(
                    type, "drawForeground", Minecraft.class, GuiGraphics.class, int.class, int.class, float.class
                );
            } catch (NoSuchMethodException error) {
                throw new IllegalStateException("Unsupported JEI overlay implementation: " + type.getName(), error);
            }
        }

        private boolean hasSplitDrawPass() {
            return drawBackground != null && drawForeground != null;
        }
    }

    private static final class UpdaterMethods {
        private final Method updateScreen;
        @Nullable
        private final Method updateGuiProperties;
        private final Method updateExclusionAreas;
        private final Method update;

        private UpdaterMethods(Class<?> type) {
            try {
                updateScreen = type.getMethod("updateScreen", Screen.class);
                updateGuiProperties = optionalMethod(type, "updateGuiProperties", IGuiProperties.class);
                updateExclusionAreas = type.getMethod("updateExclusionAreas", Set.class);
                update = type.getMethod("update");
            } catch (NoSuchMethodException error) {
                throw new IllegalStateException("Unsupported JEI screen updater: " + type.getName(), error);
            }
        }

        private void update(Object updater, Screen screen, IGuiProperties guiProperties) throws Exception {
            updateScreen.invoke(updater, screen);
            if (updateGuiProperties != null) {
                updateGuiProperties.invoke(updater, guiProperties);
            }
            updateExclusionAreas.invoke(updater, Set.of());
            update.invoke(updater);
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

    private static class ViewportGuiProperties implements IGuiProperties {
        private final Screen screen;
        private final UiAnchorViewport.Rect viewport;

        private ViewportGuiProperties(Screen screen, UiAnchorViewport.Rect viewport) {
            this.screen = screen;
            this.viewport = viewport;
        }

        @Override
        public Class<? extends Screen> screenClass() { return screen.getClass(); }

        @Override
        public int guiLeft() {
            return (int) viewport.left();
        }

        @Override
        public int guiTop() {
            return (int) viewport.top();
        }

        @Override
        public int guiXSize() {
            return (int) viewport.width();
        }

        @Override
        public int guiYSize() {
            return (int) viewport.height();
        }

        @Override
        public int screenWidth() { return screen.width; }

        @Override
        public int screenHeight() { return screen.height; }
    }

    private static class PonderUiGuiProperties extends ViewportGuiProperties {
        private PonderUiGuiProperties(PonderUI screen, UiAnchorViewport.Rect viewport) {
            super(screen, viewport);
        }
    }

    private static class ProjectorGuiContainerHandler implements IGuiContainerHandler<ProjectorConfigScreen> {
        @Override
        public List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(ProjectorConfigScreen containerScreen) {
            return containerScreen.getJeiExtraAreas();
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
