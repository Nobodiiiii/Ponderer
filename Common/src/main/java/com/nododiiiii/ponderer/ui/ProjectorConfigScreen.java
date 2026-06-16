package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.network.ProjectorConfigUpdatePayload;
import com.nododiiiii.ponderer.network.ProjectorManualTriggerPayload;
import com.nododiiiii.ponderer.network.ProjectorSeekPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorMenu;
import com.nododiiiii.ponderer.projector.ProjectorTriggerMode;
import com.nododiiiii.ponderer.projector.client.ProjectorClientSceneResolver;
import com.nododiiiii.ponderer.projector.client.ProjectorSceneBundle;
import com.nododiiiii.ponderer.projector.client.ProjectorSceneCompiler;
import com.nododiiiii.ponderer.projector.ProjectorSceneTimeline;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProjectorConfigScreen extends AbstractContainerScreen<ProjectorMenu> {

    private static final ResourceLocation BACKGROUND =
        new ResourceLocation("ponderer", "textures/gui/projector.png");
    private static final ResourceLocation PLAYER_INVENTORY =
        new ResourceLocation("ponderer", "textures/gui/player_inventory.png");
    private static final int TEXT = 0x4A2D31;
    private static final int PLAYER_INVENTORY_WIDTH = 176;
    private static final int PLAYER_INVENTORY_HEIGHT = 108;
    private static final int PLAYER_INVENTORY_TEXTURE_SIZE = 256;
    private static final int PLAYER_INVENTORY_X = ProjectorMenu.INVENTORY_PANEL_X;
    private static final int PLAYER_INVENTORY_Y = 180;
    private static final int SCREEN_WIDTH = ProjectorMenu.SCREEN_WIDTH;
    private static final int SCREEN_HEIGHT = PLAYER_INVENTORY_Y + PLAYER_INVENTORY_HEIGHT;
    private static final int MAIN_PANEL_HEIGHT = 180;
    private static final int TITLE_Y = 6;
    private static final int WHITEBOARD_X = 10;
    private static final int WHITEBOARD_Y = 18;
    private static final int WHITEBOARD_WIDTH = SCREEN_WIDTH - 20;
    private static final int WHITEBOARD_HEIGHT = 148;
    private static final int FIELD_PADDING_X = 12;
    private static final int ROW_GAP = 26;
    private static final int COLUMN_GAP = 20;
    private static final int FIELD_COLUMN_WIDTH = 133;
    private static final int LABEL_WIDTH = 52;
    private static final int VALUE_WIDTH = FIELD_COLUMN_WIDTH - LABEL_WIDTH;
    private static final int SOURCE_LABEL_X = WHITEBOARD_X + FIELD_PADDING_X;
    private static final int SOURCE_ROW_Y = WHITEBOARD_Y + 14;
    private static final int SOURCE_TEXT_X = ProjectorMenu.SOURCE_SLOT_X + 28;
    private static final int SOURCE_TEXT_WIDTH = 148;
    private static final int INVENTORY_LABEL_X = PLAYER_INVENTORY_X + 8;
    private static final int INVENTORY_LABEL_Y = PLAYER_INVENTORY_Y + 6;
    private static final int LEFT_COLUMN_X = WHITEBOARD_X + FIELD_PADDING_X;
    private static final int RIGHT_COLUMN_X = LEFT_COLUMN_X + FIELD_COLUMN_WIDTH + COLUMN_GAP;
    private static final int ROW_1_Y = SOURCE_ROW_Y + ROW_GAP;
    private static final int ROW_2_Y = ROW_1_Y + ROW_GAP;
    private static final int ROW_3_Y = ROW_2_Y + ROW_GAP;
    private static final int ROW_4_Y = ROW_3_Y + ROW_GAP;
    private static final int ACTION_ROW_TOP_Y = ROW_3_Y - 2;
    private static final int ACTION_ROW_BOTTOM_Y = ROW_4_Y - 2;
    private static final int SCENE_BUTTON_SIZE = 20;
    private static final int SCENE_BUTTON_Y = SOURCE_ROW_Y - 2;
    private static final int SCENE_LEFT_BUTTON_X = SOURCE_TEXT_X;
    private static final int SCENE_RIGHT_BUTTON_X = WHITEBOARD_X + WHITEBOARD_WIDTH - FIELD_PADDING_X - SCENE_BUTTON_SIZE;
    private static final int SCENE_TITLE_X = SCENE_LEFT_BUTTON_X + SCENE_BUTTON_SIZE + 6;
    private static final int SCENE_TITLE_Y = SOURCE_ROW_Y + 1;
    private static final int SCENE_BAR_X = SCENE_TITLE_X;
    private static final int SCENE_BAR_Y = SOURCE_ROW_Y + 14;
    private static final int SCENE_BAR_WIDTH = SCENE_RIGHT_BUTTON_X - SCENE_BAR_X - 4;
    private static final int SCENE_BAR_HEIGHT = 1;
    private static final int SCENE_KEYFRAME_HIT_RADIUS = 6;
    private static final int OFFSET_BOX_WIDTH = 24;
    private static final int OFFSET_GAP = 4;
    private static final int OFFSET_TOTAL_WIDTH = OFFSET_BOX_WIDTH * 3 + OFFSET_GAP * 2;
    private static final int OFFSET_START_X = LEFT_COLUMN_X + LABEL_WIDTH + (VALUE_WIDTH - OFFSET_TOTAL_WIDTH) / 2;

    private CycleButton<Boolean> redstoneModeButton;
    private CycleButton<Boolean> loopModeButton;
    private CycleButton<Boolean> blueTintButton;
    private Button playButton;
    @Nullable
    private PonderButton previousSceneButton;
    @Nullable
    private PonderButton nextSceneButton;
    @Nullable
    private Button resetOffsetButton;
    @Nullable
    private EditBox offsetX;
    @Nullable
    private EditBox offsetY;
    @Nullable
    private EditBox offsetZ;
    @Nullable
    private EditBox scaleBox;
    @Nullable
    private EditBox textScaleBox;
    @Nullable
    private EditBox intermissionBox;

    private List<String> resolvedSceneKeys = List.of();
    private List<String> configuredSceneKeys = List.of();
    private String sourceFingerprint = "";
    private int selectedSceneIndex;
    private boolean sceneSelectionDirty;
    private int previewTick;
    private int previewIntermissionTicks = Integer.MIN_VALUE;
    private ScenePreview scenePreview = ScenePreview.EMPTY;
    private boolean redstoneMode;
    private boolean loopMode = true;
    private boolean showBlueTint = true;
    private float miniatureScale = 1.0F;
    private float textScale = 1.0F;
    private int intermissionTicks = ProjectorBlockEntity.DEFAULT_INTERMISSION_TICKS;
    private Component statusMessage = Component.empty();
    private int statusColor = 0x606060;

    public ProjectorConfigScreen(ProjectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SCREEN_WIDTH;
        imageHeight = SCREEN_HEIGHT;
        inventoryLabelX = INVENTORY_LABEL_X;
        inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = WHITEBOARD_X + 8;
        titleLabelY = TITLE_Y;
        loadProjectorState();
        configuredSceneKeys = menu.projector() == null ? List.of() : List.copyOf(menu.projector().getSceneKeys());

        redstoneModeButton = addRenderableWidget(modeButton(
            leftPos + LEFT_COLUMN_X + LABEL_WIDTH,
            topPos + ROW_1_Y - 2,
            "ponderer.ui.projector.redstone_mode",
            redstoneMode,
            value -> Component.translatable("ponderer.ui.projector.redstone_mode." + (value ? "redstone" : "manual")),
            value -> redstoneMode = value));
        loopModeButton = addRenderableWidget(modeButton(
            leftPos + RIGHT_COLUMN_X + LABEL_WIDTH,
            topPos + ROW_1_Y - 2,
            "ponderer.ui.projector.loop_mode",
            loopMode,
            value -> Component.translatable("ponderer.ui.projector.loop_mode." + (value ? "loop" : "once")),
            value -> loopMode = value));
        previousSceneButton = addRenderableWidget(new PonderButton(leftPos + SCENE_LEFT_BUTTON_X, topPos + SCENE_BUTTON_Y)
            .showing(PonderGuiTextures.ICON_PONDER_LEFT)
            .withCallback(this::cycleSceneBackward));
        nextSceneButton = addRenderableWidget(new PonderButton(leftPos + SCENE_RIGHT_BUTTON_X, topPos + SCENE_BUTTON_Y)
            .showing(PonderGuiTextures.ICON_PONDER_RIGHT)
            .withCallback(this::cycleSceneForward));

        if (menu.projectorKind().requiresAnchor()) {
            int offsetXStart = leftPos + OFFSET_START_X;
            offsetX = addRenderableWidget(offsetBox(offsetXStart, topPos + ROW_2_Y - 1));
            offsetY = addRenderableWidget(offsetBox(offsetXStart + OFFSET_BOX_WIDTH + OFFSET_GAP, topPos + ROW_2_Y - 1));
            offsetZ = addRenderableWidget(offsetBox(offsetXStart + (OFFSET_BOX_WIDTH + OFFSET_GAP) * 2, topPos + ROW_2_Y - 1));
            textScaleBox = addRenderableWidget(decimalBox(
                leftPos + RIGHT_COLUMN_X + LABEL_WIDTH, topPos + ROW_2_Y - 1, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.text_scale"), textScale));

            blueTintButton = addRenderableWidget(modeButton(
                leftPos + LEFT_COLUMN_X + LABEL_WIDTH, topPos + ROW_3_Y - 2,
                "ponderer.ui.projector.blue_tint", showBlueTint,
                value -> Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off")),
                value -> showBlueTint = value));
            intermissionBox = addRenderableWidget(integerBox(
                leftPos + LEFT_COLUMN_X + LABEL_WIDTH, topPos + ROW_4_Y - 1, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.intermission"), intermissionTicks));
            resetOffsetButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.reset_offset"),
                button -> resetOffset()).bounds(leftPos + RIGHT_COLUMN_X + LABEL_WIDTH, topPos + ACTION_ROW_TOP_Y, VALUE_WIDTH, 20).build());
            playButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce()).bounds(leftPos + RIGHT_COLUMN_X + LABEL_WIDTH, topPos + ACTION_ROW_BOTTOM_Y, VALUE_WIDTH, 20).build());
        } else {
            scaleBox = addRenderableWidget(decimalBox(
                leftPos + LEFT_COLUMN_X + LABEL_WIDTH, topPos + ROW_2_Y - 1, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.scale"), miniatureScale));
            textScaleBox = addRenderableWidget(decimalBox(
                leftPos + RIGHT_COLUMN_X + LABEL_WIDTH, topPos + ROW_2_Y - 1, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.text_scale"), textScale));

            blueTintButton = addRenderableWidget(modeButton(
                leftPos + LEFT_COLUMN_X + LABEL_WIDTH, topPos + ROW_3_Y - 2,
                "ponderer.ui.projector.blue_tint", showBlueTint,
                value -> Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off")),
                value -> showBlueTint = value));
            intermissionBox = addRenderableWidget(integerBox(
                leftPos + RIGHT_COLUMN_X + LABEL_WIDTH, topPos + ROW_3_Y - 1, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.intermission"), intermissionTicks));
            playButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce()).bounds(leftPos + RIGHT_COLUMN_X + LABEL_WIDTH, topPos + ACTION_ROW_BOTTOM_Y, VALUE_WIDTH, 20).build());
        }

        ProjectorBlockEntity projector = menu.projector();
        if (menu.projectorKind().requiresAnchor()) {
            setOffset(projector != null
                ? projector.getEffectiveProjectionOffset()
                : ProjectorBlockEntity.defaultProjectionOffset(projectorFacing()));
        }

        refreshResolvedScenes();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        tickBox(offsetX);
        tickBox(offsetY);
        tickBox(offsetZ);
        tickBox(scaleBox);
        tickBox(textScaleBox);
        tickBox(intermissionBox);

        String current = fingerprint(menu.sourceItem());
        if (!current.equals(sourceFingerprint)) {
            refreshResolvedScenes();
        } else {
            refreshScenePreviewIfNeeded();
        }
    }

    @Override
    public void onClose() {
        if (isConfigDirty()) {
            applyConfig(false);
        }
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clickSceneTimeline(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(BACKGROUND, x, y, 0, 0, SCREEN_WIDTH, MAIN_PANEL_HEIGHT, SCREEN_WIDTH, MAIN_PANEL_HEIGHT);
        graphics.blit(PLAYER_INVENTORY, x + PLAYER_INVENTORY_X, y + PLAYER_INVENTORY_Y,
            0, 0, PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT,
            PLAYER_INVENTORY_TEXTURE_SIZE, PLAYER_INVENTORY_TEXTURE_SIZE);
        renderSceneTimeline(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        Component projectorKind = Component.translatable(menu.projectorKind().translationKey());
        graphics.drawString(font, projectorKind, imageWidth - 14 - font.width(projectorKind), TITLE_Y, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        drawRowLabel(graphics, "ponderer.ui.projector.source_item", SOURCE_LABEL_X, SOURCE_ROW_Y + 4);
        graphics.drawString(font, trimToWidth(sceneSummaryLabel(), SCENE_BAR_WIDTH), SCENE_TITLE_X,
            SCENE_TITLE_Y, sceneSummaryColor(), false);

        drawRowLabel(graphics, "ponderer.ui.projector.redstone_mode", LEFT_COLUMN_X, ROW_1_Y + 4);
        drawRowLabel(graphics, "ponderer.ui.projector.loop_mode", RIGHT_COLUMN_X, ROW_1_Y + 4);

        if (menu.projectorKind().requiresAnchor()) {
            graphics.drawString(font, Component.translatable("ponderer.ui.projector.offset"), LEFT_COLUMN_X, ROW_2_Y + 4, TEXT, false);
            drawRowLabel(graphics, "ponderer.ui.projector.text_scale", RIGHT_COLUMN_X, ROW_2_Y + 4);
            drawRowLabel(graphics, "ponderer.ui.projector.blue_tint", LEFT_COLUMN_X, ROW_3_Y + 4);
            drawRowLabel(graphics, "ponderer.ui.projector.intermission", LEFT_COLUMN_X, ROW_4_Y + 4);
        } else {
            drawRowLabel(graphics, "ponderer.ui.projector.scale", LEFT_COLUMN_X, ROW_2_Y + 4);
            drawRowLabel(graphics, "ponderer.ui.projector.text_scale", RIGHT_COLUMN_X, ROW_2_Y + 4);
            drawRowLabel(graphics, "ponderer.ui.projector.blue_tint", LEFT_COLUMN_X, ROW_3_Y + 4);
            drawRowLabel(graphics, "ponderer.ui.projector.intermission", RIGHT_COLUMN_X, ROW_3_Y + 4);
        }

        if (!statusMessage.getString().isBlank()) {
            graphics.drawString(font, trimToWidth(statusMessage, WHITEBOARD_WIDTH - 16),
                WHITEBOARD_X + 8, WHITEBOARD_Y + WHITEBOARD_HEIGHT - 12, statusColor, false);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void loadProjectorState() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector == null) {
            applyMode(ProjectorTriggerMode.MANUAL_LOOP);
            return;
        }

        applyMode(projector.getTriggerMode());
        showBlueTint = projector.showBlueTint();
        miniatureScale = projector.getMiniatureScale();
        textScale = projector.getTextScale();
        intermissionTicks = projector.getIntermissionTicks();
    }

    private void applyMode(ProjectorTriggerMode mode) {
        redstoneMode = mode.usesRedstone();
        loopMode = mode.loops();
    }

    private CycleButton<Boolean> modeButton(int x, int y, String labelKey, boolean initialValue,
                                            java.util.function.Function<Boolean, Component> valueLabel,
                                            java.util.function.Consumer<Boolean> setter) {
        return CycleButton.builder(valueLabel)
            .withValues(Boolean.FALSE, Boolean.TRUE)
            .withInitialValue(initialValue)
            .displayOnlyValue()
            .create(x, y, VALUE_WIDTH, 20, Component.translatable(labelKey), (button, value) -> setter.accept(value));
    }

    private EditBox offsetBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, OFFSET_BOX_WIDTH, 18, Component.translatable("ponderer.ui.projector.offset"));
        box.setMaxLength(6);
        box.setTextColor(0xFFFFFF);
        box.setFilter(value -> value.isEmpty() || "-".equals(value) || value.matches("-?\\d+"));
        box.setHint(Component.literal("0"));
        return box;
    }

    private EditBox decimalBox(int x, int y, int width, Component narration, float initialValue) {
        EditBox box = new EditBox(font, x, y, width, 18, narration);
        box.setMaxLength(6);
        box.setTextColor(0xFFFFFF);
        box.setFilter(value -> value.isEmpty() || "-".equals(value) || ".".equals(value) || "-.".equals(value)
            || value.matches("-?\\d*(\\.\\d*)?"));
        box.setValue(trimFloat(initialValue));
        return box;
    }

    private EditBox integerBox(int x, int y, int width, Component narration, int initialValue) {
        EditBox box = new EditBox(font, x, y, width, 18, narration);
        box.setMaxLength(6);
        box.setTextColor(0xFFFFFF);
        box.setFilter(value -> value.isEmpty() || value.matches("\\d+"));
        box.setValue(String.valueOf(Math.max(0, initialValue)));
        return box;
    }

    private void setOffset(BlockPos offset) {
        if (offsetX != null) {
            offsetX.setValue(String.valueOf(offset.getX()));
        }
        if (offsetY != null) {
            offsetY.setValue(String.valueOf(offset.getY()));
        }
        if (offsetZ != null) {
            offsetZ.setValue(String.valueOf(offset.getZ()));
        }
    }

    private void tickBox(@Nullable EditBox box) {
        if (box != null) {
            box.tick();
        }
    }

    private void refreshResolvedScenes() {
        String previousSelectedSceneKey = selectedSceneKey();
        sourceFingerprint = fingerprint(menu.sourceItem());
        resolvedSceneKeys = ProjectorClientSceneResolver.sceneKeysFor(menu.sourceItem());
        List<String> projectorSceneKeys = currentProjectorSceneKeys();
        if (!projectorSceneKeys.isEmpty()) {
            configuredSceneKeys = List.copyOf(projectorSceneKeys);
        }
        if (!resolvedSceneKeys.containsAll(configuredSceneKeys)) {
            configuredSceneKeys = List.copyOf(resolvedSceneKeys);
            sceneSelectionDirty = false;
        }
        selectSceneAfterRefresh(previousSelectedSceneKey);
        refreshSceneButtons();
        playButton.active = !resolveSceneKeysToSave().isEmpty();
        refreshScenePreview(true);
        tryAutoSyncResolvedScenes();

        if (menu.sourceItem().isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.insert_item"), 0x606060);
            return;
        }
        if (resolvedSceneKeys.isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.no_scenes_for_item"), 0xA03030);
            return;
        }

        status(Component.translatable("ponderer.ui.projector.scenes_detected", resolvedSceneKeys.size()), 0x2E6E2E);
    }

    /**
     * Native Java ponder scenes are discovered on the client. When the projector slot changes,
     * sync those resolved keys back to the server immediately so playback starts without needing
     * a manual scene-switch click.
     */
    private void tryAutoSyncResolvedScenes() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector == null || menu.sourceItem().isEmpty() || resolvedSceneKeys.isEmpty()) {
            return;
        }
        if (!projector.getSceneKeys().isEmpty()) {
            return;
        }

        List<String> sceneKeysToSave = resolveSceneKeysToSave();
        if (sceneKeysToSave.isEmpty()) {
            return;
        }

        sendConfigUpdate(sceneKeysToSave, projector.getTriggerMode(), projector.getProjectionOffset(),
            projector.getIntermissionTicks(), projector.showBlueTint(), projector.getMiniatureScale(),
            projector.getTextScale());
        configuredSceneKeys = List.copyOf(sceneKeysToSave);
        sceneSelectionDirty = false;
    }

    private boolean applyConfig(boolean showStatus) {
        List<String> sceneKeysToSave = resolveSceneKeysToSave();
        BlockPos offset = resolveProjectionOffsetToSave();
        if (requiresMissingOffset()) {
            status(Component.translatable("ponderer.ui.projector.offset.required"), 0xA03030);
            return false;
        }

        float scale = parseScale();
        float resolvedTextScale = parseTextScale();
        int resolvedIntermissionTicks = parseIntermissionTicks();
        sendConfigUpdate(sceneKeysToSave, ProjectorTriggerMode.fromFields(redstoneMode, loopMode), offset,
            resolvedIntermissionTicks, showBlueTint, scale, resolvedTextScale);
        configuredSceneKeys = List.copyOf(sceneKeysToSave);
        sceneSelectionDirty = false;
        if (showStatus) {
            status(Component.translatable("ponderer.ui.projector.saved"), 0x2E6E2E);
        }
        return true;
    }

    private void sendConfigUpdate(List<String> sceneKeys, ProjectorTriggerMode triggerMode, @Nullable BlockPos offset,
                                  int intermissionTicks, boolean blueTint, float scale, float resolvedTextScale) {
        PondererServices.NETWORK.sendToServer(new ProjectorConfigUpdatePayload(
            menu.projectorPos(),
            sceneKeys,
            triggerMode,
            offset,
            estimateDuration(sceneKeys, intermissionTicks),
            intermissionTicks,
            blueTint,
            scale,
            resolvedTextScale));
    }

    private void triggerManualOnce() {
        if (resolveSceneKeysToSave().isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.scene.required"), 0xA03030);
            return;
        }
        if (isConfigDirty()) {
            status(Component.translatable("ponderer.ui.projector.save_before_play"), 0xA03030);
            return;
        }
        PondererServices.NETWORK.sendToServer(new ProjectorManualTriggerPayload(menu.projectorPos()));
        status(Component.translatable("ponderer.ui.projector.play_once.sent"), 0x2E6E2E);
    }

    private boolean requiresMissingOffset() {
        return menu.projectorKind().requiresAnchor() && !resolveSceneKeysToSave().isEmpty() && parseProjectionOffset() == null;
    }

    @Nullable
    private BlockPos parseProjectionOffset() {
        if (offsetX == null || offsetY == null || offsetZ == null) {
            return null;
        }
        String rawX = offsetX.getValue().trim();
        String rawY = offsetY.getValue().trim();
        String rawZ = offsetZ.getValue().trim();
        if (rawX.isBlank() && rawY.isBlank() && rawZ.isBlank()) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(rawX), Integer.parseInt(rawY), Integer.parseInt(rawZ));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void resetOffset() {
        setOffset(ProjectorBlockEntity.defaultProjectionOffset(projectorFacing()));
    }

    @Nullable
    private BlockPos resolveProjectionOffsetToSave() {
        if (!menu.projectorKind().requiresAnchor()) {
            return null;
        }
        return normalizeProjectionOffset(parseProjectionOffset());
    }

    @Nullable
    private BlockPos normalizeProjectionOffset(@Nullable BlockPos offset) {
        if (offset == null) {
            return null;
        }
        return offset.equals(ProjectorBlockEntity.defaultProjectionOffset(projectorFacing())) ? null : offset;
    }

    private Direction projectorFacing() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector != null) {
            return projector.getBlockState().getValue(ProjectorBlock.FACING);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            var state = minecraft.level.getBlockState(menu.projectorPos());
            if (state.hasProperty(ProjectorBlock.FACING)) {
                return state.getValue(ProjectorBlock.FACING);
            }
        }
        return Direction.NORTH;
    }

    private float parseScale() {
        if (scaleBox == null) {
            return miniatureScale;
        }
        return parseFloat(scaleBox, 1.0F, 0.1F, 5.0F);
    }

    private float parseTextScale() {
        if (textScaleBox == null) {
            return textScale;
        }
        return parseFloat(textScaleBox, 1.0F, 0.1F, 10.0F);
    }

    private int parseIntermissionTicks() {
        if (intermissionBox == null) {
            return intermissionTicks;
        }
        String raw = intermissionBox.getValue().trim();
        if (raw.isBlank()) {
            return ProjectorBlockEntity.DEFAULT_INTERMISSION_TICKS;
        }
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return ProjectorBlockEntity.DEFAULT_INTERMISSION_TICKS;
        }
    }

    private float parseFloat(EditBox box, float fallback, float min, float max) {
        String raw = box.getValue().trim();
        if (raw.isBlank() || "-".equals(raw) || ".".equals(raw) || "-.".equals(raw)) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Float.parseFloat(raw)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Component sceneSummary() {
        if (menu.sourceItem().isEmpty()) {
            return Component.translatable("ponderer.ui.projector.insert_item");
        }
        if (resolvedSceneKeys.isEmpty()) {
            return Component.translatable("ponderer.ui.projector.no_scenes_for_item");
        }
        return Component.translatable("ponderer.ui.projector.scenes_detected", resolvedSceneKeys.size());
    }

    private Component sceneSummaryLabel() {
        if (menu.sourceItem().isEmpty() || resolvedSceneKeys.isEmpty()) {
            return sceneSummary();
        }

        String title = scenePreview.title().isBlank() ? selectedSceneKey() : scenePreview.title();
        String prefix = "[" + (selectedSceneIndex + 1) + "/" + resolvedSceneKeys.size() + "] ";
        return Component.literal(prefix + title);
    }

    private int sceneSummaryColor() {
        if (menu.sourceItem().isEmpty()) {
            return 0x606060;
        }
        if (resolvedSceneKeys.isEmpty()) {
            return 0xA03030;
        }
        return 0x2E6E2E;
    }

    private int estimateDuration(List<String> sceneKeys, int intermissionTicks) {
        return ProjectorSceneCompiler.estimatePlaybackTicks(sceneKeys, intermissionTicks);
    }

    private boolean isConfigDirty() {
        if (!resolveSceneKeysToSave().equals(configuredSceneKeys)) {
            return true;
        }

        ProjectorBlockEntity projector = menu.projector();
        ProjectorTriggerMode baselineMode = projector == null
            ? ProjectorTriggerMode.fromFields(redstoneMode, loopMode)
            : projector.getTriggerMode();
        if (currentMode() != baselineMode) {
            return true;
        }

        if (!Objects.equals(resolveProjectionOffsetToSave(),
            projector == null ? null : normalizeProjectionOffset(projector.getProjectionOffset()))) {
            return true;
        }

        if (Float.compare(parseScale(), projector == null ? miniatureScale : projector.getMiniatureScale()) != 0) {
            return true;
        }
        if (Float.compare(parseTextScale(), projector == null ? textScale : projector.getTextScale()) != 0) {
            return true;
        }
        if (parseIntermissionTicks() != (projector == null ? intermissionTicks : projector.getIntermissionTicks())) {
            return true;
        }
        return showBlueTint != (projector == null ? showBlueTint : projector.showBlueTint());
    }

    private ProjectorTriggerMode currentMode() {
        return ProjectorTriggerMode.fromFields(redstoneMode, loopMode);
    }

    private void cycleSceneBackward() {
        if (resolvedSceneKeys.size() <= 1 || selectedSceneIndex <= 0) {
            return;
        }
        selectedSceneIndex--;
        sceneSelectionDirty = true;
        refreshScenePreview(true);
        refreshSceneButtons();
        playButton.active = !resolveSceneKeysToSave().isEmpty();
        applySelectedSceneImmediately();
    }

    private void cycleSceneForward() {
        if (resolvedSceneKeys.size() <= 1 || selectedSceneIndex >= resolvedSceneKeys.size() - 1) {
            return;
        }
        selectedSceneIndex++;
        sceneSelectionDirty = true;
        refreshScenePreview(true);
        refreshSceneButtons();
        playButton.active = !resolveSceneKeysToSave().isEmpty();
        applySelectedSceneImmediately();
    }

    private void applySelectedSceneImmediately() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector == null) {
            return;
        }

        List<String> sceneKeysToSave = resolveSceneKeysToSave();
        sendConfigUpdate(sceneKeysToSave, projector.getTriggerMode(), projector.getProjectionOffset(),
            projector.getIntermissionTicks(), projector.showBlueTint(), projector.getMiniatureScale(),
            projector.getTextScale());
        configuredSceneKeys = List.copyOf(sceneKeysToSave);
        sceneSelectionDirty = false;
    }

    private void selectSceneAfterRefresh(String previousSelectedSceneKey) {
        if (resolvedSceneKeys.isEmpty()) {
            selectedSceneIndex = 0;
            return;
        }

        if (sceneSelectionDirty) {
            int dirtyIndex = resolvedSceneKeys.indexOf(previousSelectedSceneKey);
            if (dirtyIndex >= 0) {
                selectedSceneIndex = dirtyIndex;
                return;
            }
        }

        for (String configuredSceneKey : configuredSceneKeys) {
            int configuredIndex = resolvedSceneKeys.indexOf(configuredSceneKey);
            if (configuredIndex >= 0) {
                selectedSceneIndex = configuredIndex;
                return;
            }
        }

        int previousIndex = resolvedSceneKeys.indexOf(previousSelectedSceneKey);
        selectedSceneIndex = previousIndex >= 0 ? previousIndex : 0;
    }

    private void refreshSceneButtons() {
        boolean hasScenes = !resolvedSceneKeys.isEmpty();
        boolean multipleScenes = resolvedSceneKeys.size() > 1;
        if (previousSceneButton != null) {
            previousSceneButton.visible = hasScenes;
            previousSceneButton.active = multipleScenes && selectedSceneIndex > 0;
        }
        if (nextSceneButton != null) {
            nextSceneButton.visible = hasScenes;
            nextSceneButton.active = multipleScenes && selectedSceneIndex < resolvedSceneKeys.size() - 1;
        }
    }

    private void refreshScenePreviewIfNeeded() {
        int intermission = parseIntermissionTicks();
        if (intermission != previewIntermissionTicks) {
            refreshScenePreview(false);
        }
    }

    private void refreshScenePreview(boolean resetProgress) {
        previewIntermissionTicks = parseIntermissionTicks();
        scenePreview = buildScenePreview(selectedSceneKey(), previewIntermissionTicks);
        if (scenePreview.totalTicks() <= 0) {
            previewTick = 0;
            return;
        }
        if (resetProgress) {
            previewTick = 0;
        } else {
            previewTick %= scenePreview.totalTicks();
        }
    }

    private String selectedSceneKey() {
        if (resolvedSceneKeys.isEmpty()) {
            return "";
        }
        int clampedIndex = Math.max(0, Math.min(selectedSceneIndex, resolvedSceneKeys.size() - 1));
        return resolvedSceneKeys.get(clampedIndex);
    }

    private List<String> resolveSceneKeysToSave() {
        if (resolvedSceneKeys.isEmpty()) {
            return List.of();
        }
        if (sceneSelectionDirty) {
            String selectedSceneKey = selectedSceneKey();
            return selectedSceneKey.isBlank() ? List.of() : List.of(selectedSceneKey);
        }
        if (!configuredSceneKeys.isEmpty()) {
            List<String> filtered = new ArrayList<>();
            for (String configuredSceneKey : configuredSceneKeys) {
                if (resolvedSceneKeys.contains(configuredSceneKey) && !filtered.contains(configuredSceneKey)) {
                    filtered.add(configuredSceneKey);
                }
            }
            if (!filtered.isEmpty()) {
                return List.copyOf(filtered);
            }
        }
        return List.copyOf(resolvedSceneKeys);
    }

    private boolean clickSceneTimeline(double mouseX, double mouseY) {
        if (!isWithinSceneTimeline(mouseX, mouseY) || scenePreview.totalTicks() <= 0) {
            return false;
        }

        Integer clickedTick = hoveredSceneKeyframeTick(mouseX, mouseY);
        if (clickedTick == null) {
            return false;
        }
        previewTick = clickedTick;
        if (!isConfigDirty()) {
            seekSelectedSceneTimeline(clickedTick);
        }
        return true;
    }

    private boolean isWithinSceneTimeline(double mouseX, double mouseY) {
        if (resolvedSceneKeys.isEmpty() || scenePreview.totalTicks() <= 0) {
            return false;
        }
        int x = leftPos + SCENE_BAR_X;
        int y = topPos + SCENE_BAR_Y;
        return mouseX >= x && mouseX < x + SCENE_BAR_WIDTH + 4
            && mouseY >= y - 3 && mouseY < y + SCENE_BAR_HEIGHT + 20;
    }

    @Nullable
    private Integer hoveredSceneKeyframeTick(double mouseX, double mouseY) {
        if (!isWithinSceneTimeline(mouseX, mouseY)) {
            return null;
        }

        int keyframeMouseX = (int) Math.round(mouseX - (leftPos + SCENE_BAR_X - 2));
        Integer hoveredKeyframe = null;
        int hoveredDistance = Integer.MAX_VALUE;

        int startDistance = Math.abs(keyframeMouseX - timelineKeyframePosition(0));
        if (startDistance <= SCENE_KEYFRAME_HIT_RADIUS) {
            hoveredKeyframe = 0;
            hoveredDistance = startDistance;
        }

        for (int keyframeTick : scenePreview.keyframes()) {
            int keyframePos = timelineKeyframePosition(keyframeTick);
            int distance = Math.abs(keyframeMouseX - keyframePos);
            if (distance <= SCENE_KEYFRAME_HIT_RADIUS && distance < hoveredDistance) {
                hoveredKeyframe = keyframeTick;
                hoveredDistance = distance;
            }
        }
        return hoveredKeyframe;
    }

    private void seekSelectedSceneTimeline(int sceneTick) {
        ProjectorBlockEntity projector = menu.projector();
        if (projector == null) {
            return;
        }

        SceneTimelineWindow window = findSceneTimelineWindow(
            currentProjectorSceneKeys(),
            selectedSceneKey(),
            projector.getIntermissionTicks(),
            projector.isPlaybackLooping());
        if (window == null) {
            return;
        }

        int safeTick = Math.max(0, Math.min(sceneTick, scenePreview.totalTicks()));
        int playbackTick = safeTick >= scenePreview.totalTicks()
            ? window.activeEndTick()
            : window.startTick() + safeTick;
        PondererServices.NETWORK.sendToServer(new ProjectorSeekPayload(menu.projectorPos(), Math.max(0, playbackTick)));
    }

    private List<String> currentProjectorSceneKeys() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector != null && !projector.getSceneKeys().isEmpty()) {
            return List.copyOf(projector.getSceneKeys());
        }
        if (!configuredSceneKeys.isEmpty()) {
            return configuredSceneKeys;
        }
        return List.copyOf(resolvedSceneKeys);
    }

    @Nullable
    private SceneTimelineWindow findSceneTimelineWindow(List<String> sceneKeys, String targetSceneKey,
                                                        int intermissionTicks, boolean includeFinalIntermission) {
        if (sceneKeys.isEmpty() || targetSceneKey == null || targetSceneKey.isBlank()) {
            return null;
        }

        int safeIntermission = Math.max(0, intermissionTicks);
        int timeline = 0;
        for (int index = 0; index < sceneKeys.size(); index++) {
            String sceneKey = sceneKeys.get(index);
            int duration = sceneKey.equals(scenePreview.sceneKey()) && scenePreview.totalTicks() > 0
                ? scenePreview.totalTicks()
                : estimateDuration(List.of(sceneKey), intermissionTicks);
            duration = Math.max(0, duration);
            boolean hasFollowingScene = index < sceneKeys.size() - 1;
            int holdEnd = timeline + duration + ((hasFollowingScene || includeFinalIntermission) ? safeIntermission : 0);
            if (sceneKey.equals(targetSceneKey)) {
                return new SceneTimelineWindow(timeline, duration, holdEnd, !hasFollowingScene);
            }
            timeline = holdEnd;
        }

        return null;
    }

    @Nullable
    private Integer resolveLiveSceneTimelineTick() {
        if (isConfigDirty() || scenePreview.totalTicks() <= 0) {
            return null;
        }

        ProjectorBlockEntity projector = menu.projector();
        if (projector == null || !projector.isPlaying()) {
            return null;
        }

        SceneTimelineWindow window = findSceneTimelineWindow(
            currentProjectorSceneKeys(),
            selectedSceneKey(),
            projector.getIntermissionTicks(),
            projector.isPlaybackLooping());
        if (window == null) {
            return null;
        }

        Level level = projector.getLevel();
        if (level == null) {
            level = Minecraft.getInstance().level;
        }
        if (level == null) {
            return null;
        }

        int totalDuration = projector.getPlaybackDurationTicks();
        if (totalDuration <= 0) {
            totalDuration = estimateDuration(currentProjectorSceneKeys(), projector.getIntermissionTicks());
        }
        if (projector.isPlaybackLooping() && totalDuration > 0) {
            totalDuration += Math.max(0, projector.getIntermissionTicks());
        }

        int playbackTick = ProjectorSceneTimeline.resolvePlaybackTick(
            Math.max(0L, level.getGameTime() - projector.getPlaybackStartGameTime()),
            totalDuration,
            projector.isPlaybackLooping(),
            projector.shouldPersistAfterPlaybackEnd(),
            ProjectorBlockEntity.FINAL_EXTRA_TICKS);
        if (playbackTick == ProjectorSceneTimeline.NO_PLAYBACK_TICK) {
            return null;
        }

        if (playbackTick < window.startTick()) {
            return 0;
        }
        if (playbackTick < window.activeEndTick()) {
            return Math.max(0, playbackTick - window.startTick());
        }
        if (playbackTick < window.holdEndTick() || (window.isLastScene() && playbackTick >= window.activeEndTick())) {
            return scenePreview.totalTicks();
        }
        return scenePreview.totalTicks();
    }

    private ScenePreview buildScenePreview(String sceneKey, int intermissionTicks) {
        if (sceneKey == null || sceneKey.isBlank()) {
            return ScenePreview.EMPTY;
        }

        DslScene dslScene = SceneRuntime.findByKey(sceneKey);
        String title = resolveSceneTitle(sceneKey, dslScene);
        try {
            ProjectorSceneBundle bundle = ProjectorSceneBundle.compile(sceneKey);
            if (bundle != null && !bundle.segments().isEmpty()) {
                if ((title == null || title.isBlank())
                    && bundle.segments().get(0).scene() != null) {
                    title = bundle.segments().get(0).scene().getTitle();
                }
                List<Integer> keyframes = new ArrayList<>();
                int timeline = 0;
                for (int segmentIndex = 0; segmentIndex < bundle.segments().size(); segmentIndex++) {
                    ProjectorSceneBundle.Segment segment = bundle.segments().get(segmentIndex);
                    for (int keyframeIndex = 0; keyframeIndex < segment.scene().getKeyframeCount(); keyframeIndex++) {
                        keyframes.add(timeline + segment.scene().getKeyframeTime(keyframeIndex));
                    }
                    timeline += Math.max(1, segment.durationTicks());
                    if (segmentIndex < bundle.segments().size() - 1) {
                        timeline += Math.max(0, intermissionTicks);
                    }
                }
                return new ScenePreview(sceneKey, title == null ? sceneKey : title, timeline, List.copyOf(keyframes));
            }
        } catch (Throwable ignored) {
        }

        if (dslScene != null && dslScene.scenes != null && !dslScene.scenes.isEmpty()) {
            int timeline = ProjectorSceneTimeline.withIntermissions(
                ProjectorSceneTimeline.estimateTotalTicks(sceneKey),
                dslScene.scenes.size(),
                intermissionTicks,
                false);
            return new ScenePreview(sceneKey, title, timeline,
                ProjectorSceneTimeline.estimateKeyframeTicks(dslScene, intermissionTicks));
        }

        int totalTicks = Math.max(0, ProjectorSceneCompiler.estimateTotalTicks(sceneKey));
        return new ScenePreview(sceneKey, title, totalTicks, List.of());
    }

    private String resolveSceneTitle(String sceneKey, @Nullable DslScene dslScene) {
        if (dslScene != null) {
            if (dslScene.title != null) {
                String sceneTitle = dslScene.title.resolve();
                if (!sceneTitle.isBlank()) {
                    return sceneTitle;
                }
            }
            if (dslScene.id != null && !dslScene.id.isBlank()) {
                return dslScene.id;
            }
        }
        return sceneKey;
    }

    private void renderSceneTimeline(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (resolvedSceneKeys.isEmpty()) {
            return;
        }

        int x = leftPos + SCENE_BAR_X;
        int y = topPos + SCENE_BAR_Y;
        new BoxElement()
            .withBackground(PonderUI.BACKGROUND_FLAT)
            .gradientBorder(PonderUI.COLOR_IDLE)
            .at(x, y, 400)
            .withBounds(SCENE_BAR_WIDTH, SCENE_BAR_HEIGHT)
            .render(graphics);

        if (scenePreview.totalTicks() <= 0) {
            return;
        }

        PoseStack poseStack = graphics.pose();
        Integer liveTick = resolveLiveSceneTimelineTick();
        float animatedTick = liveTick == null ? previewTick : liveTick;
        animatedTick = Math.max(0.0F, Math.min(scenePreview.totalTicks(), animatedTick));
        float progress = Math.max(0.0F, Math.min(1.0F, animatedTick / (float) scenePreview.totalTicks()));
        Integer hoveredKeyframe = hoveredSceneKeyframeTick(mouseX, mouseY);

        poseStack.pushPose();
        poseStack.translate(x - 2, y - 2, 100);
        poseStack.pushPose();
        poseStack.scale((SCENE_BAR_WIDTH + 4) * progress, 1.0F, 1.0F);
        Color start = PonderProgressBar.BAR_COLORS.getFirst();
        Color end = PonderProgressBar.BAR_COLORS.getSecond();
        UIRenderHelper.drawGradientRect(poseStack.last().pose(), 310, 0f, 1f, 1f, 3f, start, start);
        UIRenderHelper.drawGradientRect(poseStack.last().pose(), 310, 0f, 3f, 1f, 4f, end, end);
        poseStack.popPose();

        var futureColors = PonderUI.COLOR_IDLE.map(color -> color.setAlpha(0x70));
        var passedColors = PonderUI.COLOR_HOVER.map(color -> color.setAlpha(0xe0));
        if (hoveredKeyframe != null && hoveredKeyframe == 0 && !scenePreview.keyframes().contains(0)) {
            int keyframePos = timelineKeyframePosition(0);
            UIRenderHelper.drawGradientRect(poseStack.last().pose(), 320, keyframePos, 0f,
                keyframePos + 2f, 9f, passedColors.getFirst(), passedColors.getSecond());
        }
        for (int keyframeTick : scenePreview.keyframes()) {
            int keyframePos = timelineKeyframePosition(keyframeTick);
            boolean hovered = hoveredKeyframe != null && hoveredKeyframe == keyframeTick;
            boolean passed = animatedTick >= keyframeTick;
            var colors = hovered ? passedColors : (passed ? passedColors : futureColors);
            int height = hovered ? 8 : 4;
            UIRenderHelper.drawGradientRect(poseStack.last().pose(), 320, keyframePos, 0f,
                keyframePos + 2f, 1f + height, colors.getFirst(), colors.getSecond());
        }
        poseStack.popPose();
    }

    private int timelineKeyframePosition(int keyframeTick) {
        return (int) (((float) keyframeTick) / Math.max(1.0F, (float) scenePreview.totalTicks())
            * (SCENE_BAR_WIDTH + 2));
    }

    private String fingerprint(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return stack.save(new CompoundTag()).toString();
    }

    private void status(Component message, int color) {
        statusMessage = message;
        statusColor = color;
    }

    private Component trimToWidth(Component text, int maxWidth) {
        String raw = text.getString();
        if (font.width(raw) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String trimmed = raw;
        while (!trimmed.isEmpty() && font.width(trimmed + ellipsis) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return Component.literal(trimmed + ellipsis).withStyle(text.getStyle());
    }

    private String trimFloat(float value) {
        if (value == (int) value) {
            return Integer.toString((int) value);
        }
        return Float.toString(value);
    }

    private void drawRowLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x, y, TEXT, false);
    }

    private record ScenePreview(String sceneKey, String title, int totalTicks, List<Integer> keyframes) {
        private static final ScenePreview EMPTY = new ScenePreview("", "", 0, List.of());
    }

    private record SceneTimelineWindow(int startTick, int sceneDurationTicks, int holdEndTick, boolean isLastScene) {
        private int activeEndTick() {
            return startTick + sceneDurationTicks;
        }
    }
}
