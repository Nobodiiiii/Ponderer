package com.nododiiiii.ponderer.forge.sticksnapshot.network;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetworking {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(StickSnapshotFeature.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int nextId = 0;

    private ModNetworking() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, SaveSnapshotPacket.class, SaveSnapshotPacket::encode, SaveSnapshotPacket::decode, SaveSnapshotPacket::handle);
        CHANNEL.registerMessage(nextId++, ReplaySnapshotPacket.class, ReplaySnapshotPacket::encode, ReplaySnapshotPacket::decode, ReplaySnapshotPacket::handle);
        CHANNEL.registerMessage(nextId++, MirrorClosePacket.class, MirrorClosePacket::encode, MirrorClosePacket::decode, MirrorClosePacket::handle);
        CHANNEL.registerMessage(nextId++, MirrorForgeOpenPacket.class, MirrorForgeOpenPacket::encode,
                MirrorForgeOpenPacket::decode, MirrorForgeOpenPacket::handle);
        CHANNEL.registerMessage(nextId++, SaveItemSnapshotPacket.class, SaveItemSnapshotPacket::encode,
                SaveItemSnapshotPacket::decode, SaveItemSnapshotPacket::handle);
        CHANNEL.registerMessage(nextId++, ReplayItemSnapshotPacket.class, ReplayItemSnapshotPacket::encode,
                ReplayItemSnapshotPacket::decode, ReplayItemSnapshotPacket::handle);
        CHANNEL.registerMessage(nextId++, ReplayUiIdPacket.class, ReplayUiIdPacket::encode,
                ReplayUiIdPacket::decode, ReplayUiIdPacket::handle);
    }
}
