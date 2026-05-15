package com.nododiiiii.ponderer.ui;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;

public final class PonderScreenNavigation {

    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static final Field BACK_STACK = findScreenOpenerField("backStack");
    @Nullable
    private static final Field BACK_STEPPED_FROM = findScreenOpenerField("backSteppedFrom");

    private static boolean suppressNextPonderReturn;

    private PonderScreenNavigation() {
    }

    public static ReturnState captureReturnState() {
        return new ReturnState(ScreenOpener.getScreenHistory(), PonderItemGridScreen.returnScreen);
    }

    public static void restoreReturnState(@Nullable ReturnState state) {
        if (state == null) {
            return;
        }
        restoreScreenHistory(state.screenHistory());
        PonderItemGridScreen.returnScreen = state.returnScreen();
    }

    public static void suppressNextPonderReturn() {
        suppressNextPonderReturn = true;
    }

    public static boolean consumeSuppressNextPonderReturn() {
        boolean suppress = suppressNextPonderReturn;
        suppressNextPonderReturn = false;
        return suppress;
    }

    public static boolean openPonderUIForScene(@Nullable DslScene scene, int sceneIndex) {
        ResourceLocation itemId = getItemId(scene);
        if (itemId == null) {
            return false;
        }

        PonderUI ponderUI = PonderUI.of(itemId);
        PonderUIAccessor accessor = (PonderUIAccessor) ponderUI;

        List<PonderScene> ponderScenes = accessor.ponderer$getScenes();
        for (int i = 0; i < ponderScenes.size(); i++) {
            SceneRuntime.SceneMatch match = SceneRuntime.findBySceneId(ponderScenes.get(i).getId());
            if (match != null && match.sceneIndex() == sceneIndex
                && match.scene() != null && scene != null && match.scene().id.equals(scene.id)) {
                accessor.ponderer$setIndex(i);
                accessor.ponderer$getLazyIndex().startWithValue(i);
                ponderScenes.get(i).begin();
                break;
            }
        }

        Minecraft.getInstance().setScreen(ponderUI);
        return true;
    }

    private static void restoreScreenHistory(List<Screen> history) {
        if (BACK_STACK == null) {
            LOGGER.warn("Unable to restore Catnip screen history: ScreenOpener.backStack was not found");
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Deque<Screen> backStack = (Deque<Screen>) BACK_STACK.get(null);
            backStack.clear();
            ListIterator<Screen> iterator = history.listIterator(history.size());
            while (iterator.hasPrevious()) {
                backStack.push(iterator.previous());
            }
            if (BACK_STEPPED_FROM != null) {
                BACK_STEPPED_FROM.set(null, null);
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            LOGGER.warn("Unable to restore Catnip screen history", e);
        }
    }

    @Nullable
    private static Field findScreenOpenerField(String name) {
        try {
            Field field = ScreenOpener.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | SecurityException e) {
            LOGGER.warn("Unable to access ScreenOpener.{}", name, e);
            return null;
        }
    }

    @Nullable
    private static ResourceLocation getItemId(@Nullable DslScene scene) {
        if (scene == null || scene.items == null || scene.items.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(scene.items.get(0));
    }

    public record ReturnState(List<Screen> screenHistory, @Nullable PonderItemGridScreen returnScreen) {
        public ReturnState {
            screenHistory = List.copyOf(screenHistory);
        }
    }
}
