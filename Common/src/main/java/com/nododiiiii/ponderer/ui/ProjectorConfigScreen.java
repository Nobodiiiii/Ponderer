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
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProjectorConfigScreen extends AbstractContainerScreen<ProjectorMenu> {

    private static final ResourceLocation BACKGROUND =
        new ResourceLocation("ponderer", "textures/gui/projector.png");
    private static final ResourceLocation PLAYER_INVENTORY =
        new ResourceLocation("ponderer", "textures/gui/player_inventory.png");
    private static final int TEXT = 0x000000;
    private static final int INPUT_TEXT = 0xFFFFFF;
    private static final int LABEL_TEXT = 0xF2F4FF;
    private static final int PROJECTOR_TEXTURE_WIDTH = 320;
    private static final int PROJECTOR_TEXTURE_HEIGHT = 480;
    private static final int PLAYER_INVENTORY_WIDTH = 176;
    private static final int PLAYER_INVENTORY_HEIGHT = 108;
    private static final int PLAYER_INVENTORY_TEXTURE_SIZE = 256;
    private static final int PLAYER_INVENTORY_X = ProjectorMenu.INVENTORY_PANEL_X;
    private static final int PLAYER_INVENTORY_Y = 194;
    private static final int SCREEN_WIDTH = ProjectorMenu.SCREEN_WIDTH;
    private static final int SCREEN_HEIGHT = PLAYER_INVENTORY_Y + PLAYER_INVENTORY_HEIGHT;
    private static final int MAIN_PANEL_HEIGHT = 194;
    private static final int TITLE_Y = 4;
    private static final int SOURCE_LABEL_TEXT_X = 31;
    private static final int SOURCE_LABEL_TEXT_Y = 30;
    private static final int SOURCE_TEXT_Y = 31;
    private static final int SOURCE_INFO_X = ProjectorMenu.SOURCE_SLOT_X + 20;
    private static final int SOURCE_INFO_WIDTH = 149;
    private static final int INVENTORY_LABEL_X = PLAYER_INVENTORY_X + 8;
    private static final int INVENTORY_LABEL_Y = PLAYER_INVENTORY_Y + 6;
    private static final int LEFT_LABEL_TEXT_X = 29;
    private static final int RIGHT_LABEL_TEXT_X = 160;
    private static final int LABEL_TEXT_MAX_WIDTH = 48;
    private static final int LEFT_VALUE_X = 80;
    private static final int RIGHT_VALUE_X = 211;
    private static final int LEFT_POINTED_VALUE_X = 76;
    private static final int RIGHT_POINTED_VALUE_X = 207;
    private static final int VALUE_WIDTH = 59;
    private static final int POINTED_VALUE_WIDTH = 64;
    private static final int CONTROL_HEIGHT = 18;
    private static final int ROW_1_Y = 55;
    private static final int ROW_2_Y = 74;
    private static final int ROW_3_Y = 93;
    private static final int ROW_4_Y = 112;
    private static final int LABEL_TEXT_OFFSET_Y = 5;
    private static final int SCENE_BUTTON_SIZE = 18;
    private static final int SCENE_BUTTON_Y = 139;
    private static final int SCENE_LEFT_BUTTON_X = 12;
    private static final int SCENE_RIGHT_BUTTON_X = 252;
    private static final int SCENE_BAR_X = 36;
    private static final int SCENE_BAR_Y = 148;
    private static final int SCENE_BAR_WIDTH = 210;
    private static final int SCENE_BAR_HEIGHT = 1;
    private static final int SCENE_KEYFRAME_HIT_RADIUS = 6;
    private static final int OFFSET_BOX_WIDTH = 18;
    private static final int OFFSET_GAP = 2;
    private static final int OFFSET_START_X = LEFT_VALUE_X;
    private static final int OFFSET_ROW_TEXTURE_X = 79;
    private static final int OFFSET_ROW_TEXTURE_Y = 316;
    private static final int OFFSET_ROW_TEXTURE_WIDTH = 60;
    private static final int BOTTOM_ACTION_Y = 171;
    private static final int RESET_BUTTON_X = 173;
    private static final int RESET_BUTTON_SIZE = 18;
    private static final int PLAY_BUTTON_X = 201;
    private static final int PLAY_BUTTON_WIDTH = 72;
    private static final int VALUE_STATE_IDLE_U = 54;
    private static final int VALUE_STATE_HOVER_U = 118;
    private static final int VALUE_STATE_CLICK_U = 182;
    private static final int VALUE_STATE_V = 461;
    private static final int VALUE_STATE_W = 64;
    private static final int VALUE_TEXT_OFFSET_X = 4;
    private static final int BUTTON_STATE_IDLE_U = 57;
    private static final int BUTTON_STATE_HOVER_U = 129;
    private static final int BUTTON_STATE_CLICK_U = 201;
    private static final int BUTTON_STATE_V = 441;
    private static final int BUTTON_STATE_W = 72;
    private static final int PONDER_BUTTON_IDLE_U = 248;
    private static final int PONDER_BUTTON_HOVER_U = 266;
    private static final int PONDER_BUTTON_CLICK_U = 284;
    private static final int PONDER_BUTTON_DISABLED_U = 302;
    private static final int PONDER_BUTTON_STATE_V = 462;
    private static final int PONDER_BUTTON_STATE_SIZE = 18;

    private Button redstoneModeButton;
    private Button loopModeButton;
    private Button blueTintButton;
    private Button textAntiOcclusionButton;
    private Button playButton;
    @Nullable
    private Button previousSceneButton;
    @Nullable
    private Button nextSceneButton;
    @Nullable
    private Button resetProjectionButton;
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
    private boolean overlayAntiOcclusion = true;
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
        titleLabelX = 18;
        titleLabelY = TITLE_Y;
        loadProjectorState();
        configuredSceneKeys = menu.projector() == null ? List.of() : List.copyOf(menu.projector().getSceneKeys());

        redstoneModeButton = addRenderableWidget(modeButton(
            leftPos + LEFT_POINTED_VALUE_X,
            topPos + ROW_1_Y,
            () -> redstoneMode,
            value -> Component.translatable("ponderer.ui.projector.redstone_mode." + (value ? "redstone" : "manual")),
            value -> redstoneMode = value));
        loopModeButton = addRenderableWidget(modeButton(
            leftPos + RIGHT_POINTED_VALUE_X,
            topPos + ROW_1_Y,
            () -> loopMode,
            value -> Component.translatable("ponderer.ui.projector.loop_mode." + (value ? "loop" : "once")),
            value -> loopMode = value));
        previousSceneButton = addRenderableWidget(new ProjectorTextureButton(
            leftPos + SCENE_LEFT_BUTTON_X,
            topPos + SCENE_BUTTON_Y,
            SCENE_BUTTON_SIZE,
            SCENE_BUTTON_SIZE,
            Component.empty(),
            button -> cycleSceneBackward(),
            ButtonVisual.ACTION_ICON_LEFT));
        nextSceneButton = addRenderableWidget(new ProjectorTextureButton(
            leftPos + SCENE_RIGHT_BUTTON_X,
            topPos + SCENE_BUTTON_Y,
            SCENE_BUTTON_SIZE,
            SCENE_BUTTON_SIZE,
            Component.empty(),
            button -> cycleSceneForward(),
            ButtonVisual.ACTION_ICON_RIGHT));

        if (menu.projectorKind().requiresAnchor()) {
            int offsetXStart = leftPos + OFFSET_START_X;
            offsetX = addRenderableWidget(offsetBox(offsetXStart, topPos + ROW_4_Y));
            offsetY = addRenderableWidget(offsetBox(offsetXStart + OFFSET_BOX_WIDTH + OFFSET_GAP, topPos + ROW_4_Y));
            offsetZ = addRenderableWidget(offsetBox(offsetXStart + (OFFSET_BOX_WIDTH + OFFSET_GAP) * 2, topPos + ROW_4_Y));

            blueTintButton = addRenderableWidget(modeButton(
                leftPos + LEFT_POINTED_VALUE_X, topPos + ROW_2_Y,
                () -> showBlueTint,
                value -> Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off")),
                value -> showBlueTint = value));
            textAntiOcclusionButton = addRenderableWidget(modeButton(
                leftPos + RIGHT_POINTED_VALUE_X, topPos + ROW_2_Y,
                () -> overlayAntiOcclusion,
                value -> Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off")),
                value -> overlayAntiOcclusion = value));
            intermissionBox = addRenderableWidget(integerBox(
                leftPos + LEFT_VALUE_X, topPos + ROW_3_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.intermission"), intermissionTicks));
            textScaleBox = addRenderableWidget(decimalBox(
                leftPos + RIGHT_VALUE_X, topPos + ROW_3_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.text_scale"), textScale));
            resetProjectionButton = addRenderableWidget(new ProjectorTextureButton(
                leftPos + RESET_BUTTON_X, topPos + BOTTOM_ACTION_Y, RESET_BUTTON_SIZE, CONTROL_HEIGHT,
                Component.empty(), button -> resetProjectionControl(), ButtonVisual.ACTION_ICON_RESET));
            playButton = addRenderableWidget(new ProjectorTextureButton(
                leftPos + PLAY_BUTTON_X, topPos + BOTTOM_ACTION_Y, PLAY_BUTTON_WIDTH, CONTROL_HEIGHT,
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce(), ButtonVisual.ACTION_PLAY));
        } else {
            scaleBox = addRenderableWidget(decimalBox(
                leftPos + LEFT_VALUE_X, topPos + ROW_4_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.scale"), miniatureScale));

            blueTintButton = addRenderableWidget(modeButton(
                leftPos + LEFT_POINTED_VALUE_X, topPos + ROW_2_Y,
                () -> showBlueTint,
                value -> Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off")),
                value -> showBlueTint = value));
            textAntiOcclusionButton = addRenderableWidget(modeButton(
                leftPos + RIGHT_POINTED_VALUE_X, topPos + ROW_2_Y,
                () -> overlayAntiOcclusion,
                value -> Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off")),
                value -> overlayAntiOcclusion = value));
            intermissionBox = addRenderableWidget(integerBox(
                leftPos + LEFT_VALUE_X, topPos + ROW_3_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.intermission"), intermissionTicks));
            textScaleBox = addRenderableWidget(decimalBox(
                leftPos + RIGHT_VALUE_X, topPos + ROW_3_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.text_scale"), textScale));
            resetProjectionButton = addRenderableWidget(new ProjectorTextureButton(
                leftPos + RESET_BUTTON_X, topPos + BOTTOM_ACTION_Y, RESET_BUTTON_SIZE, CONTROL_HEIGHT,
                Component.empty(), button -> resetProjectionControl(), ButtonVisual.ACTION_ICON_RESET));
            playButton = addRenderableWidget(new ProjectorTextureButton(
                leftPos + PLAY_BUTTON_X, topPos + BOTTOM_ACTION_Y, PLAY_BUTTON_WIDTH, CONTROL_HEIGHT,
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce(), ButtonVisual.ACTION_PLAY));
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
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(BACKGROUND, x, y, 0, 0, SCREEN_WIDTH, MAIN_PANEL_HEIGHT,
            PROJECTOR_TEXTURE_WIDTH, PROJECTOR_TEXTURE_HEIGHT);
        if (menu.projectorKind().requiresAnchor()) {
            graphics.blit(BACKGROUND, x + OFFSET_START_X, y + ROW_4_Y,
                OFFSET_ROW_TEXTURE_X, OFFSET_ROW_TEXTURE_Y, OFFSET_ROW_TEXTURE_WIDTH, CONTROL_HEIGHT,
                PROJECTOR_TEXTURE_WIDTH, PROJECTOR_TEXTURE_HEIGHT);
        }
        graphics.blit(PLAYER_INVENTORY, x + PLAYER_INVENTORY_X, y + PLAYER_INVENTORY_Y,
            0, 0, PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT,
            PLAYER_INVENTORY_TEXTURE_SIZE, PLAYER_INVENTORY_TEXTURE_SIZE);
        renderSceneTimeline(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component projectorKind = Component.translatable(menu.projectorKind().translationKey());
        graphics.drawString(font, projectorKind, (imageWidth - font.width(projectorKind)) / 2,
            TITLE_Y, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        drawLabel(graphics, "ponderer.ui.projector.source_item", SOURCE_LABEL_TEXT_X, SOURCE_LABEL_TEXT_Y);
        graphics.drawString(font, trimToWidth(sceneSummaryLabel(), SOURCE_INFO_WIDTH), SOURCE_INFO_X,
            SOURCE_TEXT_Y, sceneSummaryColor(), false);

        drawLabel(graphics, "ponderer.ui.projector.redstone_mode", LEFT_LABEL_TEXT_X, ROW_1_Y + LABEL_TEXT_OFFSET_Y);
        drawLabel(graphics, "ponderer.ui.projector.loop_mode", RIGHT_LABEL_TEXT_X, ROW_1_Y + LABEL_TEXT_OFFSET_Y);
        drawLabel(graphics, "ponderer.ui.projector.blue_tint", LEFT_LABEL_TEXT_X, ROW_2_Y + LABEL_TEXT_OFFSET_Y);
        drawLabel(graphics, "ponderer.ui.projector.text_anti_occlusion", RIGHT_LABEL_TEXT_X, ROW_2_Y + LABEL_TEXT_OFFSET_Y);
        drawLabel(graphics, "ponderer.ui.projector.intermission", LEFT_LABEL_TEXT_X, ROW_3_Y + LABEL_TEXT_OFFSET_Y);
        drawLabel(graphics, "ponderer.ui.projector.text_scale", RIGHT_LABEL_TEXT_X, ROW_3_Y + LABEL_TEXT_OFFSET_Y);

        if (menu.projectorKind().requiresAnchor()) {
            drawLabel(graphics, "ponderer.ui.projector.offset", LEFT_LABEL_TEXT_X, ROW_4_Y + LABEL_TEXT_OFFSET_Y);
        } else {
            drawLabel(graphics, "ponderer.ui.projector.scale", LEFT_LABEL_TEXT_X, ROW_4_Y + LABEL_TEXT_OFFSET_Y);
        }

        if (!statusMessage.getString().isBlank()) {
            graphics.drawString(font, trimToWidth(statusMessage, 210),
                SCENE_BAR_X, 157, statusColor, false);
        }
    }

    private void loadProjectorState() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector == null) {
            applyMode(ProjectorTriggerMode.MANUAL_LOOP);
            return;
        }

        applyMode(projector.getTriggerMode());
        showBlueTint = projector.showBlueTint();
        overlayAntiOcclusion = projector.overlayAntiOcclusion();
        miniatureScale = projector.getMiniatureScale();
        textScale = projector.getTextScale();
        intermissionTicks = projector.getIntermissionTicks();
    }

    private void applyMode(ProjectorTriggerMode mode) {
        redstoneMode = mode.usesRedstone();
        loopMode = mode.loops();
    }

    private Button modeButton(int x, int y, BooleanSupplier getter,
                              Function<Boolean, Component> valueLabel,
                              Consumer<Boolean> setter) {
        return new ProjectorTextureButton(x, y, POINTED_VALUE_WIDTH, CONTROL_HEIGHT, valueLabel.apply(getter.getAsBoolean()), button -> {
            boolean next = !getter.getAsBoolean();
            setter.accept(next);
            button.setMessage(valueLabel.apply(next));
        }, ButtonVisual.POINTED_VALUE);
    }

    private EditBox offsetBox(int x, int y) {
        EditBox box = new ProjectorEditBox(font, x, y, OFFSET_BOX_WIDTH, CONTROL_HEIGHT,
            Component.translatable("ponderer.ui.projector.offset"));
        box.setMaxLength(6);
        box.setTextColor(INPUT_TEXT);
        box.setFilter(value -> value.isEmpty() || "-".equals(value) || value.matches("-?\\d+"));
        box.setHint(Component.literal("0"));
        return box;
    }

    private EditBox decimalBox(int x, int y, int width, Component narration, float initialValue) {
        EditBox box = new ProjectorEditBox(font, x, y, width, CONTROL_HEIGHT, narration);
        box.setMaxLength(6);
        box.setTextColor(INPUT_TEXT);
        box.setFilter(value -> value.isEmpty() || "-".equals(value) || ".".equals(value) || "-.".equals(value)
            || value.matches("-?\\d*(\\.\\d*)?"));
        box.setValue(trimFloat(initialValue));
        return box;
    }

    private EditBox integerBox(int x, int y, int width, Component narration, int initialValue) {
        EditBox box = new ProjectorEditBox(font, x, y, width, CONTROL_HEIGHT, narration);
        box.setMaxLength(6);
        box.setTextColor(INPUT_TEXT);
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
            projector.getIntermissionTicks(), projector.showBlueTint(), projector.overlayAntiOcclusion(),
            projector.getMiniatureScale(),
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
            resolvedIntermissionTicks, showBlueTint, overlayAntiOcclusion, scale, resolvedTextScale);
        configuredSceneKeys = List.copyOf(sceneKeysToSave);
        sceneSelectionDirty = false;
        if (showStatus) {
            status(Component.translatable("ponderer.ui.projector.saved"), 0x2E6E2E);
        }
        return true;
    }

    private void sendConfigUpdate(List<String> sceneKeys, ProjectorTriggerMode triggerMode, @Nullable BlockPos offset,
                                  int intermissionTicks, boolean blueTint, boolean resolvedOverlayAntiOcclusion,
                                  float scale, float resolvedTextScale) {
        PondererServices.NETWORK.sendToServer(new ProjectorConfigUpdatePayload(
            menu.projectorPos(),
            sceneKeys,
            triggerMode,
            offset,
            estimateDuration(sceneKeys, intermissionTicks),
            intermissionTicks,
            blueTint,
            resolvedOverlayAntiOcclusion,
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

    private void resetProjectionControl() {
        if (menu.projectorKind().requiresAnchor()) {
            resetOffset();
        } else if (scaleBox != null) {
            scaleBox.setValue(trimFloat(1.0F));
        }
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
            return TEXT;
        }
        if (resolvedSceneKeys.isEmpty()) {
            return TEXT;
        }
        return TEXT;
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
        if (overlayAntiOcclusion != (projector == null ? overlayAntiOcclusion : projector.overlayAntiOcclusion())) {
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
            projector.getIntermissionTicks(), projector.showBlueTint(), projector.overlayAntiOcclusion(),
            projector.getMiniatureScale(),
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

    private void drawLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, trimToWidth(Component.translatable(key), LABEL_TEXT_MAX_WIDTH), x, y, LABEL_TEXT, false);
    }

    private static void renderControlOverlay(GuiGraphics graphics, int x, int y, int width, int height,
                                             int sourceU, int sourceV, int sourceWidth) {
        if (width == sourceWidth) {
            graphics.blit(BACKGROUND, x, y, sourceU, sourceV, width, height,
                PROJECTOR_TEXTURE_WIDTH, PROJECTOR_TEXTURE_HEIGHT);
            return;
        }

        graphics.blit(BACKGROUND, x, y, sourceU, sourceV, 1, height,
            PROJECTOR_TEXTURE_WIDTH, PROJECTOR_TEXTURE_HEIGHT);
        for (int offset = 1; offset < width - 1; offset++) {
            graphics.blit(BACKGROUND, x + offset, y, sourceU + 1, sourceV, 1, height,
                PROJECTOR_TEXTURE_WIDTH, PROJECTOR_TEXTURE_HEIGHT);
        }
        graphics.blit(BACKGROUND, x + width - 1, y, sourceU + sourceWidth - 1, sourceV, 1, height,
            PROJECTOR_TEXTURE_WIDTH, PROJECTOR_TEXTURE_HEIGHT);
    }

    private enum ButtonVisual {
        POINTED_VALUE,
        ACTION_ICON_RESET,
        ACTION_ICON_LEFT,
        ACTION_ICON_RIGHT,
        ACTION_PLAY
    }

    private static class ProjectorTextureButton extends Button {
        private final ButtonVisual visual;
        private boolean pressed;

        private ProjectorTextureButton(int x, int y, int width, int height, Component message, OnPress onPress,
                                       ButtonVisual visual) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.visual = visual;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            pressed = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            pressed = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHovered();
            if (!hovered) {
                pressed = false;
            }

            int u = -1;
            int v = BUTTON_STATE_V;
            int sourceWidth = BUTTON_STATE_W;
            if (visual == ButtonVisual.POINTED_VALUE) {
                v = VALUE_STATE_V;
                sourceWidth = VALUE_STATE_W;
                u = active
                    ? (pressed ? VALUE_STATE_CLICK_U : (hovered ? VALUE_STATE_HOVER_U : VALUE_STATE_IDLE_U))
                    : VALUE_STATE_IDLE_U;
            } else if (isPonderButtonVisual()) {
                v = PONDER_BUTTON_STATE_V;
                sourceWidth = PONDER_BUTTON_STATE_SIZE;
                u = active
                    ? (pressed ? PONDER_BUTTON_CLICK_U : (hovered ? PONDER_BUTTON_HOVER_U : PONDER_BUTTON_IDLE_U))
                    : PONDER_BUTTON_DISABLED_U;
            } else if (active && pressed) {
                u = BUTTON_STATE_CLICK_U;
            } else if (active && hovered) {
                u = BUTTON_STATE_HOVER_U;
            } else {
                u = BUTTON_STATE_IDLE_U;
            }

            boolean renderedOverlay = u >= 0;
            if (renderedOverlay) {
                renderControlOverlay(graphics, getX(), getY(), width, height, u, v, sourceWidth);
            }

            Font font = Minecraft.getInstance().font;
            int color = active && visual == ButtonVisual.ACTION_PLAY ? 0xF2F4FF : (active ? TEXT : 0x707070);
            if (visual == ButtonVisual.ACTION_ICON_RESET) {
                if (renderedOverlay) {
                    PonderGuiTextures.ICON_CONFIG_RESET.render(graphics, getX() + 1, getY() + 1);
                }
                return;
            }
            if (visual == ButtonVisual.ACTION_ICON_LEFT) {
                PonderGuiTextures.ICON_PONDER_LEFT.render(graphics, getX() + 1, getY() + 1);
                return;
            }
            if (visual == ButtonVisual.ACTION_ICON_RIGHT) {
                PonderGuiTextures.ICON_PONDER_RIGHT.render(graphics, getX() + 1, getY() + 1);
                return;
            }
            if (visual == ButtonVisual.ACTION_PLAY) {
                if (renderedOverlay) {
                    PonderGuiTextures.ICON_PONDER_RIGHT.render(graphics, getX() + 2, getY() + 1);
                }
                String text = font.plainSubstrByWidth(getMessage().getString(), width - 17);
                graphics.drawString(font, text, getX() + 15, getY() + 5, color, false);
                return;
            }

            int textWidth = width - VALUE_TEXT_OFFSET_X - 6;
            String text = font.plainSubstrByWidth(getMessage().getString(), textWidth);
            graphics.drawString(font, text, getX() + VALUE_TEXT_OFFSET_X + 3 + (textWidth - font.width(text)) / 2,
                getY() + 5, color, false);
        }

        private boolean isPonderButtonVisual() {
            return visual == ButtonVisual.ACTION_ICON_LEFT || visual == ButtonVisual.ACTION_ICON_RIGHT;
        }
    }

    private static class ProjectorEditBox extends EditBox {
        private final int backgroundX;
        private final int backgroundY;
        private final int backgroundWidth;
        private final int backgroundHeight;

        private ProjectorEditBox(Font font, int x, int y, int width, int height, Component narration) {
            super(font, x + 4, y + 5, Math.max(1, width - 8), 8, narration);
            this.backgroundX = x;
            this.backgroundY = y;
            this.backgroundWidth = width;
            this.backgroundHeight = height;
            setBordered(false);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return visible
                && mouseX >= backgroundX
                && mouseY >= backgroundY
                && mouseX < backgroundX + backgroundWidth
                && mouseY < backgroundY + backgroundHeight;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
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
