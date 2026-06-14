package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.network.ProjectorConfigUpdatePayload;
import com.nododiiiii.ponderer.network.ProjectorManualTriggerPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorMenu;
import com.nododiiiii.ponderer.projector.ProjectorTriggerMode;
import com.nododiiiii.ponderer.projector.client.ProjectorClientSceneResolver;
import com.nododiiiii.ponderer.projector.client.ProjectorSceneCompiler;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ProjectorConfigScreen extends AbstractContainerScreen<ProjectorMenu> {

    private static final ResourceLocation CONTAINER_BACKGROUND =
        new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int TEXT = 0x404040;
    private static final int PANEL_BORDER = 0xFF8B8B8B;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int WHITEBOARD_FILL = 0xFFECE8DD;
    private static final int WHITEBOARD_INSET = 0xFFF7F3E8;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_MID = 0xFF8B8B8B;
    private static final int SCREEN_WIDTH = ProjectorMenu.SCREEN_WIDTH;
    private static final int SCREEN_HEIGHT = 280;
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
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_INSET = 1;
    private static final int SOURCE_SLOT_DRAW_X = ProjectorMenu.SOURCE_SLOT_X;
    private static final int SOURCE_SLOT_DRAW_Y = ProjectorMenu.SOURCE_SLOT_Y;
    private static final int SOURCE_TEXT_X = SOURCE_SLOT_DRAW_X + SLOT_SIZE + 10;
    private static final int SOURCE_TEXT_WIDTH = 148;
    private static final int INVENTORY_LABEL_X = ProjectorMenu.INVENTORY_PANEL_X + 8;
    private static final int INVENTORY_LABEL_Y = ProjectorMenu.INVENTORY_PANEL_Y - 10;
    private static final int LEFT_COLUMN_X = WHITEBOARD_X + FIELD_PADDING_X;
    private static final int RIGHT_COLUMN_X = LEFT_COLUMN_X + FIELD_COLUMN_WIDTH + COLUMN_GAP;
    private static final int ROW_1_Y = SOURCE_ROW_Y + ROW_GAP;
    private static final int ROW_2_Y = ROW_1_Y + ROW_GAP;
    private static final int ROW_3_Y = ROW_2_Y + ROW_GAP;
    private static final int ROW_4_Y = ROW_3_Y + ROW_GAP;
    private static final int ACTION_ROW_TOP_Y = ROW_3_Y - 2;
    private static final int ACTION_ROW_BOTTOM_Y = ROW_4_Y - 2;
    private static final int OFFSET_BOX_WIDTH = 24;
    private static final int OFFSET_GAP = 4;
    private static final int OFFSET_TOTAL_WIDTH = OFFSET_BOX_WIDTH * 3 + OFFSET_GAP * 2;
    private static final int OFFSET_START_X = LEFT_COLUMN_X + LABEL_WIDTH + (VALUE_WIDTH - OFFSET_TOTAL_WIDTH) / 2;

    private CycleButton<Boolean> redstoneModeButton;
    private CycleButton<Boolean> loopModeButton;
    private CycleButton<Boolean> blueTintButton;
    private Button playButton;
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

    private List<String> resolvedSceneKeys = List.of();
    private String sourceFingerprint = "";
    private boolean redstoneMode;
    private boolean loopMode = true;
    private boolean showBlueTint = true;
    private float miniatureScale = 1.0F;
    private float textScale = 1.0F;
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
                value -> Component.translatable("ponderer.ui.projector.blue_tint." + (value ? "on" : "off")),
                value -> showBlueTint = value));
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
                value -> Component.translatable("ponderer.ui.projector.blue_tint." + (value ? "on" : "off")),
                value -> showBlueTint = value));
            playButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce()).bounds(leftPos + RIGHT_COLUMN_X + LABEL_WIDTH, topPos + ROW_3_Y - 2, VALUE_WIDTH, 20).build());
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

        String current = fingerprint(menu.sourceItem());
        if (!current.equals(sourceFingerprint)) {
            refreshResolvedScenes();
        }
    }

    @Override
    public void onClose() {
        applyConfig(false);
        super.onClose();
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
        renderWindowFrame(graphics, x, y, imageWidth, imageHeight);
        renderWhiteboard(graphics, x + WHITEBOARD_X, y + WHITEBOARD_Y, WHITEBOARD_WIDTH, WHITEBOARD_HEIGHT);
        renderInventoryPanel(graphics, x + ProjectorMenu.INVENTORY_PANEL_X, y + ProjectorMenu.INVENTORY_PANEL_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        Component projectorKind = Component.translatable(menu.projectorKind().translationKey());
        graphics.drawString(font, projectorKind, imageWidth - 14 - font.width(projectorKind), TITLE_Y, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        drawRowLabel(graphics, "ponderer.ui.projector.source_item", SOURCE_LABEL_X, SOURCE_ROW_Y + 4);
        graphics.drawString(font, trimToWidth(sceneSummary(), SOURCE_TEXT_WIDTH), SOURCE_TEXT_X, SOURCE_ROW_Y + 4, sceneSummaryColor(), false);

        drawRowLabel(graphics, "ponderer.ui.projector.redstone_mode", LEFT_COLUMN_X, ROW_1_Y + 4);
        drawRowLabel(graphics, "ponderer.ui.projector.loop_mode", RIGHT_COLUMN_X, ROW_1_Y + 4);

        if (menu.projectorKind().requiresAnchor()) {
            graphics.drawString(font, Component.translatable("ponderer.ui.projector.offset"), LEFT_COLUMN_X, ROW_2_Y + 4, TEXT, false);
            drawRowLabel(graphics, "ponderer.ui.projector.text_scale", RIGHT_COLUMN_X, ROW_2_Y + 4);
            drawRowLabel(graphics, "ponderer.ui.projector.blue_tint", LEFT_COLUMN_X, ROW_3_Y + 4);
        } else {
            drawRowLabel(graphics, "ponderer.ui.projector.scale", LEFT_COLUMN_X, ROW_2_Y + 4);
            drawRowLabel(graphics, "ponderer.ui.projector.text_scale", RIGHT_COLUMN_X, ROW_2_Y + 4);
            drawRowLabel(graphics, "ponderer.ui.projector.blue_tint", LEFT_COLUMN_X, ROW_3_Y + 4);
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
        sourceFingerprint = fingerprint(menu.sourceItem());
        resolvedSceneKeys = ProjectorClientSceneResolver.sceneKeysFor(menu.sourceItem());
        playButton.active = !resolvedSceneKeys.isEmpty();

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

    private boolean applyConfig(boolean showStatus) {
        BlockPos offset = parseProjectionOffset();
        if (requiresMissingOffset()) {
            status(Component.translatable("ponderer.ui.projector.offset.required"), 0xA03030);
            return false;
        }

        float scale = parseScale();
        float resolvedTextScale = parseTextScale();
        PondererServices.NETWORK.sendToServer(new ProjectorConfigUpdatePayload(
            menu.projectorPos(),
            resolvedSceneKeys,
            ProjectorTriggerMode.fromFields(redstoneMode, loopMode),
            offset,
            estimateDuration(resolvedSceneKeys),
            showBlueTint,
            scale,
            resolvedTextScale));
        if (showStatus) {
            status(Component.translatable("ponderer.ui.projector.saved"), 0x2E6E2E);
        }
        return true;
    }

    private void triggerManualOnce() {
        if (resolvedSceneKeys.isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.scene.required"), 0xA03030);
            return;
        }
        if (!applyConfig(false)) {
            return;
        }
        PondererServices.NETWORK.sendToServer(new ProjectorManualTriggerPayload(menu.projectorPos()));
        status(Component.translatable("ponderer.ui.projector.play_once.sent"), 0x2E6E2E);
    }

    private boolean requiresMissingOffset() {
        return menu.projectorKind().requiresAnchor() && !resolvedSceneKeys.isEmpty() && parseProjectionOffset() == null;
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

    private int sceneSummaryColor() {
        if (menu.sourceItem().isEmpty()) {
            return 0x606060;
        }
        if (resolvedSceneKeys.isEmpty()) {
            return 0xA03030;
        }
        return 0x2E6E2E;
    }

    private int estimateDuration(List<String> sceneKeys) {
        int total = 0;
        for (String sceneKey : sceneKeys) {
            total += Math.max(0, ProjectorSceneCompiler.estimateTotalTicks(sceneKey));
        }
        return total;
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

    private void renderWindowFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        fillPanel(graphics, x, y, width, height, 0xFFC6C6C6, 0xFFE6E6E6, PANEL_BORDER, PANEL_SHADOW);
    }

    private void renderWhiteboard(GuiGraphics graphics, int x, int y, int width, int height) {
        fillPanel(graphics, x, y, width, height, WHITEBOARD_FILL, WHITEBOARD_INSET, PANEL_BORDER, PANEL_SHADOW);
        drawSourceSlot(graphics, leftPos + SOURCE_SLOT_DRAW_X, topPos + SOURCE_SLOT_DRAW_Y);
    }

    private void renderInventoryPanel(GuiGraphics graphics, int x, int y) {
        graphics.blit(CONTAINER_BACKGROUND, x, y, 0, 126, ProjectorMenu.INVENTORY_PANEL_WIDTH, ProjectorMenu.INVENTORY_PANEL_HEIGHT);
    }

    private void fillPanel(GuiGraphics graphics, int x, int y, int width, int height,
                           int outerFill, int innerFill, int borderColor, int shadowColor) {
        graphics.fill(x, y, x + width, y + height, outerFill);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, innerFill);
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, shadowColor);
        graphics.fill(x, y + height - 1, x + width, y + height, shadowColor);
    }

    private void drawSourceSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_DARK);
        graphics.fill(x, y, x + SLOT_SIZE - 1, y + 1, SLOT_LIGHT);
        graphics.fill(x, y, x + 1, y + SLOT_SIZE - 1, SLOT_LIGHT);
        graphics.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_DARK);
        graphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_DARK);
        graphics.fill(x + SLOT_INSET, y + SLOT_INSET, x + SLOT_SIZE - SLOT_INSET, y + SLOT_SIZE - SLOT_INSET, SLOT_MID);
    }
}
