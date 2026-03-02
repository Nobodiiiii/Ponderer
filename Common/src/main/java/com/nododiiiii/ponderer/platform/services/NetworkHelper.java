package com.nododiiiii.ponderer.platform.services;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Function;

/**
 * Platform abstraction for networking (packet send/receive).
 * Forge: SimpleChannel. Fabric: Fabric Networking API.
 */
public interface NetworkHelper {

    /** Register all Ponderer network packets. Called during common setup. */
    void registerPackets();

    /** Send a packet from client to server. */
    void sendToServer(Object packet);

    /** Send a packet from server to a specific player. */
    void sendToPlayer(ServerPlayer player, Object packet);
}
