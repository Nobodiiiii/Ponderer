package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.ui.ShowExtraStructureScreen;
import com.nododiiiii.ponderer.ui.ShowStructureScreen;
import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DownloadStructureResultPayload(String sourceId, String targetId,
                                             boolean success, String message) implements CustomPacketPayload {

    public static final Type<DownloadStructureResultPayload> TYPE =
            new Type<>(new ResourceLocation(Ponderer.MODID, "download_structure_result"));
    public static final StreamCodec<FriendlyByteBuf, DownloadStructureResultPayload> CODEC =
            StreamCodec.of(DownloadStructureResultPayload::encode, DownloadStructureResultPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, DownloadStructureResultPayload payload) {
        buf.writeUtf(payload.sourceId());
        buf.writeUtf(payload.targetId());
        buf.writeBoolean(payload.success());
        buf.writeUtf(payload.message());
    }

    private static DownloadStructureResultPayload decode(FriendlyByteBuf buf) {
        return new DownloadStructureResultPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readBoolean(),
            buf.readUtf()
        );
    }

    public static void handle(DownloadStructureResultPayload payload) {
        String message = net.minecraft.network.chat.Component.translatable(payload.message(), payload.sourceId(), payload.targetId()).getString();
        ShowStructureScreen.onDownloadResult(payload.sourceId(), payload.targetId(), payload.success(), message);
        ShowExtraStructureScreen.onDownloadResult(payload.sourceId(), payload.targetId(), payload.success(), message);
        ClientFeedback.result(net.minecraft.network.chat.Component.literal(message),
            payload.success() ? FeedbackLevel.SUCCESS : FeedbackLevel.ERROR);
    }
}
