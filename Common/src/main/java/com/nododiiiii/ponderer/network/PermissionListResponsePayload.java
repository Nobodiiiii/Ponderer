package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.ui.PermissionManagementScreen;
import com.nododiiiii.ponderer.ui.UIText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public record PermissionListResponsePayload(List<Entry> entries, String viewerRole, boolean serverOperator,
                                            boolean canManage, String messageKey, String messageSubject,
                                            boolean error) {

    public record Entry(String subject, String role, boolean locked) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries().size());
        for (Entry entry : entries()) {
            buf.writeUtf(entry.subject());
            buf.writeUtf(entry.role());
            buf.writeBoolean(entry.locked());
        }
        buf.writeUtf(viewerRole() == null ? "" : viewerRole());
        buf.writeBoolean(serverOperator());
        buf.writeBoolean(canManage());
        buf.writeUtf(messageKey() == null ? "" : messageKey());
        buf.writeUtf(messageSubject() == null ? "" : messageSubject());
        buf.writeBoolean(error());
    }

    public static PermissionListResponsePayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        return new PermissionListResponsePayload(
            List.copyOf(entries),
            buf.readUtf(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readBoolean());
    }

    public static void sendSnapshot(ServerPlayer player, String messageKey, String messageSubject, boolean error) {
        UploadPermissions.Snapshot snapshot = UploadPermissions.snapshotFor(player);
        List<Entry> entries = new ArrayList<>();
        for (UploadPermissions.Entry entry : snapshot.entries()) {
            entries.add(new Entry(entry.subject(), entry.role().id(), entry.operatorManaged()));
        }
        UploadPermissions.Role viewerRole = snapshot.viewerRole();
        PondererServices.NETWORK.sendToPlayer(player, new PermissionListResponsePayload(
            List.copyOf(entries),
            viewerRole == null ? "" : viewerRole.id(),
            snapshot.serverOperator(),
            snapshot.canManage(),
            messageKey == null ? "" : messageKey,
            messageSubject == null ? "" : messageSubject,
            error));
    }

    public static void handle(PermissionListResponsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof PermissionManagementScreen screen) {
            screen.receiveSnapshot(payload);
            return;
        }

        if (payload.messageKey() != null && !payload.messageKey().isBlank() && client.player != null) {
            String text = payload.messageSubject() == null || payload.messageSubject().isBlank()
                ? UIText.of(payload.messageKey())
                : UIText.of(payload.messageKey(), payload.messageSubject());
            client.player.displayClientMessage(Component.literal(text), false);
        }
    }
}
