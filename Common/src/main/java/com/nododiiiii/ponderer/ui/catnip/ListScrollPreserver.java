package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;

import javax.annotation.Nullable;

public final class ListScrollPreserver {

    private double rememberedScroll;

    public double capture(@Nullable ConfigScreenList list) {
        rememberedScroll = current(list);
        return rememberedScroll;
    }

    public void remember(double scroll) {
        rememberedScroll = Math.max(0, scroll);
    }

    public double remembered() {
        return rememberedScroll;
    }

    public void restoreRemembered(@Nullable ConfigScreenList list) {
        restore(list, rememberedScroll);
    }

    public static double current(@Nullable ConfigScreenList list) {
        return list != null ? list.getScrollAmount() : 0;
    }

    public static void restore(@Nullable ConfigScreenList list, double scroll) {
        if (list != null) {
            list.setScrollAmount(Math.max(0, scroll));
        }
    }

    public static void restoreAfterSearch(@Nullable ConfigScreenList list, @Nullable Double preservedScroll,
                                          @Nullable String searchQuery) {
        if (preservedScroll != null && (searchQuery == null || searchQuery.isEmpty())) {
            restore(list, preservedScroll);
        }
    }
}
