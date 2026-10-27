package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record PermissionListResponsePayload(List<Entry> entries, String viewerRole, boolean serverOperator,
                                            boolean canManage, String messageKey, String messageSubject,
                                            boolean error) implements CustomPacketPayload {

    public static final Type<PermissionListResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "permission_list_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PermissionListResponsePayload> CODEC =
            StreamCodec.of(PermissionListResponsePayload::encode, PermissionListResponsePayload::decode);

    public record Entry(String subject, String role, boolean locked) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, PermissionListResponsePayload payload) {
        buf.writeVarInt(payload.entries().size());
        for (Entry entry : payload.entries()) {
            buf.writeUtf(entry.subject());
            buf.writeUtf(entry.role());
            buf.writeBoolean(entry.locked());
        }
        buf.writeUtf(payload.viewerRole() == null ? "" : payload.viewerRole());
        buf.writeBoolean(payload.serverOperator());
        buf.writeBoolean(payload.canManage());
        buf.writeUtf(payload.messageKey() == null ? "" : payload.messageKey());
        buf.writeUtf(payload.messageSubject() == null ? "" : payload.messageSubject());
        buf.writeBoolean(payload.error());
    }

    private static PermissionListResponsePayload decode(RegistryFriendlyByteBuf buf) {
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

}
