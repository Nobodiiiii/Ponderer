package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.nododiiiii.ponderer.support.nbt.DataSanitizer;
import com.nododiiiii.ponderer.support.nbt.IdentityRemapper;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Writes descriptor context into sandbox objects: blocks and block entities into the
 * sandbox level (design doc 9, steps 1–2) and the player context into the sandbox player
 * (step 3). Never touches the real level or player.
 */
public final class ContextApplier {

    private ContextApplier() {
    }

    // ---- Blocks -------------------------------------------------------------------------

    @Nullable
    public static BlockState resolveState(@Nullable String blockId, Map<String, String> properties) {
        ResourceLocation id = blockId == null ? null : ResourceLocation.tryParse(blockId.trim());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property != null) {
                state = withProperty(state, property, entry.getValue());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String raw) {
        return property.getValue(raw).map(value -> state.setValue(property, value)).orElse(state);
    }

    /**
     * Places {@code state} at {@code pos} and, for block entity blocks, constructs the block
     * entity from sanitized NBT via {@link BlockEntity#loadStatic}. The block entity is only
     * bound to the sandbox level; its lifecycle hooks are never invoked.
     */
    @Nullable
    public static BlockEntity placeBlock(SandboxSession session, BlockPos pos, BlockState state,
                                         @Nullable CompoundTag blockEntityTag) {
        SandboxLevel level = session.level();
        HolderLookup.Provider registries = session.registries();
        level.placeState(pos, state);
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        BlockEntity blockEntity = null;
        if (blockEntityTag != null) {
            CompoundTag tag = blockEntityTag.copy();
            DataSanitizer.stripBlockEntityHazards(tag);
            if (!tag.contains("id")) {
                BlockEntity probe = entityBlock.newBlockEntity(pos, state);
                if (probe != null) {
                    BlockEntity.addEntityType(tag, probe.getType());
                }
            }
            blockEntity = BlockEntity.loadStatic(pos, state, tag, registries);
            if (blockEntity == null) {
                session.report("block entity NBT for " + state.getBlock() + " could not be loaded; using defaults");
            }
        }
        if (blockEntity == null) {
            blockEntity = entityBlock.newBlockEntity(pos, state);
        }
        if (blockEntity != null) {
            level.putBlockEntity(pos, blockEntity);
        }
        return blockEntity;
    }

    /** Parses and sanitizes block entity SNBT, then writes the replay identity into its identity slots. */
    @Nullable
    public static CompoundTag blockEntityTag(SandboxSession session, @Nullable String snbt,
                                             InterfaceDescriptor descriptor) {
        DataSanitizer.Parsed parsed = DataSanitizer.parseBlockEntityNbt(snbt, DataSanitizer.DEFAULT_MAX_SNBT_CHARS);
        if (parsed.error() != null) {
            session.report("block entity NBT ignored: " + parsed.error());
        }
        CompoundTag tag = parsed.tag();
        if (tag != null && !descriptor.identitySlots.isEmpty()) {
            SandboxPlayer player = session.player();
            UUID viewer = player.getUUID();
            IdentityRemapper.apply(tag, descriptor.identitySlots, descriptor.player.identity,
                viewer, player.getGameProfile().getName());
        }
        return tag;
    }

    public static void placeContextBlocks(SandboxSession session, InterfaceDescriptor descriptor, BlockPos origin) {
        for (InterfaceDescriptor.ContextBlock contextBlock : descriptor.contextBlocks) {
            if (contextBlock.dx() == 0 && contextBlock.dy() == 0 && contextBlock.dz() == 0) {
                continue;
            }
            BlockState state = resolveState(contextBlock.block(), contextBlock.properties());
            if (state == null) {
                session.report("unknown context block " + contextBlock.block());
                continue;
            }
            BlockPos pos = origin.offset(contextBlock.dx(), contextBlock.dy(), contextBlock.dz());
            placeBlock(session, pos, state, blockEntityTag(session, contextBlock.nbt(), descriptor));
        }
    }

    // ---- Player -------------------------------------------------------------------------

    public static GameType gameType(InterfaceDescriptor descriptor) {
        return GameType.byName(descriptor.player.gameMode.toLowerCase(Locale.ROOT), GameType.SURVIVAL);
    }

    /** Must run inside the sandbox scope: {@code MultiPlayerGameMode#setLocalMode} reads {@code Minecraft.player}. */
    public static void applyPlayer(SandboxSession session, InterfaceDescriptor descriptor, BlockPos origin) {
        SandboxPlayer player = session.player();
        InterfaceDescriptor.PlayerContext context = descriptor.player;

        double[] offset = context.offset;
        player.setPos(origin.getX() + offset[0], origin.getY() + offset[1], origin.getZ() + offset[2]);
        player.setYRot(context.yaw);
        player.setXRot(context.pitch);
        player.setShiftKeyDown(context.sneaking);

        GameType type = gameType(descriptor);
        session.gameMode().setLocalMode(type, null);
        type.updatePlayerAbilities(player.getAbilities());
        if (context.instabuild != null) {
            player.getAbilities().instabuild = context.instabuild;
        }

        player.experienceLevel = context.xpLevel;
        player.experienceProgress = context.xpProgress;

        Inventory inventory = player.getInventory();
        inventory.selected = Math.max(0, Math.min(8, context.selectedSlot));
        for (InterfaceDescriptor.InventoryItem entry : context.inventory) {
            ItemStack stack = decodeItem(session, entry.item());
            if (!stack.isEmpty()) {
                putInventory(session, inventory, entry.slot(), stack);
            }
        }
    }

    static void putInventory(SandboxSession session, Inventory inventory, String slot, ItemStack stack) {
        String key = slot.toLowerCase(Locale.ROOT);
        if ("offhand".equals(key)) {
            inventory.offhand.set(0, stack);
            return;
        }
        if (key.startsWith("armor:")) {
            int index = switch (key.substring("armor:".length())) {
                case "feet", "0" -> 0;
                case "legs", "1" -> 1;
                case "chest", "2" -> 2;
                case "head", "3" -> 3;
                default -> -1;
            };
            if (index >= 0) {
                inventory.armor.set(index, stack);
            } else {
                session.report("unknown armor slot " + slot);
            }
            return;
        }
        try {
            int index = Integer.parseInt(key);
            if (index >= 0 && index < inventory.items.size()) {
                inventory.items.set(index, stack);
                return;
            }
        } catch (NumberFormatException ignored) {
        }
        session.report("unknown inventory slot " + slot);
    }

    /** Puts the target item of an item descriptor into the requested hand. */
    public static ItemStack equipTargetItem(SandboxSession session, InterfaceDescriptor descriptor) {
        if (descriptor.item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = decodeItem(session, descriptor.item);
        if (stack.isEmpty()) {
            return stack;
        }
        Inventory inventory = session.player().getInventory();
        if ("off".equals(descriptor.hand)) {
            inventory.offhand.set(0, stack);
        } else {
            inventory.items.set(inventory.selected, stack);
        }
        return stack;
    }

    // ---- Items --------------------------------------------------------------------------

    public static ItemStack decodeItem(SandboxSession session, InterfaceDescriptor.ItemSpec spec) {
        ResourceLocation id = ResourceLocation.tryParse(spec.id().trim());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            session.report("unknown item " + spec.id());
            return ItemStack.EMPTY;
        }
        ItemStack stack;
        if (spec.components() != null) {
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            json.addProperty("count", spec.count());
            json.add("components", spec.components());
            RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, session.registries());
            stack = ItemStack.OPTIONAL_CODEC.parse(ops, json)
                .resultOrPartial(error -> session.report("item " + spec.id() + ": " + error))
                .orElse(ItemStack.EMPTY);
        } else {
            stack = new ItemStack(BuiltInRegistries.ITEM.get(id), spec.count());
        }
        if (!stack.isEmpty() && spec.legacyNbt() != null) {
            DataSanitizer.Parsed parsed = DataSanitizer.parse(spec.legacyNbt(), DataSanitizer.DEFAULT_MAX_SNBT_CHARS);
            if (parsed.tag() != null && !parsed.tag().isEmpty()) {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(parsed.tag()));
            } else if (parsed.error() != null) {
                session.report("item NBT ignored: " + parsed.error());
            }
        }
        return stack;
    }
}
