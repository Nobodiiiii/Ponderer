package com.nododiiiii.ponderer.feedback;

import com.nododiiiii.ponderer.runtime.mixin.GuiFeedbackAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** One ActionBar writer for this mod. Transient feedback wins over active tools and passive triggers. */
public final class WorldFeedback {
    private record Hint(Component message, int priority, int until) {}
    private static final Map<String, Hint> hints = new LinkedHashMap<>();
    private static Object world;
    private static int ticks;
    private static Component displayed;

    private WorldFeedback() {}

    public static void hint(String owner, Component message, int priority, int duration) {
        hints.put(owner, new Hint(message, priority, ticks + duration));
    }

    public static void clear(String owner) { hints.remove(owner); }

    public static void interaction(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FeedbackTarget target) target.acceptFeedback(message, FeedbackLevel.INFO);
        else hint("interaction", message, 100, 40);
    }

    public static void title(Component message) {
        Minecraft.getInstance().gui.setTitle(message);
        Minecraft.getInstance().gui.setTimes(0, 20, 10);
    }

    public static void beginTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientFeedback.tick();
        if (world != mc.level) {
            hints.clear();
            release(mc);
            world = mc.level;
        }
        ticks++;
        hints.values().removeIf(hint -> hint.until() <= ticks);
        if (mc.player == null || mc.screen != null) {
            hints.clear();
            release(mc);
            return;
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        Hint selected = null;
        for (Hint hint : hints.values()) {
            if (selected == null || hint.priority() > selected.priority()) selected = hint;
        }
        if (selected == null) {
            release(mc);
            return;
        }
        displayed = selected.message();
        mc.gui.setOverlayMessage(displayed, false);
    }

    private static void release(Minecraft mc) {
        // Only remove the exact message we own. Another mod or vanilla may have replaced it.
        GuiFeedbackAccessor gui = (GuiFeedbackAccessor) mc.gui;
        if (displayed != null && gui.ponderer$getOverlayMessage() == displayed) gui.ponderer$setOverlayMessageTime(0);
        displayed = null;
    }
}
