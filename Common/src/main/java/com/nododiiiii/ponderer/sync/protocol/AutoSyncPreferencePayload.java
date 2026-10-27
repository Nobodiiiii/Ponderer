package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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


}
