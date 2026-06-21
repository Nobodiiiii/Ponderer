package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.network.RemoteActionResponsePayload;
import com.nododiiiii.ponderer.network.RemotePullRequestPayload;
import com.nododiiiii.ponderer.network.ProjectorConfigUpdatePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorMenu;
import com.nododiiiii.ponderer.projector.ProjectorProjectionMode;
import com.nododiiiii.ponderer.projector.ProjectorSceneKey;
import com.nododiiiii.ponderer.projector.ProjectorTriggerMode;
import com.nododiiiii.ponderer.projector.client.ProjectorClientSceneResolver;
import com.nododiiiii.ponderer.projector.client.ProjectorSceneBundle;
import com.nododiiiii.ponderer.projector.client.ProjectorSceneCompiler;
import com.nododiiiii.ponderer.projector.ProjectorSceneTimeline;
import com.nododiiiii.ponderer.registry.ModBlocks;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final int TITLE_READY_TEXT = 0x2E6E2E;
    private static final int TITLE_ERROR_TEXT = 0xA03030;
    private static final int STATUS_TEXT = 0x606060;
    private static final int PROJECTOR_TEXTURE_WIDTH = 320;
    private static final int PROJECTOR_TEXTURE_HEIGHT = 480;
    private static final int MAIN_PANEL_WIDTH = 288;
    private static final int RIGHT_TIP_WIDTH = MAIN_PANEL_WIDTH - ProjectorMenu.SCREEN_WIDTH;
    private static final int PLAYER_INVENTORY_WIDTH = 176;
    private static final int PLAYER_INVENTORY_HEIGHT = 108;
    private static final int PLAYER_INVENTORY_TEXTURE_SIZE = 256;
    private static final int PLAYER_INVENTORY_X = ProjectorMenu.INVENTORY_PANEL_X;
    private static final int SCREEN_WIDTH = ProjectorMenu.SCREEN_WIDTH;
    private static final int MINIATURE_PLAYER_INVENTORY_Y = 195;
    private static final int LIFE_SIZE_PLAYER_INVENTORY_Y = 214;
    private static final int MINIATURE_MAIN_PANEL_HEIGHT = 195;
    private static final int LIFE_SIZE_MAIN_PANEL_HEIGHT = 214;
    private static final int MINIATURE_PANEL_TEXTURE_Y = 0;
    private static final int LIFE_SIZE_PANEL_TEXTURE_Y = 203;
    private static final int TITLE_Y = 4;
    private static final int SOURCE_LABEL_TEXT_X = 30;
    private static final int SOURCE_LABEL_TEXT_Y = 30;
    private static final int SOURCE_TEXT_Y = SOURCE_LABEL_TEXT_Y;
    private static final int SOURCE_INFO_X = 98;
    private static final int SOURCE_INFO_WIDTH = 149;
    private static final int REMOTE_PULL_BUTTON_X = 249;
    private static final int REMOTE_PULL_BUTTON_Y = 25;
    private static final int INVENTORY_LABEL_X = PLAYER_INVENTORY_X + 8;
    private static final int LEFT_LABEL_TEXT_X = 28;
    private static final int RIGHT_LABEL_TEXT_X = 159;
    private static final int LABEL_TEXT_MAX_WIDTH = 48;
    private static final int LEFT_VALUE_X = 79;
    private static final int RIGHT_VALUE_X = 210;
    private static final int LEFT_POINTED_VALUE_X = 75;
    private static final int RIGHT_POINTED_VALUE_X = 206;
    private static final int VALUE_WIDTH = 59;
    private static final int POINTED_VALUE_WIDTH = 64;
    private static final int CONTROL_HEIGHT = 18;
    private static final int ROW_1_Y = 55;
    private static final int ROW_2_Y = 74;
    private static final int ROW_3_Y = 93;
    private static final int ROW_4_Y = 112;
    private static final int ROW_5_Y = 131;
    private static final int LABEL_TEXT_OFFSET_Y = 5;
    private static final int SCENE_BUTTON_SIZE = 18;
    private static final int MINIATURE_SCENE_BUTTON_Y = 139;
    private static final int LIFE_SIZE_SCENE_BUTTON_Y = 158;
    private static final int SCENE_LEFT_BUTTON_X = 11;
    private static final int SCENE_RIGHT_BUTTON_X = 251;
    private static final int SCENE_BAR_X = 35;
    private static final int MINIATURE_SCENE_BAR_Y = 148;
    private static final int LIFE_SIZE_SCENE_BAR_Y = 167;
    private static final int SCENE_BAR_WIDTH = 210;
    private static final int SCENE_BAR_HEIGHT = 1;
    private static final int SCENE_STATUS_TEXT_Y_OFFSET = -12;
    private static final int SCENE_KEYFRAME_HIT_RADIUS = 6;
    private static final int OFFSET_BOX_WIDTH = 40;
    private static final int OFFSET_GAP = 0;
    private static final int OFFSET_START_X = 77;
    private static final int MINIATURE_BOTTOM_ACTION_Y = 171;
    private static final int LIFE_SIZE_BOTTOM_ACTION_Y = 190;
    private static final int BOTTOM_STATUS_X = 10;
    private static final int BOTTOM_STATUS_WIDTH = 158;
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
    private static final int SCENE_BUTTON_IDLE_U = 248;
    private static final int SCENE_BUTTON_HOVER_U = 266;
    private static final int SCENE_BUTTON_DISABLED_U = 302;
    private static final int SCENE_BUTTON_STATE_V = 462;
    private static final int SCENE_BUTTON_STATE_SIZE = 18;
    private static final Color SCENE_BUTTON_DISABLED_ICON_COLOR = new Color(0xff_9c9c9c, true);
    private static final int PULL_ICON_U = 0;
    private static final int RESET_ICON_U = 18;
    private static final int PLAY_ICON_U = 36;
    private static final int ACTION_ICON_V = 441;
    private static final int ACTION_ICON_SIZE = 16;
    private static final int ACTION_ICON_OFFSET = 0;
    private static final int PLAY_TEXT_START_X = ACTION_ICON_OFFSET + ACTION_ICON_SIZE + 2;
    private static final int PLAY_TEXT_RIGHT_PADDING = 4;
    private static final int MODEL_AREA_X = MAIN_PANEL_WIDTH + 4;
    private static final int MODEL_AREA_WIDTH = 64;
    private static final int MODEL_AREA_HEIGHT = 56;
    private static final int MINIATURE_MODEL_AREA_Y = MINIATURE_MAIN_PANEL_HEIGHT - 44;
    private static final int LIFE_SIZE_MODEL_AREA_Y = LIFE_SIZE_MAIN_PANEL_HEIGHT - 44;
    private static final int MODEL_RENDER_X = MODEL_AREA_X + MODEL_AREA_WIDTH / 2 + 20;
    private static final int MINIATURE_MODEL_RENDER_Y = MINIATURE_MAIN_PANEL_HEIGHT + 4;
    private static final int LIFE_SIZE_MODEL_RENDER_Y = LIFE_SIZE_MAIN_PANEL_HEIGHT + 4;
    private static final float MODEL_RENDER_SCALE = 40.0F;
    private static final float MODEL_RENDER_X_ROT = -22.0F;
    private static final float MODEL_RENDER_Y_ROT = 243.0F;

    private Button redstoneModeButton;
    private Button loopModeButton;
    private Button blueTintButton;
    private Button textAntiOcclusionButton;
    private Button compatibilityModeButton;
    @Nullable
    private Button projectionModeButton;
    private Button remotePullButton;
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
    private List<DisplaySceneSegment> displaySceneSegments = List.of();
    private String sourceFingerprint = "";
    private int selectedSceneIndex;
    private boolean sceneSelectionDirty;
    private int previewTick;
    private int previewIntermissionTicks = Integer.MIN_VALUE;
    private ScenePreview scenePreview = ScenePreview.EMPTY;
    private boolean redstoneMode;
    private boolean loopMode = true;
    private boolean showBlueTint = true;
    private boolean overlayAntiOcclusion = false;
    private boolean compatibilityMode = true;
    private ProjectorProjectionMode projectionMode = ProjectorProjectionMode.DEFAULT;
    private float miniatureScale = 1.0F;
    private float textScale = 1.0F;
    private int intermissionTicks = ProjectorBlockEntity.DEFAULT_INTERMISSION_TICKS;
    private int manualSceneSelectionHoldTicks;
    private Component statusMessage = Component.empty();
    private int statusColor = 0x606060;

    public ProjectorConfigScreen(ProjectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SCREEN_WIDTH;
        imageHeight = playerInventoryY() + PLAYER_INVENTORY_HEIGHT;
        inventoryLabelX = INVENTORY_LABEL_X;
        inventoryLabelY = playerInventoryY() + 6;
    }

    private boolean isLifeSizeProjector() {
        return menu.projectorKind().requiresAnchor();
    }

    private int playerInventoryY() {
        return isLifeSizeProjector() ? LIFE_SIZE_PLAYER_INVENTORY_Y : MINIATURE_PLAYER_INVENTORY_Y;
    }

    private int mainPanelHeight() {
        return isLifeSizeProjector() ? LIFE_SIZE_MAIN_PANEL_HEIGHT : MINIATURE_MAIN_PANEL_HEIGHT;
    }

    private int sceneButtonY() {
        return isLifeSizeProjector() ? LIFE_SIZE_SCENE_BUTTON_Y : MINIATURE_SCENE_BUTTON_Y;
    }

    private int sceneBarY() {
        return isLifeSizeProjector() ? LIFE_SIZE_SCENE_BAR_Y : MINIATURE_SCENE_BAR_Y;
    }

    private int bottomActionY() {
        return isLifeSizeProjector() ? LIFE_SIZE_BOTTOM_ACTION_Y : MINIATURE_BOTTOM_ACTION_Y;
    }

    private int modelAreaY() {
        return isLifeSizeProjector() ? LIFE_SIZE_MODEL_AREA_Y : MINIATURE_MODEL_AREA_Y;
    }

    private int modelRenderY() {
        return isLifeSizeProjector() ? LIFE_SIZE_MODEL_RENDER_Y : MINIATURE_MODEL_RENDER_Y;
    }

    public List<Rect2i> getJeiExtraAreas() {
        return List.of(
            new Rect2i(leftPos + ProjectorMenu.SCREEN_WIDTH, topPos + modelAreaY(), RIGHT_TIP_WIDTH, mainPanelHeight() - modelAreaY()),
            new Rect2i(leftPos + MODEL_AREA_X, topPos + modelAreaY(), MODEL_AREA_WIDTH, MODEL_AREA_HEIGHT)
        );
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 18;
        titleLabelY = TITLE_Y;
        loadProjectorState();
        configuredSceneKeys = List.of();

        remotePullButton = addRenderableWidget(new ProjectorTextureButton(
            leftPos + REMOTE_PULL_BUTTON_X,
            topPos + REMOTE_PULL_BUTTON_Y,
            RESET_BUTTON_SIZE,
            CONTROL_HEIGHT,
            Component.empty(),
            button -> pullRemoteScenesForSource(),
            ButtonVisual.ACTION_ICON_REMOTE_PULL));
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
            topPos + sceneButtonY(),
            SCENE_BUTTON_SIZE,
            SCENE_BUTTON_SIZE,
            Component.empty(),
            button -> cycleSceneBackward(),
            ButtonVisual.ACTION_ICON_LEFT));
        nextSceneButton = addRenderableWidget(new ProjectorTextureButton(
            leftPos + SCENE_RIGHT_BUTTON_X,
            topPos + sceneButtonY(),
            SCENE_BUTTON_SIZE,
            SCENE_BUTTON_SIZE,
            Component.empty(),
            button -> cycleSceneForward(),
            ButtonVisual.ACTION_ICON_RIGHT));

        if (menu.projectorKind().requiresAnchor()) {
            int offsetXStart = leftPos + OFFSET_START_X;
            offsetX = addRenderableWidget(offsetBox(offsetXStart, topPos + ROW_5_Y));
            offsetY = addRenderableWidget(offsetBox(offsetXStart + OFFSET_BOX_WIDTH + OFFSET_GAP, topPos + ROW_5_Y));
            offsetZ = addRenderableWidget(offsetBox(offsetXStart + (OFFSET_BOX_WIDTH + OFFSET_GAP) * 2, topPos + ROW_5_Y));

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
            projectionModeButton = addRenderableWidget(cycleButton(
                leftPos + LEFT_POINTED_VALUE_X, topPos + ROW_3_Y,
                () -> Component.translatable(projectionMode.translationKey()),
                () -> {
                    projectionMode = projectionMode.next();
                    return Component.translatable(projectionMode.translationKey());
                }));
            compatibilityModeButton = addRenderableWidget(modeButton(
                leftPos + RIGHT_POINTED_VALUE_X, topPos + ROW_3_Y,
                () -> compatibilityMode,
                value -> Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off")),
                value -> compatibilityMode = value));
            intermissionBox = addRenderableWidget(integerBox(
                leftPos + LEFT_VALUE_X, topPos + ROW_4_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.intermission"), intermissionTicks));
            textScaleBox = addRenderableWidget(decimalBox(
                leftPos + RIGHT_VALUE_X, topPos + ROW_4_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.text_scale"), textScale));
            resetProjectionButton = addRenderableWidget(new ProjectorTextureButton(
                leftPos + RESET_BUTTON_X, topPos + bottomActionY(), RESET_BUTTON_SIZE, CONTROL_HEIGHT,
                Component.empty(), button -> resetProjectionControl(), ButtonVisual.ACTION_ICON_RESET));
            playButton = addRenderableWidget(new ProjectorTextureButton(
                leftPos + PLAY_BUTTON_X, topPos + bottomActionY(), PLAY_BUTTON_WIDTH, CONTROL_HEIGHT,
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce(), ButtonVisual.ACTION_PLAY));
        } else {
            compatibilityModeButton = addRenderableWidget(modeButton(
                leftPos + LEFT_POINTED_VALUE_X, topPos + ROW_3_Y,
                () -> compatibilityMode,
                value -> Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off")),
                value -> compatibilityMode = value));
            scaleBox = addRenderableWidget(decimalBox(
                leftPos + RIGHT_VALUE_X, topPos + ROW_3_Y, VALUE_WIDTH,
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
                leftPos + LEFT_VALUE_X, topPos + ROW_4_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.intermission"), intermissionTicks));
            textScaleBox = addRenderableWidget(decimalBox(
                leftPos + RIGHT_VALUE_X, topPos + ROW_4_Y, VALUE_WIDTH,
                Component.translatable("ponderer.ui.projector.text_scale"), textScale));
            resetProjectionButton = addRenderableWidget(new ProjectorTextureButton(
                leftPos + RESET_BUTTON_X, topPos + bottomActionY(), RESET_BUTTON_SIZE, CONTROL_HEIGHT,
                Component.empty(), button -> resetProjectionControl(), ButtonVisual.ACTION_ICON_RESET));
            playButton = addRenderableWidget(new ProjectorTextureButton(
                leftPos + PLAY_BUTTON_X, topPos + bottomActionY(), PLAY_BUTTON_WIDTH, CONTROL_HEIGHT,
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce(), ButtonVisual.ACTION_PLAY));
        }

        remotePullButton.setTooltip(Tooltip.create(Component.translatable("ponderer.ui.projector.remote_pull.tooltip")));
        if (resetProjectionButton != null) {
            resetProjectionButton.setTooltip(Tooltip.create(Component.translatable("ponderer.ui.projector.reset_offset.tooltip")));
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
        if (manualSceneSelectionHoldTicks > 0) {
            manualSceneSelectionHoldTicks--;
        }

        String current = fingerprint(menu.sourceItem());
        if (!current.equals(sourceFingerprint)) {
            refreshResolvedScenes();
        } else {
            refreshScenePreviewIfNeeded();
            syncLiveSceneSelection();
            refreshSceneButtons();
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
        syncLiveSceneSelection();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int panelTextureY = menu.projectorKind().requiresAnchor()
            ? LIFE_SIZE_PANEL_TEXTURE_Y
            : MINIATURE_PANEL_TEXTURE_Y;
        graphics.blit(BACKGROUND, x, y, 0, panelTextureY, MAIN_PANEL_WIDTH, mainPanelHeight(),
            PROJECTOR_TEXTURE_WIDTH, PROJECTOR_TEXTURE_HEIGHT);
        graphics.blit(PLAYER_INVENTORY, x + PLAYER_INVENTORY_X, y + playerInventoryY(),
            0, 0, PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT,
            PLAYER_INVENTORY_TEXTURE_SIZE, PLAYER_INVENTORY_TEXTURE_SIZE);
        renderProjectorModel(graphics, x, y);
        renderSceneTimeline(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component projectorKind = Component.translatable(menu.projectorKind().translationKey());
        graphics.drawString(font, projectorKind, (imageWidth - font.width(projectorKind)) / 2,
            TITLE_Y, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        drawLabel(graphics, "ponderer.ui.projector.source_item", SOURCE_LABEL_TEXT_X, SOURCE_LABEL_TEXT_Y);
        Component sourceStatus = titleStatusLabel();
        if (!sourceStatus.getString().isBlank()) {
            graphics.drawString(font, trimToWidth(sourceStatus, SOURCE_INFO_WIDTH), SOURCE_INFO_X,
                SOURCE_TEXT_Y, titleStatusColor(), false);
        }

        drawLabel(graphics, "ponderer.ui.projector.redstone_mode", LEFT_LABEL_TEXT_X, ROW_1_Y + LABEL_TEXT_OFFSET_Y);
        drawLabel(graphics, "ponderer.ui.projector.loop_mode", RIGHT_LABEL_TEXT_X, ROW_1_Y + LABEL_TEXT_OFFSET_Y);
        drawLabel(graphics, "ponderer.ui.projector.blue_tint", LEFT_LABEL_TEXT_X, ROW_2_Y + LABEL_TEXT_OFFSET_Y);
        drawLabel(graphics, "ponderer.ui.projector.text_anti_occlusion", RIGHT_LABEL_TEXT_X, ROW_2_Y + LABEL_TEXT_OFFSET_Y);

        if (menu.projectorKind().requiresAnchor()) {
            drawLabel(graphics, "ponderer.ui.projector.projection_mode", LEFT_LABEL_TEXT_X, ROW_3_Y + LABEL_TEXT_OFFSET_Y);
            drawLabel(graphics, "ponderer.ui.projector.compatibility_mode", RIGHT_LABEL_TEXT_X, ROW_3_Y + LABEL_TEXT_OFFSET_Y);
            drawLabel(graphics, "ponderer.ui.projector.intermission", LEFT_LABEL_TEXT_X, ROW_4_Y + LABEL_TEXT_OFFSET_Y);
            drawLabel(graphics, "ponderer.ui.projector.text_scale", RIGHT_LABEL_TEXT_X, ROW_4_Y + LABEL_TEXT_OFFSET_Y);
            drawLabel(graphics, "ponderer.ui.projector.offset", LEFT_LABEL_TEXT_X, ROW_5_Y + LABEL_TEXT_OFFSET_Y);
        } else {
            drawLabel(graphics, "ponderer.ui.projector.compatibility_mode", LEFT_LABEL_TEXT_X, ROW_3_Y + LABEL_TEXT_OFFSET_Y);
            drawLabel(graphics, "ponderer.ui.projector.scale", RIGHT_LABEL_TEXT_X, ROW_3_Y + LABEL_TEXT_OFFSET_Y);
            drawLabel(graphics, "ponderer.ui.projector.intermission", LEFT_LABEL_TEXT_X, ROW_4_Y + LABEL_TEXT_OFFSET_Y);
            drawLabel(graphics, "ponderer.ui.projector.text_scale", RIGHT_LABEL_TEXT_X, ROW_4_Y + LABEL_TEXT_OFFSET_Y);
        }

        renderBottomStatus(graphics);
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
        compatibilityMode = projector.compatibilityMode();
        projectionMode = projector.getProjectionMode();
        miniatureScale = projector.getMiniatureScale();
        textScale = projector.getTextScale();
        intermissionTicks = projector.getIntermissionTicks();
    }

    private void applyMode(ProjectorTriggerMode mode) {
        redstoneMode = mode.usesRedstone();
        loopMode = mode.loops();
    }

    private Component redstoneModeLabel() {
        return Component.translatable("ponderer.ui.projector.redstone_mode." + (redstoneMode ? "redstone" : "manual"));
    }

    private Component loopModeLabel() {
        return Component.translatable("ponderer.ui.projector.loop_mode." + (loopMode ? "loop" : "once"));
    }

    private Component toggleLabel(boolean value) {
        return Component.translatable("ponderer.ui.projector.toggle." + (value ? "on" : "off"));
    }

    private void refreshConfigButtonLabels() {
        redstoneModeButton.setMessage(redstoneModeLabel());
        loopModeButton.setMessage(loopModeLabel());
        blueTintButton.setMessage(toggleLabel(showBlueTint));
        textAntiOcclusionButton.setMessage(toggleLabel(overlayAntiOcclusion));
        compatibilityModeButton.setMessage(toggleLabel(compatibilityMode));
        if (projectionModeButton != null) {
            projectionModeButton.setMessage(Component.translatable(projectionMode.translationKey()));
        }
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

    private Button cycleButton(int x, int y, java.util.function.Supplier<Component> currentLabel,
                               java.util.function.Supplier<Component> nextLabel) {
        return new ProjectorTextureButton(x, y, POINTED_VALUE_WIDTH, CONTROL_HEIGHT, currentLabel.get(), button -> {
            button.setMessage(nextLabel.get());
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
        displaySceneSegments = buildDisplaySceneSegments(resolvedSceneKeys);
        if (configuredSceneKeys.isEmpty() || !resolvedSceneKeys.containsAll(configuredSceneKeys)) {
            configuredSceneKeys = List.copyOf(resolvedSceneKeys);
            sceneSelectionDirty = false;
        }
        selectSceneAfterRefresh(previousSelectedSceneKey);
        refreshActionButtonStates();
        refreshScenePreview(true);
        refreshSceneButtons();
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
        sendConfigUpdate(ProjectorTriggerMode.fromFields(redstoneMode, loopMode), offset,
            resolvedIntermissionTicks, showBlueTint, overlayAntiOcclusion, compatibilityMode,
            projectionMode, scale, resolvedTextScale);
        configuredSceneKeys = List.copyOf(sceneKeysToSave);
        sceneSelectionDirty = false;
        if (showStatus) {
            status(Component.translatable("ponderer.ui.projector.saved"), 0x2E6E2E);
        }
        return true;
    }

    private void sendConfigUpdate(ProjectorTriggerMode triggerMode, @Nullable BlockPos offset,
                                  int intermissionTicks, boolean blueTint, boolean resolvedOverlayAntiOcclusion,
                                  boolean resolvedCompatibilityMode, ProjectorProjectionMode resolvedProjectionMode,
                                  float scale, float resolvedTextScale) {
        PondererServices.NETWORK.sendToServer(new ProjectorConfigUpdatePayload(
            menu.projectorPos(),
            triggerMode,
            offset,
            intermissionTicks,
            blueTint,
            resolvedOverlayAntiOcclusion,
            resolvedCompatibilityMode,
            resolvedProjectionMode,
            scale,
            resolvedTextScale));
    }

    private void triggerManualOnce() {
        if (menu.sourceItem().isEmpty()) {
            promptInsertSourceItem();
            return;
        }
        if (resolveSceneKeysToSave().isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.scene.required"), 0xA03030);
            return;
        }
        if (isConfigDirty()) {
            status(Component.translatable("ponderer.ui.projector.save_before_play"), 0xA03030);
            return;
        }
        ProjectorBlockEntity projector = menu.projector();
        if (projector == null) {
            return;
        }
        projector.triggerClientManualOnce();
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
        redstoneMode = false;
        loopMode = true;
        showBlueTint = true;
        overlayAntiOcclusion = false;
        compatibilityMode = true;
        projectionMode = ProjectorProjectionMode.DEFAULT;
        refreshConfigButtonLabels();

        if (textScaleBox != null) {
            textScaleBox.setValue(trimFloat(1.0F));
        }
        if (intermissionBox != null) {
            intermissionBox.setValue(String.valueOf(ProjectorBlockEntity.DEFAULT_INTERMISSION_TICKS));
        }
        if (scaleBox != null) {
            scaleBox.setValue(trimFloat(1.0F));
        }
        if (menu.projectorKind().requiresAnchor()) {
            resetOffset();
        }
        refreshScenePreview(false);
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
        return Component.translatable("ponderer.ui.projector.scenes_detected", displaySceneCount());
    }

    private Component titleStatusLabel() {
        if (menu.sourceItem().isEmpty()) {
            return Component.empty();
        }
        if (resolvedSceneKeys.isEmpty()) {
            return Component.translatable("ponderer.ui.projector.no_scenes_for_item");
        }
        return Component.translatable("ponderer.ui.projector.scenes_detected", displaySceneCount());
    }

    private int titleStatusColor() {
        if (menu.sourceItem().isEmpty()) {
            return TEXT;
        }
        return resolvedSceneKeys.isEmpty() ? TITLE_ERROR_TEXT : TITLE_READY_TEXT;
    }

    private int displaySceneCount() {
        return displaySceneSegments.isEmpty() ? resolvedSceneKeys.size() : displaySceneSegments.size();
    }

    private Component sceneSummaryLabel() {
        if (menu.sourceItem().isEmpty() || resolvedSceneKeys.isEmpty()) {
            return sceneSummary();
        }

        PreviewSegment activeSegment = activePreviewSegment();
        int displayIndex = displaySceneIndex(selectedSceneKey(), activeSegment.segmentOrdinal());
        String title = activeSegment.title().isBlank()
            ? displaySceneTitle(selectedSceneKey(), scenePreview.title())
            : activeSegment.title();
        String prefix = "[" + (displayIndex + 1) + "/" + displaySceneCount() + "] ";
        return Component.literal(prefix + title);
    }

    private PreviewSegment activePreviewSegment() {
        Integer liveTick = resolveLiveSceneTimelineTick();
        return activePreviewSegmentForTick(liveTick == null ? previewTick : liveTick);
    }

    private PreviewSegment activePreviewSegmentForTick(int tick) {
        if (scenePreview.segments().isEmpty()) {
            return PreviewSegment.EMPTY;
        }

        int labelTick = Math.max(0, Math.min(scenePreview.totalTicks(), tick));
        for (int index = 0; index < scenePreview.segments().size(); index++) {
            PreviewSegment segment = scenePreview.segments().get(index);
            int segmentEnd = segment.startTick() + Math.max(1, segment.durationTicks());
            int holdEnd = index < scenePreview.segments().size() - 1
                ? scenePreview.segments().get(index + 1).startTick()
                : scenePreview.totalTicks();
            if (labelTick >= segment.startTick() && labelTick < Math.max(segmentEnd, holdEnd)) {
                return segment;
            }
        }
        return scenePreview.segments().get(scenePreview.segments().size() - 1);
    }

    private int displaySceneIndex(String sceneKey, int segmentOrdinal) {
        if (displaySceneSegments.isEmpty()) {
            return Math.max(0, Math.min(selectedSceneIndex, Math.max(0, resolvedSceneKeys.size() - 1)));
        }
        for (int index = 0; index < displaySceneSegments.size(); index++) {
            DisplaySceneSegment segment = displaySceneSegments.get(index);
            if (segment.sceneKey().equals(sceneKey) && segment.segmentOrdinal() == segmentOrdinal) {
                return index;
            }
        }
        for (int index = 0; index < displaySceneSegments.size(); index++) {
            if (displaySceneSegments.get(index).sceneKey().equals(sceneKey)) {
                return index;
            }
        }
        return Math.max(0, Math.min(selectedSceneIndex, displaySceneSegments.size() - 1));
    }

    private int currentDisplaySceneIndex() {
        if (resolvedSceneKeys.isEmpty()) {
            return -1;
        }
        PreviewSegment activeSegment = activePreviewSegment();
        return displaySceneIndex(selectedSceneKey(), activeSegment.segmentOrdinal());
    }

    private int segmentStartTick(int segmentOrdinal) {
        for (PreviewSegment segment : scenePreview.segments()) {
            if (segment.segmentOrdinal() == segmentOrdinal) {
                return Math.max(0, Math.min(scenePreview.totalTicks(), segment.startTick()));
            }
        }
        return 0;
    }

    private String displaySceneTitle(String sceneKey, String title) {
        ProjectorSceneKey.Native nativeKey = ProjectorSceneKey.parseNative(sceneKey);
        if (nativeKey != null) {
            return nativeKey.sceneId().getPath();
        }
        if (title == null || title.isBlank()) {
            return sceneKey;
        }
        return title;
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
        if (compatibilityMode != (projector == null ? compatibilityMode : projector.compatibilityMode())) {
            return true;
        }
        if (projectionMode != (projector == null ? projectionMode : projector.getProjectionMode())) {
            return true;
        }
        return showBlueTint != (projector == null ? showBlueTint : projector.showBlueTint());
    }

    private ProjectorTriggerMode currentMode() {
        return ProjectorTriggerMode.fromFields(redstoneMode, loopMode);
    }

    private void cycleSceneBackward() {
        int currentIndex = currentDisplaySceneIndex();
        if (currentIndex <= 0) {
            return;
        }
        selectDisplayScene(currentIndex - 1);
    }

    private void cycleSceneForward() {
        int currentIndex = currentDisplaySceneIndex();
        if (currentIndex < 0 || currentIndex >= displaySceneCount() - 1) {
            return;
        }
        selectDisplayScene(currentIndex + 1);
    }

    private void selectDisplayScene(int displayIndex) {
        if (displaySceneSegments.isEmpty()) {
            return;
        }

        int clampedIndex = Math.max(0, Math.min(displayIndex, displaySceneSegments.size() - 1));
        DisplaySceneSegment target = displaySceneSegments.get(clampedIndex);
        int targetSceneIndex = resolvedSceneKeys.indexOf(target.sceneKey());
        if (targetSceneIndex < 0) {
            return;
        }

        selectedSceneIndex = targetSceneIndex;

        refreshScenePreview(false);
        previewTick = segmentStartTick(target.segmentOrdinal());
        refreshSceneButtons(clampedIndex);
        suppressSceneButtonHoverOnce();
        manualSceneSelectionHoldTicks = 3;
        refreshActionButtonStates();

        if (!isConfigDirty()) {
            seekSelectedSceneTimeline(previewTick);
        }
    }

    private void applySelectedSceneImmediately() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector == null) {
            return;
        }

        List<String> sceneKeysToSave = resolveSceneKeysToSave();
        sendConfigUpdate(projector.getTriggerMode(), projector.getProjectionOffset(),
            projector.getIntermissionTicks(), projector.showBlueTint(), projector.overlayAntiOcclusion(),
            projector.compatibilityMode(), projector.getProjectionMode(), projector.getMiniatureScale(),
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
        refreshSceneButtons(currentDisplaySceneIndex());
    }

    private void refreshSceneButtons(int currentIndex) {
        boolean hasScenes = displaySceneCount() > 0;
        boolean multipleScenes = displaySceneCount() > 1;
        if (previousSceneButton != null) {
            previousSceneButton.visible = hasScenes;
            previousSceneButton.active = multipleScenes && currentIndex > 0;
        }
        if (nextSceneButton != null) {
            nextSceneButton.visible = hasScenes;
            nextSceneButton.active = multipleScenes && currentIndex >= 0 && currentIndex < displaySceneCount() - 1;
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

    private void syncLiveSceneSelection() {
        if (manualSceneSelectionHoldTicks > 0) {
            return;
        }

        LiveSceneCursor cursor = resolveLiveSceneCursor();
        if (cursor == null) {
            return;
        }

        int resolvedIndex = resolvedSceneKeys.indexOf(cursor.sceneKey());
        if (resolvedIndex < 0) {
            return;
        }

        if (resolvedIndex != selectedSceneIndex) {
            selectedSceneIndex = resolvedIndex;
            sceneSelectionDirty = false;
            refreshScenePreview(false);
        }
        previewTick = Math.max(0, Math.min(scenePreview.totalTicks(), cursor.sceneTick()));
    }

    private String selectedSceneKey() {
        if (resolvedSceneKeys.isEmpty()) {
            return "";
        }
        int clampedIndex = Math.max(0, Math.min(selectedSceneIndex, resolvedSceneKeys.size() - 1));
        return resolvedSceneKeys.get(clampedIndex);
    }

    private List<DisplaySceneSegment> buildDisplaySceneSegments(List<String> sceneKeys) {
        if (sceneKeys == null || sceneKeys.isEmpty()) {
            return List.of();
        }

        List<DisplaySceneSegment> segments = new ArrayList<>();
        for (String sceneKey : sceneKeys) {
            if (sceneKey == null || sceneKey.isBlank()) {
                continue;
            }
            DslScene dslScene = SceneRuntime.findByKey(sceneKey);
            String sceneTitle = resolveSceneTitle(sceneKey, dslScene);
            try {
                ProjectorSceneBundle bundle = ProjectorSceneBundle.compile(sceneKey);
                if (bundle != null && !bundle.segments().isEmpty()) {
                    for (int ordinal = 0; ordinal < bundle.segments().size(); ordinal++) {
                        ProjectorSceneBundle.Segment segment = bundle.segments().get(ordinal);
                        segments.add(new DisplaySceneSegment(sceneKey, ordinal,
                            resolveSegmentTitle(dslScene, segment.segmentIndex(), segment.scene().getTitle(), sceneTitle)));
                    }
                    continue;
                }
            } catch (Throwable ignored) {
            }

            if (dslScene != null && dslScene.scenes != null && !dslScene.scenes.isEmpty()) {
                for (int ordinal = 0; ordinal < dslScene.scenes.size(); ordinal++) {
                    segments.add(new DisplaySceneSegment(sceneKey, ordinal,
                        resolveSegmentTitle(dslScene, ordinal, "", sceneTitle)));
                }
            } else {
                segments.add(new DisplaySceneSegment(sceneKey, 0, displaySceneTitle(sceneKey, sceneTitle)));
            }
        }

        return List.copyOf(segments);
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
        int y = topPos + sceneBarY();
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
        Integer liveTick = resolveLiveSceneTimelineTick();
        int animatedTick = Math.max(0, Math.min(scenePreview.totalTicks(), liveTick == null ? previewTick : liveTick));
        PreviewSegment activeSegment = activePreviewSegmentForTick(animatedTick);
        int segmentStart = activeSegment.startTick();
        int segmentDuration = Math.max(1, activeSegment.durationTicks());
        int segmentEnd = segmentStart + segmentDuration;

        int startDistance = Math.abs(keyframeMouseX - timelineKeyframePosition(0, segmentDuration));
        if (startDistance <= SCENE_KEYFRAME_HIT_RADIUS) {
            hoveredKeyframe = segmentStart;
            hoveredDistance = startDistance;
        }

        if (!scenePreview.keyframes().contains(segmentEnd)) {
            int endDistance = Math.abs(keyframeMouseX - timelineKeyframePosition(segmentDuration, segmentDuration));
            if (endDistance <= SCENE_KEYFRAME_HIT_RADIUS && endDistance < hoveredDistance) {
                hoveredKeyframe = segmentEnd;
                hoveredDistance = endDistance;
            }
        }

        for (int keyframeTick : scenePreview.keyframes()) {
            if (keyframeTick < segmentStart || keyframeTick > segmentEnd) {
                continue;
            }
            int localKeyframeTick = Math.max(0, Math.min(segmentDuration, keyframeTick - segmentStart));
            int keyframePos = timelineKeyframePosition(localKeyframeTick, segmentDuration);
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
        projector.seekClientPlaybackToTick(Math.max(0, playbackTick));
    }

    private List<String> currentProjectorSceneKeys() {
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
        if (scenePreview.totalTicks() <= 0) {
            return null;
        }

        LiveSceneCursor cursor = resolveLiveSceneCursor();
        if (cursor != null && cursor.sceneKey().equals(selectedSceneKey())) {
            return Math.max(0, Math.min(scenePreview.totalTicks(), cursor.sceneTick()));
        }
        return null;
    }

    @Nullable
    private LiveSceneCursor resolveLiveSceneCursor() {
        if (isConfigDirty()) {
            return null;
        }

        ProjectorBlockEntity projector = menu.projector();
        if (projector == null || !projector.isPlaying()) {
            return null;
        }

        List<String> sceneKeys = currentProjectorSceneKeys();
        if (sceneKeys.isEmpty()) {
            return null;
        }

        Integer playbackTick = resolveLivePlaybackTick(projector);
        if (playbackTick == null) {
            return null;
        }

        int safeIntermission = Math.max(0, projector.getIntermissionTicks());
        List<LiveDisplaySegment> timeline = buildLiveDisplayTimeline(sceneKeys, projector.getIntermissionTicks());
        for (int index = 0; index < timeline.size(); index++) {
            LiveDisplaySegment segment = timeline.get(index);
            int activeEnd = segment.startTick() + segment.durationTicks();
            int holdEnd = index < timeline.size() - 1
                ? timeline.get(index + 1).startTick()
                : activeEnd + (projector.isPlaybackLooping() ? safeIntermission : 0);
            if (playbackTick < activeEnd) {
                return new LiveSceneCursor(segment.displayIndex(), segment.sceneKey(),
                    segment.sceneStartTick() + Math.max(0, playbackTick - segment.startTick()));
            }
            if (playbackTick < holdEnd) {
                return new LiveSceneCursor(segment.displayIndex(), segment.sceneKey(),
                    segment.sceneStartTick() + segment.durationTicks());
            }
        }

        if (timeline.isEmpty()) {
            return null;
        }
        LiveDisplaySegment last = timeline.get(timeline.size() - 1);
        return new LiveSceneCursor(last.displayIndex(), last.sceneKey(),
            last.sceneStartTick() + last.durationTicks());
    }

    private List<LiveDisplaySegment> buildLiveDisplayTimeline(List<String> sceneKeys, int intermissionTicks) {
        List<LiveDisplaySegment> timeline = new ArrayList<>();
        int playbackTimeline = 0;
        int safeIntermission = Math.max(0, intermissionTicks);
        for (int sceneIndex = 0; sceneIndex < sceneKeys.size(); sceneIndex++) {
            String sceneKey = sceneKeys.get(sceneIndex);
            ScenePreview preview = sceneKey.equals(scenePreview.sceneKey())
                ? scenePreview
                : buildScenePreview(sceneKey, intermissionTicks);
            if (preview.totalTicks() <= 0 || preview.segments().isEmpty()) {
                continue;
            }

            for (PreviewSegment segment : preview.segments()) {
                int displayIndex = exactDisplaySceneIndex(sceneKey, segment.segmentOrdinal());
                if (displayIndex < 0) {
                    continue;
                }
                timeline.add(new LiveDisplaySegment(displayIndex, sceneKey, segment.segmentOrdinal(),
                    playbackTimeline + segment.startTick(), segment.startTick(),
                    Math.max(1, segment.durationTicks())));
            }

            playbackTimeline += preview.totalTicks();
            if (sceneIndex < sceneKeys.size() - 1) {
                playbackTimeline += safeIntermission;
            }
        }
        return List.copyOf(timeline);
    }

    private int exactDisplaySceneIndex(String sceneKey, int segmentOrdinal) {
        for (int index = 0; index < displaySceneSegments.size(); index++) {
            DisplaySceneSegment segment = displaySceneSegments.get(index);
            if (segment.sceneKey().equals(sceneKey) && segment.segmentOrdinal() == segmentOrdinal) {
                return index;
            }
        }
        return -1;
    }

    private void suppressSceneButtonHoverOnce() {
        if (previousSceneButton instanceof ProjectorTextureButton previous) {
            previous.suppressHoverOnce();
        }
        if (nextSceneButton instanceof ProjectorTextureButton next) {
            next.suppressHoverOnce();
        }
    }

    @Nullable
    private Integer resolveLivePlaybackTick(ProjectorBlockEntity projector) {
        if (projector == null || !projector.isPlaying()) {
            return null;
        }

        Level level = projector.getLevel();
        if (level == null) {
            level = Minecraft.getInstance().level;
        }
        if (level == null) {
            return null;
        }

        List<String> sceneKeys = currentProjectorSceneKeys();
        int totalDuration = 0;
        ProjectorSceneBundle compiled = ProjectorSceneBundle.compile(sceneKeys);
        if (compiled != null && compiled.totalDurationTicks() > 0) {
            totalDuration = ProjectorSceneTimeline.withIntermissions(
                compiled.totalDurationTicks(),
                compiled.segments().size(),
                projector.getIntermissionTicks(),
                projector.isPlaybackLooping());
        } else if (totalDuration <= 0) {
            totalDuration = estimateDuration(sceneKeys, projector.getIntermissionTicks());
            if (projector.isPlaybackLooping() && totalDuration > 0) {
                totalDuration += Math.max(0, projector.getIntermissionTicks());
            }
        }

        int playbackTick = projector.resolveDisplayPlaybackTick(totalDuration, true);
        return playbackTick == ProjectorSceneTimeline.NO_PLAYBACK_TICK ? null : playbackTick;
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
                List<PreviewSegment> previewSegments = new ArrayList<>();
                int timeline = 0;
                for (int segmentIndex = 0; segmentIndex < bundle.segments().size(); segmentIndex++) {
                    ProjectorSceneBundle.Segment segment = bundle.segments().get(segmentIndex);
                    int duration = Math.max(1, segment.durationTicks());
                    previewSegments.add(new PreviewSegment(segmentIndex,
                        resolveSegmentTitle(dslScene, segment.segmentIndex(), segment.scene().getTitle(), title),
                        timeline, duration));
                    for (int keyframeIndex = 0; keyframeIndex < segment.scene().getKeyframeCount(); keyframeIndex++) {
                        keyframes.add(timeline + segment.scene().getKeyframeTime(keyframeIndex));
                    }
                    timeline += duration;
                    if (segmentIndex < bundle.segments().size() - 1) {
                        timeline += Math.max(0, intermissionTicks);
                    }
                }
                return new ScenePreview(sceneKey, title == null ? sceneKey : title, timeline,
                    List.copyOf(keyframes), List.copyOf(previewSegments));
            }
        } catch (Throwable ignored) {
        }

        if (dslScene != null && dslScene.scenes != null && !dslScene.scenes.isEmpty()) {
            List<PreviewSegment> previewSegments = new ArrayList<>();
            int segmentTimeline = 0;
            for (int segmentIndex = 0; segmentIndex < dslScene.scenes.size(); segmentIndex++) {
                DslScene.SceneSegment segment = dslScene.scenes.get(segmentIndex);
                int duration = Math.max(1, ProjectorSceneCompiler.estimateSegmentTicks(segment));
                previewSegments.add(new PreviewSegment(segmentIndex,
                    resolveSegmentTitle(dslScene, segmentIndex, "", title), segmentTimeline, duration));
                segmentTimeline += duration;
                if (segmentIndex < dslScene.scenes.size() - 1) {
                    segmentTimeline += Math.max(0, intermissionTicks);
                }
            }
            int timeline = ProjectorSceneTimeline.withIntermissions(
                ProjectorSceneTimeline.estimateTotalTicks(sceneKey),
                dslScene.scenes.size(),
                intermissionTicks,
                false);
            return new ScenePreview(sceneKey, title, timeline,
                ProjectorSceneTimeline.estimateKeyframeTicks(dslScene, intermissionTicks),
                List.copyOf(previewSegments));
        }

        int totalTicks = Math.max(0, ProjectorSceneCompiler.estimateTotalTicks(sceneKey));
        return new ScenePreview(sceneKey, title, totalTicks, List.of(),
            List.of(new PreviewSegment(0, displaySceneTitle(sceneKey, title), 0, totalTicks)));
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

    private String resolveSegmentTitle(@Nullable DslScene dslScene, int segmentIndex,
                                       @Nullable String compiledTitle, @Nullable String sceneTitle) {
        if (dslScene != null && dslScene.scenes != null
            && segmentIndex >= 0 && segmentIndex < dslScene.scenes.size()) {
            DslScene.SceneSegment segment = dslScene.scenes.get(segmentIndex);
            if (segment != null) {
                if (segment.title != null) {
                    String title = segment.title.resolve();
                    if (!title.isBlank()) {
                        return title;
                    }
                }
                if (segment.id != null && !segment.id.isBlank()) {
                    return segment.id;
                }
            }
        }
        if (compiledTitle != null && !compiledTitle.isBlank()) {
            return compiledTitle;
        }
        return sceneTitle == null || sceneTitle.isBlank() ? "" : sceneTitle;
    }

    private void renderSceneTimeline(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (resolvedSceneKeys.isEmpty()) {
            return;
        }

        int x = leftPos + SCENE_BAR_X;
        int y = topPos + sceneBarY();
        new BoxElement()
            .withBackground(PonderUI.BACKGROUND_FLAT)
            .gradientBorder(PonderUI.COLOR_IDLE)
            .at(x, y, 400)
            .withBounds(SCENE_BAR_WIDTH, SCENE_BAR_HEIGHT)
            .render(graphics);

        renderSceneProgressStatus(graphics, x, y);

        if (scenePreview.totalTicks() <= 0) {
            return;
        }

        PoseStack poseStack = graphics.pose();
        Integer liveTick = resolveLiveSceneTimelineTick();
        int animatedTick = Math.max(0, Math.min(scenePreview.totalTicks(), liveTick == null ? previewTick : liveTick));
        PreviewSegment activeSegment = activePreviewSegmentForTick(animatedTick);
        int segmentDuration = Math.max(1, activeSegment.durationTicks());
        int segmentStart = activeSegment.startTick();
        int localTick = Math.max(0, Math.min(segmentDuration, animatedTick - segmentStart));
        float progress = Math.max(0.0F, Math.min(1.0F, localTick / (float) segmentDuration));
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
        if (hoveredKeyframe != null && hoveredKeyframe == segmentStart
            && !scenePreview.keyframes().contains(segmentStart)) {
            int keyframePos = timelineKeyframePosition(0, segmentDuration);
            UIRenderHelper.drawGradientRect(poseStack.last().pose(), 320, keyframePos, 0f,
                keyframePos + 2f, 9f, passedColors.getFirst(), passedColors.getSecond());
        }
        if (hoveredKeyframe != null && hoveredKeyframe == segmentStart + segmentDuration
            && !scenePreview.keyframes().contains(segmentStart + segmentDuration)) {
            int keyframePos = timelineKeyframePosition(segmentDuration, segmentDuration);
            UIRenderHelper.drawGradientRect(poseStack.last().pose(), 320, keyframePos, 0f,
                keyframePos + 2f, 9f, passedColors.getFirst(), passedColors.getSecond());
        }
        for (int keyframeTick : scenePreview.keyframes()) {
            if (keyframeTick < segmentStart || keyframeTick > segmentStart + segmentDuration) {
                continue;
            }
            int localKeyframeTick = Math.max(0, Math.min(segmentDuration, keyframeTick - segmentStart));
            int keyframePos = timelineKeyframePosition(localKeyframeTick, segmentDuration);
            boolean hovered = hoveredKeyframe != null && hoveredKeyframe == keyframeTick;
            boolean passed = animatedTick >= keyframeTick;
            var colors = hovered ? passedColors : (passed ? passedColors : futureColors);
            int height = hovered ? 8 : 4;
            UIRenderHelper.drawGradientRect(poseStack.last().pose(), 320, keyframePos, 0f,
                keyframePos + 2f, 1f + height, colors.getFirst(), colors.getSecond());
        }
        poseStack.popPose();
    }

    private void renderSceneProgressStatus(GuiGraphics graphics, int x, int y) {
        if (resolvedSceneKeys.isEmpty()) {
            return;
        }
        Component label = trimToWidth(sceneSummaryLabel(), SCENE_BAR_WIDTH);
        graphics.drawString(font, label, x + (SCENE_BAR_WIDTH - font.width(label)) / 2,
            y + SCENE_STATUS_TEXT_Y_OFFSET, TEXT, false);
    }

    private int timelineKeyframePosition(int keyframeTick, int durationTicks) {
        return (int) (((float) keyframeTick) / Math.max(1.0F, (float) durationTicks)
            * (SCENE_BAR_WIDTH + 2));
    }

    private String fingerprint(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return stack.save(new CompoundTag()).toString();
    }

    private void pullRemoteScenesForSource() {
        ItemStack source = menu.sourceItem();
        if (source.isEmpty()) {
            promptInsertSourceItem();
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(source.getItem());
        if (itemId == null) {
            status(Component.translatable("ponderer.ui.projector.remote_pull.no_item"), TITLE_ERROR_TEXT);
            return;
        }

        PondererServices.NETWORK.sendToServer(new RemotePullRequestPayload(
            RemoteWorkspaceService.KIND_ITEM_SCENES, itemId.toString(), null, true));
        status(Component.translatable("ponderer.ui.projector.remote_pull.requesting", itemId), STATUS_TEXT);
    }

    private void refreshActionButtonStates() {
        remotePullButton.active = true;
        playButton.active = menu.sourceItem().isEmpty() || !resolveSceneKeysToSave().isEmpty();
    }

    private void promptInsertSourceItem() {
        status(Component.translatable("ponderer.ui.projector.source_item.required"), 0xA03030);
    }

    public void receiveRemoteAction(RemoteActionResponsePayload payload) {
        if (payload.message() != null && !payload.message().isBlank()) {
            status(localizedRemotePullMessage(payload.message()), payload.success() ? TITLE_READY_TEXT : TITLE_ERROR_TEXT);
        }
        if (payload.success()) {
            refreshResolvedScenes();
        }
    }

    private Component localizedRemotePullMessage(String message) {
        String noRemotePrefix = "No remote scenes found for item ";
        if (message.startsWith(noRemotePrefix)) {
            return Component.translatable("ponderer.ui.projector.remote_pull.none",
                message.substring(noRemotePrefix.length()));
        }

        String pulledPrefix = "Pulled ";
        String pulledSuffix = " remote scene(s) for item ";
        if (message.startsWith(pulledPrefix)) {
            int suffixStart = message.indexOf(pulledSuffix, pulledPrefix.length());
            if (suffixStart > pulledPrefix.length()) {
                String count = message.substring(pulledPrefix.length(), suffixStart);
                String itemId = message.substring(suffixStart + pulledSuffix.length());
                return Component.translatable("ponderer.ui.projector.remote_pull.done", count, itemId);
            }
        }

        return Component.literal(message);
    }

    private void renderBottomStatus(GuiGraphics graphics) {
        if (statusMessage.getString().isBlank()) {
            return;
        }
        Component message = trimToWidth(statusMessage, BOTTOM_STATUS_WIDTH);
        graphics.drawString(font, message, BOTTOM_STATUS_X, bottomActionY() + 5, statusColor, false);
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
        Component label = trimToWidth(Component.translatable(key), LABEL_TEXT_MAX_WIDTH);
        graphics.drawString(font, label, x + (LABEL_TEXT_MAX_WIDTH - font.width(label)) / 2, y, LABEL_TEXT, false);
    }

    private void renderProjectorModel(GuiGraphics graphics, int x, int y) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        TransformStack.of(pose)
            .pushPose()
            .translate(x + MODEL_RENDER_X, y + modelRenderY(), 100)
            .scale(MODEL_RENDER_SCALE)
            .rotateXDegrees(MODEL_RENDER_X_ROT)
            .rotateYDegrees(MODEL_RENDER_Y_ROT);
        GuiGameElement.of(projectorDisplayState())
            .render(graphics);
        pose.popPose();
    }

    private net.minecraft.world.level.block.state.BlockState projectorDisplayState() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector != null) {
            return projector.getBlockState();
        }

        Direction facing = projectorFacing();
        return (menu.projectorKind().requiresAnchor()
            ? ModBlocks.LIFE_SIZE_PROJECTOR.get()
            : ModBlocks.MINIATURE_PROJECTOR.get())
            .defaultBlockState()
            .setValue(ProjectorBlock.FACING, facing);
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
        ACTION_ICON_REMOTE_PULL,
        ACTION_ICON_RESET,
        ACTION_ICON_LEFT,
        ACTION_ICON_RIGHT,
        ACTION_PLAY
    }

    private static class ProjectorTextureButton extends Button {
        private final ButtonVisual visual;
        private boolean pressed;
        private boolean suppressHoverOnce;

        private ProjectorTextureButton(int x, int y, int width, int height, Component message, OnPress onPress,
                                       ButtonVisual visual) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.visual = visual;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            pressed = true;
            super.onClick(mouseX, mouseY);
            if (isSceneButtonVisual()) {
                suppressHoverOnce = true;
            }
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            pressed = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            if (suppressHoverOnce) {
                hovered = false;
                suppressHoverOnce = false;
            }
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
            } else if (isSceneButtonVisual()) {
                v = SCENE_BUTTON_STATE_V;
                sourceWidth = SCENE_BUTTON_STATE_SIZE;
                u = active
                    ? (hovered ? SCENE_BUTTON_HOVER_U : SCENE_BUTTON_IDLE_U)
                    : SCENE_BUTTON_DISABLED_U;
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
            if (visual == ButtonVisual.ACTION_ICON_REMOTE_PULL) {
                if (renderedOverlay) {
                    renderTextureIcon(graphics, getX() + ACTION_ICON_OFFSET, getY() + ACTION_ICON_OFFSET,
                        PULL_ICON_U, ACTION_ICON_V);
                }
                return;
            }
            if (visual == ButtonVisual.ACTION_ICON_RESET) {
                if (renderedOverlay) {
                    renderTextureIcon(graphics, getX() + ACTION_ICON_OFFSET, getY() + ACTION_ICON_OFFSET,
                        RESET_ICON_U, ACTION_ICON_V);
                }
                return;
            }
            if (visual == ButtonVisual.ACTION_ICON_LEFT) {
                renderSceneButtonIcon(graphics, PonderGuiTextures.ICON_PONDER_LEFT);
                return;
            }
            if (visual == ButtonVisual.ACTION_ICON_RIGHT) {
                renderSceneButtonIcon(graphics, PonderGuiTextures.ICON_PONDER_RIGHT);
                return;
            }
            if (visual == ButtonVisual.ACTION_PLAY) {
                if (renderedOverlay) {
                    renderTextureIcon(graphics, getX() + ACTION_ICON_OFFSET, getY() + ACTION_ICON_OFFSET,
                        PLAY_ICON_U, ACTION_ICON_V);
                }
                int textAreaX = getX() + PLAY_TEXT_START_X;
                int textAreaWidth = width - PLAY_TEXT_START_X - PLAY_TEXT_RIGHT_PADDING;
                String text = font.plainSubstrByWidth(getMessage().getString(), textAreaWidth);
                graphics.drawString(font, text, textAreaX + (textAreaWidth - font.width(text)) / 2,
                    getY() + 5, color, false);
                return;
            }

            int textWidth = width - VALUE_TEXT_OFFSET_X - 6;
            String text = font.plainSubstrByWidth(getMessage().getString(), textWidth);
            graphics.drawString(font, text, getX() + VALUE_TEXT_OFFSET_X + 3 + (textWidth - font.width(text)) / 2,
                getY() + 5, color, false);
        }

        private boolean isSceneButtonVisual() {
            return visual == ButtonVisual.ACTION_ICON_LEFT || visual == ButtonVisual.ACTION_ICON_RIGHT;
        }

        private void suppressHoverOnce() {
            suppressHoverOnce = true;
        }

        private void renderSceneButtonIcon(GuiGraphics graphics, PonderGuiTextures icon) {
            if (active) {
                icon.render(graphics, getX() + 1, getY() + 1);
            } else {
                icon.render(graphics, getX() + 1, getY() + 1, SCENE_BUTTON_DISABLED_ICON_COLOR);
            }
        }

        private void renderTextureIcon(GuiGraphics graphics, int x, int y, int sourceU, int sourceV) {
            graphics.blit(BACKGROUND, x, y, sourceU, sourceV, ACTION_ICON_SIZE, ACTION_ICON_SIZE,
                PROJECTOR_TEXTURE_WIDTH, PROJECTOR_TEXTURE_HEIGHT);
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

    private record DisplaySceneSegment(String sceneKey, int segmentOrdinal, String title) {
    }

    private record PreviewSegment(int segmentOrdinal, String title, int startTick, int durationTicks) {
        private static final PreviewSegment EMPTY = new PreviewSegment(0, "", 0, 0);
    }

    private record ScenePreview(String sceneKey, String title, int totalTicks, List<Integer> keyframes,
                                List<PreviewSegment> segments) {
        private static final ScenePreview EMPTY = new ScenePreview("", "", 0, List.of(), List.of());
    }

    private record LiveSceneCursor(int displayIndex, String sceneKey, int sceneTick) {
    }

    private record LiveDisplaySegment(int displayIndex, String sceneKey, int segmentOrdinal, int startTick,
                                      int sceneStartTick, int durationTicks) {
    }

    private record SceneTimelineWindow(int startTick, int sceneDurationTicks, int holdEndTick, boolean isLastScene) {
        private int activeEndTick() {
            return startTick + sceneDurationTicks;
        }
    }
}
