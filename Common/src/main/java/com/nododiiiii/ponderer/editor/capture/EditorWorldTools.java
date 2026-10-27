package com.nododiiiii.ponderer.editor.capture;

import com.nododiiiii.ponderer.feedback.WorldFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** World tool lifecycle, scheduled by the product independently of feedback rendering. */
public final class EditorWorldTools {
    private static Object world;
    private EditorWorldTools() {}
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) {
            world = mc.level;
            NbtPickState.reset();
            CoordPickState.reset();
        }
        if (mc.player == null || mc.screen != null) return;
        if (NbtPickState.isActive()) {
            WorldFeedback.hint("nbt", Component.translatable(NbtPickState.isAwaitingServer()
                ? "ponderer.ui.nbt_pick.waiting" : "ponderer.ui.nbt_pick.middle_prompt"), 60, 2);
        } else WorldFeedback.clear("nbt");
        if (CoordPickState.isActive()) CoordPickState.onClientTick();
        else WorldFeedback.clear("coordinates");
        if (InterfaceCapture.isObserving()) WorldFeedback.hint("capture", Component.translatable("ponderer.interface.capture.start"), 60, 2);
        else WorldFeedback.clear("capture");
    }
}
