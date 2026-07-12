package com.nododiiiii.ponderer.forge.mirror;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.forge.mirror.client.ClientInputHandler;
import com.nododiiiii.ponderer.forge.mirror.network.MirrorNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ForgeMirrorFeature {
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(Ponderer.MODID, "mirror");
    public static final Logger LOGGER = LoggerFactory.getLogger(Ponderer.MODID + ".forge.mirror");

    private ForgeMirrorFeature() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MirrorNetworking::register);
    }

    public static void onClientInit() {
        ClientInputHandler.register();
    }
}
