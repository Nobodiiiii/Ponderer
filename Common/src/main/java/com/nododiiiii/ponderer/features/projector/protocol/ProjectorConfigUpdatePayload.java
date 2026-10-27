package com.nododiiiii.ponderer.features.projector.protocol;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.features.projector.ProjectorProjectionMode;
import com.nododiiiii.ponderer.features.projector.ProjectorTriggerMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ProjectorConfigUpdatePayload(BlockPos projectorPos,
                                           ProjectorTriggerMode triggerMode, @Nullable BlockPos anchorPos,
                                           int intermissionTicks,
                                           boolean showBlueTint, boolean overlayAntiOcclusion,
                                           boolean compatibilityMode, ProjectorProjectionMode projectionMode,
                                           float miniatureScale, float textScale) implements CustomPacketPayload {

    public static final Type<ProjectorConfigUpdatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "projector_config_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectorConfigUpdatePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ProjectorConfigUpdatePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    private static final int MAX_TRIGGER_MODE_LENGTH = 64;
    private static final int MAX_PROJECTION_MODE_LENGTH = 64;

    public ProjectorConfigUpdatePayload {
        projectionMode = projectionMode == null ? ProjectorProjectionMode.DEFAULT : projectionMode;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(projectorPos);
        buf.writeUtf(triggerMode.serializedName(), MAX_TRIGGER_MODE_LENGTH);
        buf.writeBoolean(anchorPos != null);
        if (anchorPos != null) {
            buf.writeBlockPos(anchorPos);
        }
        buf.writeVarInt(intermissionTicks);
        buf.writeBoolean(showBlueTint);
        buf.writeBoolean(overlayAntiOcclusion);
        buf.writeBoolean(compatibilityMode);
        buf.writeUtf(projectionMode.serializedName(), MAX_PROJECTION_MODE_LENGTH);
        buf.writeFloat(miniatureScale);
        buf.writeFloat(textScale);
    }

    public static ProjectorConfigUpdatePayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos projectorPos = buf.readBlockPos();
        ProjectorTriggerMode triggerMode = ProjectorTriggerMode.byName(buf.readUtf(MAX_TRIGGER_MODE_LENGTH));
        BlockPos anchorPos = buf.readBoolean() ? buf.readBlockPos() : null;
        int intermissionTicks = buf.readVarInt();
        boolean showBlueTint = buf.readBoolean();
        boolean overlayAntiOcclusion = buf.readBoolean();
        boolean compatibilityMode = buf.readBoolean();
        ProjectorProjectionMode projectionMode = ProjectorProjectionMode.byName(buf.readUtf(MAX_PROJECTION_MODE_LENGTH));
        float miniatureScale = buf.readFloat();
        float textScale = buf.readFloat();
        return new ProjectorConfigUpdatePayload(projectorPos, triggerMode, anchorPos,
            intermissionTicks, showBlueTint, overlayAntiOcclusion,
            compatibilityMode, projectionMode, miniatureScale, textScale);
    }

}
