package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.ui.ShowStructureScreen;
import net.minecraft.network.FriendlyByteBuf;

public record DownloadStructureResultPayload(String sourceId, String targetId,
                                             boolean success, String message) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sourceId());
        buf.writeUtf(targetId());
        buf.writeBoolean(success());
        buf.writeUtf(message());
    }

    public static DownloadStructureResultPayload decode(FriendlyByteBuf buf) {
        return new DownloadStructureResultPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readBoolean(),
            buf.readUtf()
        );
    }

    public static void handle(DownloadStructureResultPayload payload) {
        ShowStructureScreen.onDownloadResult(payload.sourceId(), payload.targetId(), payload.success(), payload.message());
    }
}
