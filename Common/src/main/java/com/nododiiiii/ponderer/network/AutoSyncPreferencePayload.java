package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ponder.AutoRemoteSyncService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record AutoSyncPreferencePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<AutoSyncPreferencePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "auto_sync_preference"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AutoSyncPreferencePayload> CODEC =
        StreamCodec.of((buf, payload) -> buf.writeBoolean(payload.enabled()),
            buf -> new AutoSyncPreferencePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AutoSyncPreferencePayload payload, @Nullable ServerPlayer player) {
        if (player != null) {
            AutoRemoteSyncService.setPlayerEnabled(player, payload.enabled());
        }
    }
}
