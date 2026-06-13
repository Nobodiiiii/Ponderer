package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.projector.client.ProjectorRenderBounds;
import com.nododiiiii.ponderer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ProjectorBlockEntity extends BlockEntity implements Container, MenuProvider {

    private static final String TAG_LEGACY_SCENE_KEY = "SceneKey";
    private static final String TAG_SCENE_KEYS = "SceneKeys";
    private static final String TAG_SOURCE_ITEM = "SourceItem";
    private static final String TAG_TRIGGER_MODE = "TriggerMode";
    private static final String TAG_ANCHOR = "AnchorPos";
    private static final String TAG_REDSTONE = "RedstonePowered";
    private static final String TAG_PLAYING = "Playing";
    private static final String TAG_PLAYBACK_LOOPING = "PlaybackLooping";
    private static final String TAG_SUPPRESS_AUTO_LOOP = "SuppressAutoLoop";
    private static final String TAG_START_TIME = "PlaybackStartGameTime";
    private static final String TAG_REVISION = "PlaybackRevision";
    private static final String TAG_DURATION = "PlaybackDurationTicks";
    private static final String TAG_SHOW_BLUE_TINT = "ShowBlueTint";
    private static final String TAG_MINIATURE_SCALE = "MiniatureScale";
    private static final int FALLBACK_ONCE_DURATION_TICKS = 20 * 60;

    private ItemStack sourceItem = ItemStack.EMPTY;
    private List<String> sceneKeys = List.of();
    @Nullable
    private BlockPos anchorPos;
    private ProjectorTriggerMode triggerMode = ProjectorTriggerMode.MANUAL_LOOP;
    private boolean redstonePowered;
    private boolean playing;
    private boolean playbackLooping = true;
    private boolean suppressAutoLoop;
    private long playbackStartGameTime;
    private int playbackRevision;
    private int playbackDurationTicks;
    private boolean showBlueTint = true;
    private float miniatureScale = 1.0F;

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

        boolean changed = projector.updateRedstoneState(level.hasNeighborSignal(pos), level.getGameTime());

        if (projector.triggerMode == ProjectorTriggerMode.MANUAL_LOOP
            && projector.hasRenderableScene()
            && !projector.playing
            && !projector.suppressAutoLoop) {
            projector.startPlayback(level.getGameTime(), true);
            changed = true;
        }

        if (!projector.hasRenderableScene() && projector.playing) {
            projector.stopPlayback();
            changed = true;
        }

        if (projector.playing && !projector.playbackLooping && projector.playbackDurationTicks > 0) {
            long elapsed = Math.max(0L, level.getGameTime() - projector.playbackStartGameTime);
            if (elapsed >= projector.playbackDurationTicks) {
                projector.stopPlayback();
                changed = true;
            }
        }

        if (changed) {
            projector.syncBlockState();
            projector.syncToClient();
        }
    }

    public ProjectorKind getProjectorKind() {
        return ProjectorKind.fromState(getBlockState());
    }

    public ItemStack getSourceItem() {
        return sourceItem.copy();
    }

    public List<String> getSceneKeys() {
        return sceneKeys;
    }

    public String getSceneKey() {
        return sceneKeys.isEmpty() ? "" : sceneKeys.get(0);
    }

    @Nullable
    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    public ProjectorTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPlaybackLooping() {
        return playbackLooping;
    }

    public boolean isRedstonePowered() {
        return redstonePowered;
    }

    public long getPlaybackStartGameTime() {
        return playbackStartGameTime;
    }

    public int getPlaybackRevision() {
        return playbackRevision;
    }

    public int getPlaybackDurationTicks() {
        return playbackDurationTicks;
    }

    public boolean showBlueTint() {
        return showBlueTint;
    }

    public float getMiniatureScale() {
        return miniatureScale;
    }

    public AABB getRenderBoundingBox() {
        if (level != null && level.isClientSide) {
            return ProjectorRenderBounds.estimate(this);
        }
        return new AABB(worldPosition).inflate(1.0D);
    }

    public boolean hasRenderableScene() {
        if (sourceItem.isEmpty() || sceneKeys.isEmpty()) {
            return false;
        }
        return !getProjectorKind().requiresAnchor() || anchorPos != null;
    }

    public void applyConfig(List<String> newSceneKeys, ProjectorTriggerMode newMode,
                            @Nullable BlockPos newAnchorPos, int newPlaybackDurationTicks,
                            boolean newShowBlueTint, float newMiniatureScale) {
        this.sceneKeys = sourceItem.isEmpty() ? List.of() : resolveSceneKeys(newSceneKeys);
        this.triggerMode = newMode == null ? ProjectorTriggerMode.MANUAL_LOOP : newMode;
        this.anchorPos = getProjectorKind().requiresAnchor() ? newAnchorPos : null;
        this.playbackDurationTicks = Math.max(0, newPlaybackDurationTicks);
        this.showBlueTint = newShowBlueTint;
        this.miniatureScale = Math.max(0.1F, Math.min(5.0F, newMiniatureScale));
        if (this.playbackDurationTicks <= 0 && !this.sceneKeys.isEmpty()) {
            this.playbackDurationTicks = estimateDurationOrFallback();
        }
        this.suppressAutoLoop = false;

        refreshPlaybackAfterConfigChange();
        syncBlockState();
        syncToClient();
    }

    public void triggerManualOnce() {
        if (level == null || level.isClientSide || !hasRenderableScene()) {
            return;
        }
        if (playbackDurationTicks <= 0) {
            playbackDurationTicks = estimateDurationOrFallback();
        }
        suppressAutoLoop = true;
        startPlayback(level.getGameTime(), false);
        syncBlockState();
        syncToClient();
    }

    public void refreshRedstoneState(boolean powered) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (updateRedstoneState(powered, level.getGameTime())) {
            syncBlockState();
            syncToClient();
        }
    }

    public void writeMenuData(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }

    private void refreshPlaybackAfterConfigChange() {
        if (!hasRenderableScene()) {
            stopPlayback();
        } else if (triggerMode == ProjectorTriggerMode.MANUAL_LOOP) {
            startPlayback(level == null ? 0L : level.getGameTime(), true);
        } else if (triggerMode.runsWhilePowered()) {
            if (redstonePowered) {
                startPlayback(level == null ? 0L : level.getGameTime(), true);
            } else {
                stopPlayback();
            }
        } else {
            stopPlayback();
        }
    }

    private boolean updateRedstoneState(boolean powered, long gameTime) {
        if (this.redstonePowered == powered) {
            return false;
        }

        this.redstonePowered = powered;

        if (triggerMode.startsOnRedstoneRisingEdge() && powered && hasRenderableScene()) {
            playbackDurationTicks = estimateDurationOrFallback();
            suppressAutoLoop = false;
            startPlayback(gameTime, false);
        } else if (triggerMode.runsWhilePowered()) {
            if (powered && hasRenderableScene()) {
                suppressAutoLoop = false;
                startPlayback(gameTime, true);
            } else {
                stopPlayback();
            }
        } else {
            setChanged();
        }

        return true;
    }

    private void startPlayback(long gameTime, boolean looping) {
        this.playing = true;
        this.playbackLooping = looping;
        this.playbackStartGameTime = gameTime;
        this.playbackRevision++;
        setChanged();
    }

    private int estimateDurationOrFallback() {
        int total = 0;
        for (String sceneKey : sceneKeys) {
            total += Math.max(0, ProjectorSceneTimeline.estimateTotalTicks(sceneKey));
        }
        return total > 0 ? total : FALLBACK_ONCE_DURATION_TICKS;
    }

    private void stopPlayback() {
        if (!this.playing) {
            setChanged();
            return;
        }
        this.playing = false;
        setChanged();
    }

    private void syncBlockState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        boolean desiredPowered = redstonePowered;
        boolean desiredLit = playing && hasRenderableScene();
        if (state.getValue(ProjectorBlock.POWERED) == desiredPowered && state.getValue(ProjectorBlock.LIT) == desiredLit) {
            return;
        }
        level.setBlock(worldPosition,
            state.setValue(ProjectorBlock.POWERED, desiredPowered).setValue(ProjectorBlock.LIT, desiredLit),
            Block.UPDATE_CLIENTS);
    }

    private void syncToClient() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static List<String> sanitizeSceneKeys(List<String> rawKeys) {
        if (rawKeys == null || rawKeys.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String key : rawKeys) {
            if (key == null || key.isBlank() || cleaned.contains(key.trim())) {
                continue;
            }
            cleaned.add(key.trim());
        }
        return List.copyOf(cleaned);
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
        sceneKeys = sourceItem.isEmpty() ? List.of() : resolveSceneKeys(List.of());
        playbackDurationTicks = sceneKeys.isEmpty() ? 0 : estimateDurationOrFallback();
        suppressAutoLoop = false;
        if (level == null || level.isClientSide) {
            setChanged();
            return;
        }
        refreshPlaybackAfterConfigChange();
        syncBlockState();
        syncToClient();
    }

    private List<String> resolveSceneKeys(List<String> fallbackSceneKeys) {
        List<String> fallback = sanitizeSceneKeys(fallbackSceneKeys);
        if (!fallback.isEmpty()) {
            return fallback;
        }
        return ProjectorSceneResolver.sceneKeysFor(sourceItem);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!sourceItem.isEmpty()) {
            tag.put(TAG_SOURCE_ITEM, sourceItem.save(new CompoundTag()));
        }
        tag.putString(TAG_LEGACY_SCENE_KEY, getSceneKey());
        ListTag keyList = new ListTag();
        for (String sceneKey : sceneKeys) {
            keyList.add(StringTag.valueOf(sceneKey));
        }
        tag.put(TAG_SCENE_KEYS, keyList);
        tag.putString(TAG_TRIGGER_MODE, triggerMode.serializedName());
        if (anchorPos != null) {
            tag.putLong(TAG_ANCHOR, anchorPos.asLong());
        }
        tag.putBoolean(TAG_REDSTONE, redstonePowered);
        tag.putBoolean(TAG_PLAYING, playing);
        tag.putBoolean(TAG_PLAYBACK_LOOPING, playbackLooping);
        tag.putBoolean(TAG_SUPPRESS_AUTO_LOOP, suppressAutoLoop);
        tag.putLong(TAG_START_TIME, playbackStartGameTime);
        tag.putInt(TAG_REVISION, playbackRevision);
        tag.putInt(TAG_DURATION, playbackDurationTicks);
        tag.putBoolean(TAG_SHOW_BLUE_TINT, showBlueTint);
        tag.putFloat(TAG_MINIATURE_SCALE, miniatureScale);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        sourceItem = tag.contains(TAG_SOURCE_ITEM, Tag.TAG_COMPOUND)
            ? ItemStack.of(tag.getCompound(TAG_SOURCE_ITEM))
            : ItemStack.EMPTY;
        sceneKeys = loadSceneKeys(tag);
        triggerMode = ProjectorTriggerMode.byName(tag.getString(TAG_TRIGGER_MODE));
        anchorPos = tag.contains(TAG_ANCHOR) ? BlockPos.of(tag.getLong(TAG_ANCHOR)) : null;
        redstonePowered = tag.getBoolean(TAG_REDSTONE);
        playing = tag.getBoolean(TAG_PLAYING);
        playbackLooping = tag.contains(TAG_PLAYBACK_LOOPING) ? tag.getBoolean(TAG_PLAYBACK_LOOPING) : triggerMode.loops();
        suppressAutoLoop = tag.getBoolean(TAG_SUPPRESS_AUTO_LOOP);
        playbackStartGameTime = tag.getLong(TAG_START_TIME);
        playbackRevision = tag.getInt(TAG_REVISION);
        playbackDurationTicks = tag.getInt(TAG_DURATION);
        showBlueTint = !tag.contains(TAG_SHOW_BLUE_TINT) || tag.getBoolean(TAG_SHOW_BLUE_TINT);
        miniatureScale = tag.contains(TAG_MINIATURE_SCALE) ? tag.getFloat(TAG_MINIATURE_SCALE) : 1.0F;
    }

    private static List<String> loadSceneKeys(CompoundTag tag) {
        List<String> loaded = new ArrayList<>();
        if (tag.contains(TAG_SCENE_KEYS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_SCENE_KEYS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                String key = list.getString(i);
                if (!key.isBlank() && !loaded.contains(key)) {
                    loaded.add(key);
                }
            }
        }

        if (loaded.isEmpty()) {
            String legacyKey = tag.getString(TAG_LEGACY_SCENE_KEY);
            if (!legacyKey.isBlank()) {
                loaded.add(legacyKey);
            }
        }

        return List.copyOf(loaded);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
