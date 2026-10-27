package com.nododiiiii.ponderer.feedback;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import java.util.List;

/** Shared login notices; interactive chat components retain their original click semantics. */
public final class PackFeedback {
    private PackFeedback() {}
    public static void show(List<com.nododiiiii.ponderer.workspace.api.WorkspaceResults.PackUpdateInfo> updates, List<String> orphaned) {
        if (updates.isEmpty() && orphaned.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            var player = mc.player;
            if (player == null) return;

            for (com.nododiiiii.ponderer.workspace.api.WorkspaceResults.PackUpdateInfo info : updates) {
                String versionChange = info.oldVersion != null
                        ? "v" + info.oldVersion + " → v" + info.newVersion
                        : "v" + info.newVersion;
                MutableComponent msg = Component.literal("[Ponderer] ")
                        .withStyle(Style.EMPTY.withColor(0xFFA500))
                        .append(Component.literal(info.packName).withStyle(Style.EMPTY.withColor(0xFFFFFF)))
                        .append(Component.literal(": ").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
                if (info.oldVersion != null) {
                    msg.append(Component.translatable("ponderer.pack.update.version", versionChange)
                            .withStyle(Style.EMPTY.withColor(0x55FF55)));
                } else {
                    msg.append(Component.translatable("ponderer.pack.update.loaded", info.newVersion)
                            .withStyle(Style.EMPTY.withColor(0x55FF55)));
                }
                if (info.conflictCount > 0) {
                    msg.append(Component.literal(" ")
                            .append(Component.translatable("ponderer.pack.update.conflicts", info.conflictCount)
                                    .withStyle(Style.EMPTY.withColor(0xFFAA00))));
                }
                ClientFeedback.chat(msg);
            }

            if (Config.PACK_ORPHAN_PROMPT.get()) {
                for (String packName : orphaned) {
                    MutableComponent msg = Component.literal("[Ponderer] ")
                            .withStyle(Style.EMPTY.withColor(0xFFA500))
                            .append(Component.literal(packName).withStyle(Style.EMPTY.withColor(0xFFFFFF)))
                            .append(Component.literal(": ").withStyle(Style.EMPTY.withColor(0xAAAAAA)))
                            .append(Component.translatable("ponderer.pack.orphan.message")
                                    .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
                    ClientFeedback.chat(msg);

                    MutableComponent removeBtn = Component.literal("  [")
                            .withStyle(Style.EMPTY.withColor(0x888888))
                            .append(Component.translatable("ponderer.pack.orphan.remove")
                                    .withStyle(Style.EMPTY
                                            .withColor(0xFF6666)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                                    "/ponderer unregister_pack " + packName))
                                            .withUnderlined(true)))
                            .append(Component.literal("]").withStyle(Style.EMPTY.withColor(0x888888)));
                    ClientFeedback.chat(removeBtn);
                }
            }
        });
    }

}
