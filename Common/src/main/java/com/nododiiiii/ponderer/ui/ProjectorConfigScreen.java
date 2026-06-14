package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.network.ProjectorConfigUpdatePayload;
import com.nododiiiii.ponderer.network.ProjectorManualTriggerPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorMenu;
import com.nododiiiii.ponderer.projector.ProjectorTriggerMode;
import com.nododiiiii.ponderer.projector.client.ProjectorClientSceneResolver;
import com.nododiiiii.ponderer.projector.client.ProjectorSceneCompiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ProjectorConfigScreen extends AbstractContainerScreen<ProjectorMenu> {

    private static final int PANEL_COLOR = 0xE6121519;
    private static final int PANEL_BORDER = 0xFF5D7685;
    private static final int SLOT_BG = 0xFF252B31;
    private static final int TEXT = 0xFFE7EEF2;
    private static final int MUTED = 0xFF9BAAB4;
    private static final int ERROR = 0xFFFF8A8A;
    private static final int INFO = 0xFFA8F3BA;

    private Button triggerButton;
    private Button blueTintButton;
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
    private int triggerModeIndex;
    private boolean showBlueTint = true;
    private float miniatureScale = 1.0F;
    private float textScale = 1.0F;
    private Component statusMessage = Component.empty();
    private int statusColor = INFO;

    public ProjectorConfigScreen(ProjectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 226;
        imageHeight = 226;
        inventoryLabelX = 32;
        inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        loadProjectorState();

        int x = leftPos;
        int y = topPos;
        triggerButton = addRenderableWidget(Button.builder(
            Component.empty(),
            button -> cycleTriggerMode()).bounds(x + 104, y + 55, 102, 20).build());

        if (menu.projectorKind().requiresAnchor()) {
            offsetX = addRenderableWidget(offsetBox(x + 74, y + 80));
            offsetY = addRenderableWidget(offsetBox(x + 119, y + 80));
            offsetZ = addRenderableWidget(offsetBox(x + 164, y + 80));
            resetOffsetButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.reset_offset"),
                button -> resetOffset()).bounds(x + 15, y + 102, 62, 20).build());
            blueTintButton = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> toggleBlueTint()).bounds(x + 82, y + 102, 62, 20).build());
            playButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce()).bounds(x + 149, y + 102, 62, 20).build());
            textScaleBox = addRenderableWidget(textScaleBox(x + 140, y + 80));
        } else {
            blueTintButton = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> toggleBlueTint()).bounds(x + 15, y + 76, 88, 20).build());
            playButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce()).bounds(x + 118, y + 76, 88, 20).build());
            scaleBox = addRenderableWidget(scaleBox(x + 74, y + 102));
            textScaleBox = addRenderableWidget(textScaleBox(x + 140, y + 102));
        }

        ProjectorBlockEntity projector = menu.projector();
        if (menu.projectorKind().requiresAnchor()) {
            setOffset(projector != null
                ? projector.getEffectiveProjectionOffset()
                : ProjectorBlockEntity.defaultProjectionOffset(projectorFacing()));
        }
        updateTriggerButton();
        updateBlueTintButton();
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
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_COLOR);
        graphics.hLine(x, x + imageWidth - 1, y, PANEL_BORDER);
        graphics.hLine(x, x + imageWidth - 1, y + imageHeight - 1, PANEL_BORDER);
        graphics.vLine(x, y, y + imageHeight - 1, PANEL_BORDER);
        graphics.vLine(x + imageWidth - 1, y, y + imageHeight - 1, PANEL_BORDER);

        drawSlot(graphics, x + 103, y + 30);
        graphics.fill(x + 28, y + 136, x + 198, y + 220, 0x66000000);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, TEXT, false);
        graphics.drawString(font, Component.translatable(menu.projectorKind().translationKey()), 10, 21, MUTED, false);
        graphics.drawString(font, Component.translatable("ponderer.ui.projector.source_item"), 10, 36, TEXT, false);
        Component sceneLine = sceneSummary();
        graphics.drawString(font, sceneLine, 10, 49, resolvedSceneKeys.isEmpty() ? ERROR : INFO, false);

        graphics.drawString(font, Component.translatable("ponderer.ui.projector.trigger_mode"), 10, 61, TEXT, false);
        if (menu.projectorKind().requiresAnchor()) {
            graphics.drawString(font, Component.translatable("ponderer.ui.projector.offset"), 10, 85, TEXT, false);
            graphics.drawString(font, Component.translatable("ponderer.ui.projector.text_scale"), 138, 85, TEXT, false);
        } else {
            graphics.drawString(font, Component.translatable("ponderer.ui.projector.scale"), 10, 107, TEXT, false);
            graphics.drawString(font, Component.translatable("ponderer.ui.projector.text_scale"), 138, 107, TEXT, false);
        }

        if (statusMessage != null && !statusMessage.getString().isBlank()) {
            graphics.drawString(font, trimToWidth(statusMessage.getString(), 198), 10, 124, statusColor, false);
        }
    }

    private void loadProjectorState() {
        ProjectorBlockEntity projector = menu.projector();
        if (projector == null) {
            triggerModeIndex = ProjectorTriggerMode.MANUAL_LOOP.ordinal();
            return;
        }

        triggerModeIndex = projector.getTriggerMode().ordinal();
        showBlueTint = projector.showBlueTint();
        miniatureScale = projector.getMiniatureScale();
        textScale = projector.getTextScale();
    }

    private EditBox offsetBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, 39, 18, Component.translatable("ponderer.ui.projector.offset"));
        box.setMaxLength(10);
        box.setTextColor(0xFFFFFF);
        box.setFilter(value -> value.isEmpty() || "-".equals(value) || value.matches("-?\\d+"));
        return box;
    }

    private EditBox scaleBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, 60, 18, Component.translatable("ponderer.ui.projector.scale"));
        box.setMaxLength(6);
        box.setTextColor(0xFFFFFF);
        box.setValue(String.valueOf(miniatureScale));
        return box;
    }

    private EditBox textScaleBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, 60, 18, Component.translatable("ponderer.ui.projector.text_scale"));
        box.setMaxLength(6);
        box.setTextColor(0xFFFFFF);
        box.setValue(String.valueOf(textScale));
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

    private void cycleTriggerMode() {
        triggerModeIndex = (triggerModeIndex + 1) % ProjectorTriggerMode.values().length;
        updateTriggerButton();
    }

    private void updateTriggerButton() {
        triggerButton.setMessage(Component.translatable(currentTriggerMode().translationKey()));
    }

    private void toggleBlueTint() {
        showBlueTint = !showBlueTint;
        updateBlueTintButton();
    }

    private void updateBlueTintButton() {
        blueTintButton.setMessage(Component.translatable(showBlueTint
            ? "ponderer.ui.projector.blue_tint.on"
            : "ponderer.ui.projector.blue_tint.off"));
    }

    private ProjectorTriggerMode currentTriggerMode() {
        return ProjectorTriggerMode.values()[Math.max(0, Math.min(triggerModeIndex, ProjectorTriggerMode.values().length - 1))];
    }

    private void refreshResolvedScenes() {
        sourceFingerprint = fingerprint(menu.sourceItem());
        resolvedSceneKeys = ProjectorClientSceneResolver.sceneKeysFor(menu.sourceItem());
        playButton.active = !resolvedSceneKeys.isEmpty();

        if (menu.sourceItem().isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.insert_item"), MUTED);
            return;
        }
        if (resolvedSceneKeys.isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.no_scenes_for_item"), ERROR);
            return;
        }

        status(Component.translatable("ponderer.ui.projector.scenes_detected", resolvedSceneKeys.size()), INFO);
    }

    private boolean applyConfig(boolean showStatus) {
        BlockPos offset = parseProjectionOffset();
        if (requiresMissingOffset()) {
            status(Component.translatable("ponderer.ui.projector.offset.required"), ERROR);
            return false;
        }

        float scale = parseScale();
        float textScale = parseTextScale();
        PondererServices.NETWORK.sendToServer(new ProjectorConfigUpdatePayload(
            menu.projectorPos(),
            resolvedSceneKeys,
            currentTriggerMode(),
            offset,
            estimateDuration(resolvedSceneKeys),
            showBlueTint,
            scale,
            textScale));
        if (showStatus) {
            status(Component.translatable("ponderer.ui.projector.saved"), INFO);
        }
        return true;
    }

    private void triggerManualOnce() {
        if (resolvedSceneKeys.isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.scene.required"), ERROR);
            return;
        }
        if (!applyConfig(false)) {
            return;
        }
        PondererServices.NETWORK.sendToServer(new ProjectorManualTriggerPayload(menu.projectorPos()));
        status(Component.translatable("ponderer.ui.projector.play_once.sent"), INFO);
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
        String rawScale = scaleBox.getValue().trim();
        if (rawScale.isBlank()) {
            return 1.0F;
        }
        try {
            return Math.max(0.1F, Math.min(5.0F, Float.parseFloat(rawScale)));
        } catch (NumberFormatException ignored) {
            return 1.0F;
        }
    }

    private float parseTextScale() {
        if (textScaleBox == null) {
            return textScale;
        }
        String rawScale = textScaleBox.getValue().trim();
        if (rawScale.isBlank()) {
            return 1.0F;
        }
        try {
            return Math.max(0.1F, Math.min(10.0F, Float.parseFloat(rawScale)));
        } catch (NumberFormatException ignored) {
            return 1.0F;
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

    private Component trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return Component.literal(text);
        }
        String ellipsis = "...";
        String trimmed = text;
        while (!trimmed.isEmpty() && font.width(trimmed + ellipsis) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return Component.literal(trimmed + ellipsis);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, 0xFF0A0C0F);
        graphics.fill(x, y, x + 17, y + 17, SLOT_BG);
    }
}
