package com.nododiiiii.ponderer.forge.sticksnapshot.client;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ModNetworking;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ReplaySnapshotPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.SaveSnapshotPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.BlockSnapshot;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public final class ForgeShowInterfaceClient {

    private ForgeShowInterfaceClient() {
    }

    public static void showInterfaceStep(DslScene.DslStep step) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        BlockPos pos = parseBlockPos(step.blockPos);
        if (pos == null) {
            StickSnapshotFeature.LOGGER.warn("show_interface skipped: missing blockPos context");
            return;
        }

        BlockState state = resolveSnapshotState(step, mc.level.getBlockState(pos));
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        CompoundTag blockEntityTag = parseBlockEntityTag(step.nbt, pos);
        Direction face = parseDirection(step.direction);
        Vec3 hit = parseHit(step.point, pos);

        BlockSnapshot snapshot = new BlockSnapshot(
            Block.getId(state),
            blockId,
            blockEntityTag,
            mc.level.dimension().location(),
            pos.immutable(),
            face,
            hit,
            Boolean.TRUE.equals(step.whileSneaking));

        ClientInputHandler.prepareMirrorReplay(-1, true);
        ModNetworking.CHANNEL.sendToServer(new SaveSnapshotPacket(snapshot));
        ModNetworking.CHANNEL.sendToServer(new ReplaySnapshotPacket());
    }

    public static void clickInterfaceStep(DslScene.DslStep step) {
        if (step.pos == null || step.pos.size() < 2) {
            StickSnapshotFeature.LOGGER.warn("click_interface skipped: missing point");
            return;
        }
        Integer clickButton = parseClickButton(step.action);
        if (clickButton == null) {
            StickSnapshotFeature.LOGGER.warn("click_interface skipped: unsupported action={} ", step.action);
            return;
        }

        ClientInputHandler.clickEmbeddedMirrorAt(step.pos.get(0), step.pos.get(1), clickButton);
    }

    @Nullable
    private static BlockPos parseBlockPos(@Nullable List<Integer> pos) {
        if (pos == null || pos.size() < 3) {
            return null;
        }
        return new BlockPos(pos.get(0), pos.get(1), pos.get(2));
    }

    private static Direction parseDirection(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Direction.UP;
        }
        try {
            return Direction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Direction.UP;
        }
    }

    @Nullable
    private static Integer parseClickButton(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "left" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case "right" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            default -> null;
        };
    }

    private static Vec3 parseHit(@Nullable List<Double> point, BlockPos pos) {
        if (point == null || point.size() < 3) {
            return Vec3.atCenterOf(pos);
        }
        return new Vec3(point.get(0), point.get(1), point.get(2));
    }

    private static BlockState resolveSnapshotState(DslScene.DslStep step, BlockState fallbackState) {
        ResourceLocation expected = step.block == null ? null : ResourceLocation.tryParse(step.block);
        if (expected == null) {
            return fallbackState;
        }

        Block block = BuiltInRegistries.BLOCK.get(expected);
        if (block == null || block.defaultBlockState().isAir() && !"minecraft:air".equals(expected.toString())) {
            StickSnapshotFeature.LOGGER.debug("show_interface block registry miss, fallback to world state id={}", expected);
            return fallbackState;
        }

        BlockState resolved = block.defaultBlockState();
        if (step.blockProperties != null && !step.blockProperties.isEmpty()) {
            for (var entry : step.blockProperties.entrySet()) {
                Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
                if (property == null) {
                    continue;
                }
                resolved = applyProperty(resolved, property, entry.getValue());
            }
        }
        return resolved;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String rawValue) {
        return property.getValue(rawValue)
                .map(value -> state.setValue(property, value))
                .orElse(state);
    }

    @Nullable
    private static CompoundTag parseBlockEntityTag(@Nullable String rawNbt, BlockPos pos) {
        if (rawNbt == null || rawNbt.isBlank()) {
            return null;
        }
        try {
            CompoundTag parsed = TagParser.parseTag(rawNbt);
            parsed.putInt("x", pos.getX());
            parsed.putInt("y", pos.getY());
            parsed.putInt("z", pos.getZ());
            return parsed;
        } catch (CommandSyntaxException ex) {
            StickSnapshotFeature.LOGGER.debug("show_interface nbt parse failed, ignore nbt: {}", ex.getMessage());
            return null;
        }
    }
}