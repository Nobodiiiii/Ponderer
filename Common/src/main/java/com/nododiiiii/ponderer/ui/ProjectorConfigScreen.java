package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.network.ProjectorConfigUpdatePayload;
import com.nododiiiii.ponderer.network.ProjectorManualTriggerPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorKind;
import com.nododiiiii.ponderer.projector.ProjectorMenu;
import com.nododiiiii.ponderer.projector.ProjectorSceneResolver;
import com.nododiiiii.ponderer.projector.ProjectorTriggerMode;
import com.nododiiiii.ponderer.projector.client.ProjectorSceneCompiler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
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
    private Button applyButton;
    private Button playButton;
    @Nullable
    private Button clearAnchorButton;
    @Nullable
    private EditBox anchorX;
    @Nullable
    private EditBox anchorY;
    @Nullable
    private EditBox anchorZ;

    private List<String> resolvedSceneKeys = List.of();
    private String sourceFingerprint = "";
    private int triggerModeIndex;
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
            anchorX = addRenderableWidget(anchorBox(x + 74, y + 80));
            anchorY = addRenderableWidget(anchorBox(x + 119, y + 80));
            anchorZ = addRenderableWidget(anchorBox(x + 164, y + 80));
            clearAnchorButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.clear_anchor"),
                button -> clearAnchor()).bounds(x + 15, y + 102, 62, 20).build());
            applyButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.apply"),
                button -> applyConfig(true)).bounds(x + 82, y + 102, 62, 20).build());
            playButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce()).bounds(x + 149, y + 102, 62, 20).build());
        } else {
            applyButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.apply"),
                button -> applyConfig(true)).bounds(x + 15, y + 76, 88, 20).build());
            playButton = addRenderableWidget(Button.builder(
                Component.translatable("ponderer.ui.projector.play_once"),
                button -> triggerManualOnce()).bounds(x + 118, y + 76, 88, 20).build());
        }

        ProjectorBlockEntity projector = menu.projector();
        if (projector != null && projector.getAnchorPos() != null) {
            setAnchor(projector.getAnchorPos());
        }
        updateTriggerButton();
        refreshResolvedScenes(true);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        tickBox(anchorX);
        tickBox(anchorY);
        tickBox(anchorZ);

        String current = fingerprint(menu.sourceItem());
        if (!current.equals(sourceFingerprint)) {
            refreshResolvedScenes(true);
        }
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
            graphics.drawString(font, Component.translatable("ponderer.ui.projector.anchor"), 10, 85, TEXT, false);
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
    }

    private EditBox anchorBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, 39, 18, Component.translatable("ponderer.ui.projector.anchor"));
        box.setMaxLength(10);
        box.setTextColor(0xFFFFFF);
        return box;
    }

    private void setAnchor(BlockPos pos) {
        if (anchorX != null) {
            anchorX.setValue(String.valueOf(pos.getX()));
        }
        if (anchorY != null) {
            anchorY.setValue(String.valueOf(pos.getY()));
        }
        if (anchorZ != null) {
            anchorZ.setValue(String.valueOf(pos.getZ()));
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

    private ProjectorTriggerMode currentTriggerMode() {
        return ProjectorTriggerMode.values()[Math.max(0, Math.min(triggerModeIndex, ProjectorTriggerMode.values().length - 1))];
    }

    private void refreshResolvedScenes(boolean autoApply) {
        sourceFingerprint = fingerprint(menu.sourceItem());
        resolvedSceneKeys = ProjectorSceneResolver.sceneKeysFor(menu.sourceItem());
        playButton.active = !resolvedSceneKeys.isEmpty();

        if (menu.sourceItem().isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.insert_item"), MUTED);
            applyConfig(false);
            return;
        }
        if (resolvedSceneKeys.isEmpty()) {
            status(Component.translatable("ponderer.ui.projector.no_scenes_for_item"), ERROR);
            applyConfig(false);
            return;
        }

        status(Component.translatable("ponderer.ui.projector.scenes_detected", resolvedSceneKeys.size()), INFO);
        if (autoApply && !requiresMissingAnchor()) {
            applyConfig(false);
        }
    }

    private boolean applyConfig(boolean showStatus) {
        BlockPos anchor = parseAnchorPos();
        if (requiresMissingAnchor()) {
            status(Component.translatable("ponderer.ui.projector.anchor.required"), ERROR);
            return false;
        }

        PondererServices.NETWORK.sendToServer(new ProjectorConfigUpdatePayload(
            menu.projectorPos(),
            resolvedSceneKeys,
            currentTriggerMode(),
            anchor,
            estimateDuration(resolvedSceneKeys)));
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

    private boolean requiresMissingAnchor() {
        return menu.projectorKind().requiresAnchor() && !resolvedSceneKeys.isEmpty() && parseAnchorPos() == null;
    }

    @Nullable
    private BlockPos parseAnchorPos() {
        if (anchorX == null || anchorY == null || anchorZ == null) {
            return null;
        }
        String rawX = anchorX.getValue().trim();
        String rawY = anchorY.getValue().trim();
        String rawZ = anchorZ.getValue().trim();
        if (rawX.isBlank() && rawY.isBlank() && rawZ.isBlank()) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(rawX), Integer.parseInt(rawY), Integer.parseInt(rawZ));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void clearAnchor() {
        if (anchorX != null) {
            anchorX.setValue("");
        }
        if (anchorY != null) {
            anchorY.setValue("");
        }
        if (anchorZ != null) {
            anchorZ.setValue("");
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
