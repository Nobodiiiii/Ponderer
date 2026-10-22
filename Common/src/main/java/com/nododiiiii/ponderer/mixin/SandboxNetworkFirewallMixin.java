package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.sandbox.SandboxNetworkFirewall;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Interface sandbox network firewall. Every other {@code send} overload funnels into this one.
 * Optional ({@code require = 0}); without it sessions stay read-only.
 */
@Mixin(Connection.class)
public class SandboxNetworkFirewallMixin {

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void ponderer$sandboxFirewall(Packet<?> packet, @Nullable PacketSendListener listener,
                                          CallbackInfo ci) {
        SandboxNetworkFirewall.markLive();
        if (SandboxNetworkFirewall.shouldBlock(packet)) {
            ci.cancel();
        }
    }
}
