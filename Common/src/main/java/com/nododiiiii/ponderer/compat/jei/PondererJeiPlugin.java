package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.ui.AbstractJeiAwareFormScreen;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.JeiAwareScreen;
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.ui.UiAnchorViewport;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

@JeiPlugin
public class PondererJeiPlugin implements IModPlugin {

    @Nullable
    private static IJeiRuntime runtime = null;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("ponderer", "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(AbstractJeiAwareFormScreen.class, screen -> {
            if (!screen.shouldShowJei()) return null;
            return new JeiAwareGuiProperties(screen);
        });
        registration.addGhostIngredientHandler(AbstractJeiAwareFormScreen.class, new JeiAwareGhostHandler<>());
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public List<Rect2i> getGuiExtraAreas() {
                Screen screen = Minecraft.getInstance().screen;
                if (screen instanceof AbstractJeiAwareFormScreen form && form.shouldShowJei()) {
                    return form.getJeiExtraAreas();
                }
                return List.of();
            }
        });

        registration.addGuiContainerHandler(ProjectorConfigScreen.class, new ProjectorGuiContainerHandler());

        // PonderUI: public API only (design doc 14.6). JEI sits beside the embedded interface
        // while slots are edited or the player is trying it; drops target sandbox slots.
        registration.addGuiScreenHandler(PonderUI.class, screen -> {
            UiAnchorViewport.Rect viewport = resolvePonderUiViewport(screen);
            return viewport == null ? null : new PonderUiGuiProperties(screen, viewport);
        });
        registration.addGhostIngredientHandler(PonderUI.class, new PonderUiGhostHandler());
        registration.addGlobalGuiHandler(new PonderUiIngredientHandler());
    }

    @Nullable
    static IJeiRuntime getRuntime() {
        return runtime;
    }

    static boolean isIngredientListNotDisplayed() {
        return runtime != null && !runtime.getIngredientListOverlay().isListDisplayed();
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

    private static class ProjectorGuiContainerHandler implements IGuiContainerHandler<ProjectorConfigScreen> {
        @Override
        public List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(ProjectorConfigScreen containerScreen) {
            return containerScreen.getJeiExtraAreas();
        }
    }

    /** The embedded interface rectangle while JEI should be shown next to it, else null. */
    @Nullable
    static UiAnchorViewport.Rect resolvePonderUiViewport(Screen screen) {
        SandboxSession session = InterfaceHost.sessionFor(screen);
        if (session == null || !(InterfaceSlotEditState.isActive() || InterfaceHost.isTakingInput(session))) {
            return null;
        }
        int[] rect = session.guiRect();
        if (rect == null || rect[2] <= 1 || rect[3] <= 1) {
            return null;
        }
        return new UiAnchorViewport.Rect(rect[0], rect[1], rect[2], rect[3]);
    }
}
