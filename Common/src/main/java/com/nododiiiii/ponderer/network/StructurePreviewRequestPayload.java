package com.nododiiiii.ponderer.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.List;

/** Reads preview resources only; never generates a structure or loads a chunk. */
public record StructurePreviewRequestPayload(long requestId, String structureId) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_TEMPLATE_BYTES = 768 * 1024;
    public static final int MAX_STRUCTURES = 16384;
    public static final String STATUS_PREFIX = "ponderer.ui.structure_list.preview.";
    public static final Type<StructurePreviewRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "structure_preview_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StructurePreviewRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeLong(payload.requestId());
            buf.writeUtf(payload.structureId(), 256);
        }, buf -> new StructurePreviewRequestPayload(buf.readLong(), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StructurePreviewRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) return;
        StructurePreviewResponsePayload response;
        try {
            var registry = player.server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            if (payload.structureId().isEmpty()) {
                List<String> ids = registry.keySet().stream().map(ResourceLocation::toString)
                    .sorted().limit(MAX_STRUCTURES).toList();
                response = new StructurePreviewResponsePayload(payload.requestId(), "", ids, new byte[0], "");
            } else {
                ResourceLocation id = ResourceLocation.tryParse(payload.structureId());
                Structure structure = id == null ? null : registry.get(id);
                if (structure == null) {
                    response = failure(payload, "unavailable");
                } else {
                    var ops = player.server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                    JsonObject definition = Structure.DIRECT_CODEC.encodeStart(ops, structure).getOrThrow().getAsJsonObject();
                    JsonElement poolDefinition = definition.get("start_pool");
                    if (poolDefinition == null) {
                        response = failure(payload, "no_template");
                    } else {
                        JsonObject pool;
                        if (poolDefinition.isJsonPrimitive()) {
                            ResourceLocation poolId = ResourceLocation.parse(poolDefinition.getAsString());
                            StructureTemplatePool value = player.server.registryAccess()
                                .registryOrThrow(Registries.TEMPLATE_POOL).get(poolId);
                            if (value == null) throw new IllegalArgumentException("Missing template pool " + poolId);
                            pool = StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, value).getOrThrow().getAsJsonObject();
                        } else {
                            pool = poolDefinition.getAsJsonObject();
                        }
                        ResourceLocation templateId = firstTemplate(pool.getAsJsonArray("elements").get(0)
                            .getAsJsonObject().getAsJsonObject("element"), 0);
                        if (templateId == null) {
                            response = failure(payload, "no_template");
                        } else {
                            // Minecraft 1.21 uses the singular data/<namespace>/structure directory.
                            ResourceLocation resourceId = templateId.withPath(path -> "structure/" + path + ".nbt");
                            byte[] bytes;
                            try (InputStream input = player.server.getResourceManager().open(resourceId)) {
                                bytes = input.readNBytes(MAX_TEMPLATE_BYTES + 1);
                            }
                            response = bytes.length > MAX_TEMPLATE_BYTES
                                ? failure(payload, "too_large")
                                : new StructurePreviewResponsePayload(payload.requestId(), payload.structureId(),
                                    List.of(), bytes, "");
                        }
                    }
                }
            }
        } catch (Exception | LinkageError e) {
            LOGGER.debug("Cannot read structure preview for {}", payload.structureId(), e);
            response = failure(payload, "unavailable");
        }
        try {
            PondererServices.NETWORK.sendToPlayer(player, response);
        } catch (Exception | LinkageError e) {
            LOGGER.debug("Cannot send structure preview for {}", payload.structureId(), e);
        }
    }

    @Nullable
    private static ResourceLocation firstTemplate(JsonObject element, int depth) {
        if (depth > 16) return null;
        if (element.has("location") && element.get("location").isJsonPrimitive()) {
            return ResourceLocation.tryParse(element.get("location").getAsString());
        }
        if (element.has("elements") && !element.getAsJsonArray("elements").isEmpty()) {
            return firstTemplate(element.getAsJsonArray("elements").get(0).getAsJsonObject(), depth + 1);
        }
        return null;
    }

    private static StructurePreviewResponsePayload failure(StructurePreviewRequestPayload request, String status) {
        return new StructurePreviewResponsePayload(request.requestId(), request.structureId(), List.of(),
            new byte[0], STATUS_PREFIX + status);
    }
}
