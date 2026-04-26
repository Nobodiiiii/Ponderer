package com.nododiiiii.ponderer.ui;

import com.mojang.logging.LogUtils;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.gui.screens.Screen;
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

    public record ReturnState(List<Screen> screenHistory, @Nullable PonderItemGridScreen returnScreen) {
        public ReturnState {
            screenHistory = List.copyOf(screenHistory);
        }
    }
}
