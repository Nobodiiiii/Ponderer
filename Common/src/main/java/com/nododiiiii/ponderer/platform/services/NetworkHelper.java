package com.nododiiiii.ponderer.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Platform abstraction for networking (packet send/receive).
 */
public interface NetworkHelper {

    /** Register all Ponderer network packets. Called during common setup. */
    void registerPackets();

    /** Send a packet from client to server. */
    void sendToServer(CustomPacketPayload packet);

    /** Send a packet from server to a specific player. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload packet);
}
