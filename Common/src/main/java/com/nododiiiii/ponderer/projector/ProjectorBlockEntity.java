package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.network.ProjectorPlaybackStartPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.client.ProjectorClientCaches;
import com.nododiiiii.ponderer.projector.client.ProjectorPlaybackState;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderBounds;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderDistances;
import com.nododiiiii.ponderer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class ProjectorBlockEntity extends BlockEntity implements Container, MenuProvider {

    private static final String TAG_SOURCE_ITEM = "SourceItem";
    private static final String TAG_TRIGGER_MODE = "TriggerMode";
    private static final String TAG_OFFSET = "ProjectionOffset";
    private static final String TAG_OFFSET_WORLD_SPACE = "ProjectionOffsetWorldSpace";
    private static final String TAG_REDSTONE = "RedstonePowered";
    private static final String TAG_INTERMISSION = "IntermissionTicks";
    private static final String TAG_SHOW_BLUE_TINT = "ShowBlueTint";
    private static final String TAG_OVERLAY_ANTI_OCCLUSION = "OverlayAntiOcclusion";
    private static final String TAG_COMPATIBILITY_MODE = "CompatibilityMode";
    private static final String TAG_PROJECTION_MODE = "ProjectionMode";
    private static final String TAG_MINIATURE_SCALE = "MiniatureScale";
    private static final String TAG_TEXT_SCALE = "TextScale";
    public static final int DEFAULT_INTERMISSION_TICKS = 40;
    public static final int FINAL_EXTRA_TICKS = 200;

    private ItemStack sourceItem = ItemStack.EMPTY;
    @Nullable
    private BlockPos projectionOffset;
    private ProjectorTriggerMode triggerMode = ProjectorTriggerMode.MANUAL_LOOP;
    private boolean redstonePowered;
    private int intermissionTicks = DEFAULT_INTERMISSION_TICKS;
    private boolean showBlueTint = true;
    private boolean overlayAntiOcclusion = false;
    private boolean compatibilityMode = true;
    private ProjectorProjectionMode projectionMode = ProjectorProjectionMode.DEFAULT;
    private float miniatureScale = 1.0F;
    private float textScale = 1.0F;
    private transient boolean clientPlaying;
    private transient boolean clientPlaybackLooping = true;
    private transient long clientPlaybackStartGameTime;
    private transient int clientPlaybackRevision;
    private transient int clientPlaybackTickSnapshot;
    private transient int clientPlaybackStateToken;
    private transient boolean clientAutoplayActive;
    private transient String clientAutoplayKey = "";

    public ProjectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PROJECTOR.get(), pos, state);
    }

    public ProjectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProjectorBlockEntity projector) {
        if (level.isClientSide) {
            return;
        }

        if (!ProjectorFeature.isProjectorEnabled()) {
            projector.convertToDisabledChest();
            return;
        }

        boolean changed = projector.updateRedstoneState(level.hasNeighborSignal(pos));

        if (changed) {
            projector.syncBlockState();
            projector.syncToClient();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ProjectorBlockEntity projector) {
        if (!level.isClientSide) {
            return;
        }

        projector.syncClientPlaybackFromServerState();
        if (!projector.isPlaying() || !projector.hasRenderableScene()) {
            return;
        }

        if (level.players().isEmpty()) {
            return;
        }

        Player player = level.players().get(0);
        double distanceSqr = ProjectorRenderBounds.distanceToRenderBoundsSqr(projector, player.position());
        if (distanceSqr > ProjectorRenderDistances.CLIENT_PRELOAD_DISTANCE_SQR) {
            return;
        }

        ProjectorPlaybackState.prepareForTick(projector);
    }

    public ProjectorKind getProjectorKind() {
        return ProjectorKind.fromState(getBlockState());
    }

    public ItemStack getSourceItem() {
        return sourceItem.copy();
    }

    @Nullable
    public BlockPos getProjectionOffset() {
        return projectionOffset;
    }

    /**
     * Returns the world-space offset from the projector block to the scene origin.
     * If no explicit offset is stored yet, 1:1 projectors default to one block in front
     * of their current facing direction.
     */
    public BlockPos getEffectiveProjectionOffset() {
        if (projectionOffset != null) {
            return projectionOffset;
        }
        if (!getProjectorKind().requiresAnchor()) {
            return BlockPos.ZERO;
        }
        return defaultProjectionOffset(getBlockState().getValue(ProjectorBlock.FACING));
    }

    public static BlockPos defaultProjectionOffset(Direction facing) {
        return new BlockPos(facing.getStepX(), 0, facing.getStepZ());
    }

    /**
     * Rotates the scene so the projector facing points toward scene +X.
     */
    public float getSceneRotationDegrees() {
        return sceneRotationDegrees(getBlockState().getValue(ProjectorBlock.FACING));
    }

    public static float sceneRotationDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 90.0F;
            case SOUTH -> -90.0F;
            case WEST -> 180.0F;
            case EAST -> 0.0F;
            default -> 0.0F;
        };
    }

    /**
     * 获取场景在世界中的实际投影原点（投影仪位置 + 世界坐标偏移）。
     * 仅对 1:1 投影仪有效。
     */
    public BlockPos getProjectionAnchor() {
        return getBlockPos().offset(getEffectiveProjectionOffset());
    }

    private static BlockPos rotateLegacyOffsetByFacing(BlockPos offset, Direction facing) {
        return switch (facing) {
            case SOUTH -> new BlockPos(offset.getZ(), offset.getY(), offset.getX());
            case NORTH -> new BlockPos(-offset.getZ(), offset.getY(), -offset.getX());
            case EAST -> new BlockPos(offset.getX(), offset.getY(), -offset.getZ());
            case WEST -> new BlockPos(-offset.getX(), offset.getY(), offset.getZ());
            default -> offset;
        };
    }

    public ProjectorTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public boolean isPlaying() {
        return level != null && level.isClientSide && clientPlaying;
    }

    public boolean isPlaybackLooping() {
        return level != null && level.isClientSide && clientPlaybackLooping;
    }

    public boolean isRedstonePowered() {
        return redstonePowered;
    }

    public int getPlaybackRevision() {
        return level != null && level.isClientSide ? clientPlaybackRevision : 0;
    }

    public int resolveDisplayPlaybackTick(int totalDurationTicks, boolean advanceClientClock) {
        if (!isPlaying()) {
            return ProjectorSceneTimeline.NO_PLAYBACK_TICK;
        }

        int safeTotalDuration = Math.max(0, totalDurationTicks);
        if (level == null || !level.isClientSide) {
            return ProjectorSceneTimeline.NO_PLAYBACK_TICK;
        }
        long currentGameTime = level.getGameTime();
        if (shouldStopClientPlayback(currentGameTime, safeTotalDuration)) {
            stopClientPlayback();
            return ProjectorSceneTimeline.NO_PLAYBACK_TICK;
        }
        return resolveClientPlaybackTick(currentGameTime, safeTotalDuration);
    }

    public float resolveDisplayPartialTick(int totalDurationTicks, float partialTick) {
        if (!isPlaying()) {
            return 0.0F;
        }

        int safeTotalDuration = Math.max(0, totalDurationTicks);
        if (level == null || !level.isClientSide) {
            return 0.0F;
        }
        long currentGameTime = level.getGameTime();
        return isClientPlaybackFrozen(currentGameTime, safeTotalDuration) ? 0.0F : partialTick;
    }

    public int getIntermissionTicks() {
        return intermissionTicks;
    }

    public boolean showBlueTint() {
        return showBlueTint;
    }

    public boolean overlayAntiOcclusion() {
        return overlayAntiOcclusion;
    }

    public boolean compatibilityMode() {
        return compatibilityMode;
    }

    public ProjectorProjectionMode getProjectionMode() {
        return projectionMode;
    }

    public float getMiniatureScale() {
        return miniatureScale;
    }

    public float getTextScale() {
        return textScale;
    }

    public void setTextScale(float textScale) {
        this.textScale = Math.max(0.1F, Math.min(10.0F, textScale));
        setChanged();
        syncToClient();
    }

    public AABB getRenderBoundingBox() {
        if (level != null && level.isClientSide && isPlaying()) {
            return ProjectorRenderBounds.estimate(this);
        }
        return new AABB(worldPosition).inflate(1.0D);
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            ProjectorClientCaches.invalidate(worldPosition);
        }
        super.setRemoved();
    }

    public boolean hasRenderableScene() {
        return !sourceItem.isEmpty();
    }

    @Override
    public void setChanged() {
        normalizeSourceItem();
        super.setChanged();
        if (level != null && !level.isClientSide) {
            syncBlockState();
        }
    }

    public void applyConfig(ProjectorTriggerMode newMode,
                            @Nullable BlockPos newProjectionOffset,
                            int newIntermissionTicks, boolean newShowBlueTint,
                            boolean newOverlayAntiOcclusion, boolean newCompatibilityMode,
                            ProjectorProjectionMode newProjectionMode,
                            float newMiniatureScale, float newTextScale) {
        this.triggerMode = newMode == null ? ProjectorTriggerMode.MANUAL_LOOP : newMode;

        // 1:1 投影仪保存世界坐标偏移；null 表示继续使用“前方一格”的默认锚点。
        if (getProjectorKind().requiresAnchor()) {
            this.projectionOffset = newProjectionOffset;
        } else {
            this.projectionOffset = null;
        }

        this.intermissionTicks = sanitizeIntermissionTicks(newIntermissionTicks);
        this.showBlueTint = newShowBlueTint;
        this.overlayAntiOcclusion = newOverlayAntiOcclusion;
        this.compatibilityMode = newCompatibilityMode;
        this.projectionMode = newProjectionMode == null ? ProjectorProjectionMode.DEFAULT : newProjectionMode;
        this.miniatureScale = Math.max(0.1F, Math.min(5.0F, newMiniatureScale));
        this.textScale = Math.max(0.1F, Math.min(10.0F, newTextScale));
        syncBlockState();
        syncToClient();
    }

    public void triggerClientManualOnce() {
        if (level == null || !level.isClientSide || !hasRenderableScene()) {
            return;
        }
        clientAutoplayActive = false;
        clientAutoplayKey = "";
        beginClientPlayback(0, false);
    }

    public void seekClientPlaybackToTick(int playbackTick) {
        if (level == null || !level.isClientSide || !hasRenderableScene()) {
            return;
        }
        beginClientPlayback(Math.max(0, playbackTick), clientPlaybackLooping);
    }

    public void refreshRedstoneState(boolean powered) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (updateRedstoneState(powered)) {
            syncBlockState();
            syncToClient();
        }
    }

    public void writeMenuData(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }

    private boolean updateRedstoneState(boolean powered) {
        if (this.redstonePowered == powered) {
            return false;
        }

        this.redstonePowered = powered;

        if (triggerMode.startsOnRedstoneRisingEdge()) {
            if (powered && hasRenderableScene()) {
                notifyClientsToStart(false);
            }
        }

        setChanged();
        return true;
    }

    public boolean shouldPersistAfterPlaybackEnd() {
        return triggerMode.startsOnRedstoneRisingEdge() && redstonePowered && hasRenderableScene();
    }

    private static int sanitizeIntermissionTicks(int ticks) {
        return Math.max(0, ticks);
    }

    private void convertToDisabledChest() {
        if (level == null || level.isClientSide || getBlockState().getBlock() == Blocks.CHEST) {
            return;
        }

        ItemStack recoveredSourceItem = sourceItem.copy();
        ProjectorKind kind = getProjectorKind();
        Direction facing = getBlockState().hasProperty(ProjectorBlock.FACING)
            ? getBlockState().getValue(ProjectorBlock.FACING)
            : Direction.NORTH;

        sourceItem = ItemStack.EMPTY;

        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        level.setBlock(worldPosition, chestState, Block.UPDATE_ALL);
        if (level.getBlockEntity(worldPosition) instanceof ChestBlockEntity chest) {
            if (!recoveredSourceItem.isEmpty()) {
                chest.setItem(13, recoveredSourceItem);
            }
            chest.setChanged();
        }
    }

    private boolean syncBlockState() {
        if (level == null) {
            return false;
        }
        normalizeSourceItem();
        BlockState state = getBlockState();
        if (!state.hasProperty(ProjectorBlock.POWERED) || !state.hasProperty(ProjectorBlock.LIT)) {
            return false;
        }
        boolean desiredPowered = redstonePowered;
        boolean desiredLit = !sourceItem.isEmpty();
        if (state.getValue(ProjectorBlock.POWERED) == desiredPowered && state.getValue(ProjectorBlock.LIT) == desiredLit) {
            return false;
        }
        return level.setBlock(worldPosition,
            state.setValue(ProjectorBlock.POWERED, desiredPowered).setValue(ProjectorBlock.LIT, desiredLit),
            Block.UPDATE_ALL);
    }

    private void syncToClient() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private void normalizeSourceItem() {
        if (sourceItem == null || sourceItem.isEmpty()) {
            sourceItem = ItemStack.EMPTY;
            return;
        }
        if (sourceItem.getCount() != 1) {
            ItemStack normalized = sourceItem.copy();
            normalized.setCount(1);
            sourceItem = normalized;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("ponderer.ui.projector.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return ProjectorMenu.create(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return sourceItem.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == ProjectorMenu.SOURCE_SLOT ? sourceItem : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != ProjectorMenu.SOURCE_SLOT || amount <= 0 || sourceItem.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = sourceItem.split(amount);
        if (sourceItem.isEmpty()) {
            sourceItem = ItemStack.EMPTY;
        }
        onSourceItemChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != ProjectorMenu.SOURCE_SLOT) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = sourceItem;
        sourceItem = ItemStack.EMPTY;
        onSourceItemChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != ProjectorMenu.SOURCE_SLOT) {
            return;
        }
        ItemStack normalized = stack == null ? ItemStack.EMPTY : stack.copy();
        if (!normalized.isEmpty()) {
            normalized.setCount(1);
        }
        boolean changed = !ItemStack.matches(sourceItem, normalized);
        sourceItem = normalized;
        if (changed) {
            onSourceItemChanged();
        } else {
            setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
            && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        if (!sourceItem.isEmpty()) {
            sourceItem = ItemStack.EMPTY;
            onSourceItemChanged();
        }
    }

    private void onSourceItemChanged() {
        if (level == null || level.isClientSide) {
            setChanged();
            return;
        }
        syncBlockState();
        syncToClient();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!sourceItem.isEmpty()) {
            tag.put(TAG_SOURCE_ITEM, sourceItem.saveOptional(registries));
        }
        tag.putString(TAG_TRIGGER_MODE, triggerMode.serializedName());
        if (projectionOffset != null) {
            tag.putLong(TAG_OFFSET, projectionOffset.asLong());
            tag.putBoolean(TAG_OFFSET_WORLD_SPACE, true);
        }
        tag.putBoolean(TAG_REDSTONE, redstonePowered);
        tag.putInt(TAG_INTERMISSION, intermissionTicks);
        tag.putBoolean(TAG_SHOW_BLUE_TINT, showBlueTint);
        tag.putBoolean(TAG_OVERLAY_ANTI_OCCLUSION, overlayAntiOcclusion);
        tag.putBoolean(TAG_COMPATIBILITY_MODE, compatibilityMode);
        tag.putString(TAG_PROJECTION_MODE, projectionMode.serializedName());
        tag.putFloat(TAG_MINIATURE_SCALE, miniatureScale);
        tag.putFloat(TAG_TEXT_SCALE, textScale);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack oldSourceItem = sourceItem.copy();
        ProjectorTriggerMode oldTriggerMode = triggerMode;
        boolean oldRedstonePowered = redstonePowered;
        int oldIntermissionTicks = intermissionTicks;

        super.loadAdditional(tag, registries);
        sourceItem = tag.contains(TAG_SOURCE_ITEM, Tag.TAG_COMPOUND)
            ? ItemStack.parseOptional(registries, tag.getCompound(TAG_SOURCE_ITEM))
            : ItemStack.EMPTY;
        triggerMode = ProjectorTriggerMode.byName(tag.getString(TAG_TRIGGER_MODE));
        if (tag.contains(TAG_OFFSET)) {
            BlockPos loadedOffset = BlockPos.of(tag.getLong(TAG_OFFSET));
            if (getProjectorKind().requiresAnchor() && !tag.getBoolean(TAG_OFFSET_WORLD_SPACE)) {
                loadedOffset = rotateLegacyOffsetByFacing(loadedOffset, getBlockState().getValue(ProjectorBlock.FACING));
            }
            projectionOffset = loadedOffset;
        } else {
            projectionOffset = null;
        }
        redstonePowered = tag.getBoolean(TAG_REDSTONE);
        intermissionTicks = tag.contains(TAG_INTERMISSION)
            ? sanitizeIntermissionTicks(tag.getInt(TAG_INTERMISSION))
            : DEFAULT_INTERMISSION_TICKS;
        showBlueTint = !tag.contains(TAG_SHOW_BLUE_TINT) || tag.getBoolean(TAG_SHOW_BLUE_TINT);
        overlayAntiOcclusion = tag.contains(TAG_OVERLAY_ANTI_OCCLUSION)
            && tag.getBoolean(TAG_OVERLAY_ANTI_OCCLUSION);
        compatibilityMode = !tag.contains(TAG_COMPATIBILITY_MODE) || tag.getBoolean(TAG_COMPATIBILITY_MODE);
        projectionMode = tag.contains(TAG_PROJECTION_MODE)
            ? ProjectorProjectionMode.byName(tag.getString(TAG_PROJECTION_MODE))
            : ProjectorProjectionMode.DEFAULT;
        miniatureScale = tag.contains(TAG_MINIATURE_SCALE) ? tag.getFloat(TAG_MINIATURE_SCALE) : 1.0F;
        textScale = tag.contains(TAG_TEXT_SCALE) ? tag.getFloat(TAG_TEXT_SCALE) : 1.0F;
        boolean sourceChanged = !ItemStack.matches(oldSourceItem, sourceItem);
        boolean triggerModeChanged = oldTriggerMode != triggerMode;
        boolean redstoneChanged = oldRedstonePowered != redstonePowered;
        boolean intermissionChanged = oldIntermissionTicks != intermissionTicks;
        if (sourceChanged || triggerModeChanged || redstoneChanged || intermissionChanged) {
            markClientPlaybackStateDirty(sourceChanged, triggerModeChanged, redstoneChanged, intermissionChanged);
        }
    }

    private void beginClientPlayback(int playbackTick, boolean looping) {
        if (level == null || !level.isClientSide) {
            return;
        }

        this.clientPlaying = true;
        this.clientPlaybackLooping = looping;
        this.clientPlaybackTickSnapshot = Math.max(0, playbackTick);
        this.clientPlaybackStartGameTime = level.getGameTime();
        this.clientPlaybackRevision++;
    }

    private int resolveClientPlaybackTick(long currentGameTime, int totalDurationTicks) {
        long absoluteTick = resolveAbsoluteClientPlaybackTick(currentGameTime);
        return ProjectorSceneTimeline.resolvePlaybackTick(
            absoluteTick,
            totalDurationTicks,
            clientPlaybackLooping,
            shouldPersistAfterPlaybackEnd(),
            FINAL_EXTRA_TICKS);
    }

    private boolean shouldStopClientPlayback(long currentGameTime, int totalDurationTicks) {
        long absoluteTick = resolveAbsoluteClientPlaybackTick(currentGameTime);
        return ProjectorSceneTimeline.shouldStopPlayback(
            absoluteTick,
            totalDurationTicks,
            clientPlaybackLooping,
            shouldPersistAfterPlaybackEnd(),
            FINAL_EXTRA_TICKS);
    }

    private boolean isClientPlaybackFrozen(long currentGameTime, int totalDurationTicks) {
        long absoluteTick = resolveAbsoluteClientPlaybackTick(currentGameTime);
        return ProjectorSceneTimeline.isPlaybackFrozen(
            absoluteTick,
            totalDurationTicks,
            clientPlaybackLooping,
            shouldPersistAfterPlaybackEnd(),
            FINAL_EXTRA_TICKS);
    }

    private long resolveAbsoluteClientPlaybackTick(long currentGameTime) {
        long elapsed = Math.max(0L, currentGameTime - clientPlaybackStartGameTime);
        return Math.max(0L, (long) clientPlaybackTickSnapshot + elapsed);
    }

    public void startClientPlaybackFromServerSignal(boolean looping) {
        if (level == null || !level.isClientSide || !hasRenderableScene()) {
            return;
        }
        clientAutoplayActive = false;
        clientAutoplayKey = "";
        beginClientPlayback(0, looping);
    }

    private void syncClientPlaybackFromServerState() {
        if (level == null || !level.isClientSide) {
            return;
        }

        if (!hasRenderableScene()) {
            stopClientPlayback();
            return;
        }

        boolean shouldAutoplay = shouldClientAutoplay(triggerMode, redstonePowered);
        if (!shouldAutoplay) {
            if (clientAutoplayActive) {
                stopClientPlayback();
            }
            return;
        }

        String desiredAutoplayKey = clientAutoplayStateKey(triggerMode, clientPlaybackStateToken);
        if (!clientAutoplayActive
            || !desiredAutoplayKey.equals(clientAutoplayKey)
            || !clientPlaying
            || !clientPlaybackLooping) {
            clientAutoplayActive = true;
            clientAutoplayKey = desiredAutoplayKey;
            beginClientPlayback(0, true);
        }
    }

    private void stopClientPlayback() {
        clientPlaying = false;
        clientAutoplayActive = false;
        clientAutoplayKey = "";
        if (level != null && level.isClientSide) {
            ProjectorPlaybackState.clear(worldPosition);
        }
    }

    static boolean shouldClientAutoplay(@Nullable ProjectorTriggerMode triggerMode, boolean redstonePowered) {
        ProjectorTriggerMode resolvedTriggerMode = triggerMode == null ? ProjectorTriggerMode.MANUAL_LOOP : triggerMode;
        return resolvedTriggerMode == ProjectorTriggerMode.MANUAL_LOOP
            || (resolvedTriggerMode.runsWhilePowered() && redstonePowered);
    }

    static String clientAutoplayStateKey(@Nullable ProjectorTriggerMode triggerMode, int clientPlaybackStateToken) {
        ProjectorTriggerMode resolvedTriggerMode = triggerMode == null ? ProjectorTriggerMode.MANUAL_LOOP : triggerMode;
        return resolvedTriggerMode.serializedName() + ":" + clientPlaybackStateToken;
    }

    static boolean shouldBumpClientPlaybackStateToken(boolean sourceChanged, boolean triggerModeChanged,
                                                      boolean redstoneChanged, boolean intermissionChanged) {
        // Redstone-only sync should stop/start redstone-driven autoplay without rewinding manual playback.
        return sourceChanged || triggerModeChanged || intermissionChanged;
    }

    private void markClientPlaybackStateDirty(boolean sourceChanged, boolean triggerModeChanged,
                                              boolean redstoneChanged, boolean intermissionChanged) {
        if (shouldBumpClientPlaybackStateToken(sourceChanged, triggerModeChanged, redstoneChanged, intermissionChanged)) {
            clientPlaybackStateToken++;
        }
        if ((sourceChanged || triggerModeChanged) && clientPlaying && !clientAutoplayActive) {
            stopClientPlayback();
        }
    }

    private void notifyClientsToStart(boolean looping) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ProjectorPlaybackStartPayload payload = new ProjectorPlaybackStartPayload(worldPosition, looping);
        for (ServerPlayer player : serverLevel.players()) {
            PondererServices.NETWORK.sendToPlayer(player, payload);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
