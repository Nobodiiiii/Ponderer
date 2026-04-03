package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.util.SafePaths;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Screen for exporting Ponderer packs with metadata and scene selection.
 */
public class ExportPackScreen extends AbstractSimiScreen {

    private static final int WINDOW_W = 250;
    private static final int WINDOW_H = 240;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 40;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int displayH = WINDOW_H;
    private record FormWidgetRecord(AbstractWidget widget, int contentOffsetY) {}
    private final List<FormWidgetRecord> formWidgetRecords = new ArrayList<>();

    private HintableTextFieldWidget nameField;
    private HintableTextFieldWidget versionField;
    private HintableTextFieldWidget authorField;

    private PonderButton selectScenesButton;
    private PonderButton exportButton;
    private PonderButton cancelButton;

    private Set<String> selectedSceneIds = new HashSet<>();
    private String scenesLabel = UIText.of("ponderer.ui.export.all_scenes");

    // Preserved field values across screen transitions
    private String savedName = "";
    private String savedVersion = "1.0.0";
    private String savedAuthor = "";

    public ExportPackScreen() {
        super(Component.literal("Export as Resource Pack"));
    }

    @Override
    protected void init() {
        formWidgetRecords.clear();
        displayH = Math.min(WINDOW_H, height - UILayoutConstants.SCREEN_MARGIN * 2);
        maxScroll = Math.max(0, WINDOW_H - displayH);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        setWindowSize(WINDOW_W, displayH);
        super.init();

        var font = Minecraft.getInstance().font;
        int x = guiLeft + 80;
        int fieldW = 140;

        // Package Name field (content offset 35)
        nameField = new SoftHintTextFieldWidget(font, x, guiTop + 35 - scrollOffset, fieldW, 18);
        nameField.setHint(UIText.of("ponderer.ui.export.name"));
        nameField.setMaxLength(64);
        nameField.setValue(savedName);
        addRenderableWidget(nameField);
        formWidgetRecords.add(new FormWidgetRecord(nameField, 35));

        // Version field (content offset 65)
        versionField = new SoftHintTextFieldWidget(font, x, guiTop + 65 - scrollOffset, fieldW, 18);
        versionField.setHint(UIText.of("ponderer.ui.export.version"));
        versionField.setValue(savedVersion);
        versionField.setMaxLength(32);
        addRenderableWidget(versionField);
        formWidgetRecords.add(new FormWidgetRecord(versionField, 65));

        // Author field (content offset 95)
        authorField = new SoftHintTextFieldWidget(font, x, guiTop + 95 - scrollOffset, fieldW, 18);
        authorField.setHint(UIText.of("ponderer.ui.export.author"));
        authorField.setValue(savedAuthor);
        authorField.setMaxLength(64);
        addRenderableWidget(authorField);
        formWidgetRecords.add(new FormWidgetRecord(authorField, 95));

        // Select Scenes button (content offset WINDOW_H - 70 = 170)
        int sceneBtnOffset = WINDOW_H - 70;
        selectScenesButton = new PonderButton(guiLeft + 30, guiTop + sceneBtnOffset - scrollOffset, 190, 18);
        selectScenesButton.withCallback(() -> onSelectScenesClicked());
        addRenderableWidget(selectScenesButton);
        formWidgetRecords.add(new FormWidgetRecord(selectScenesButton, sceneBtnOffset));

        // Export button (fixed at bottom)
        exportButton = new PonderButton(guiLeft + 30, guiTop + displayH - 30, 80, 18);
        exportButton.withCallback(() -> onExportClicked());
        addRenderableWidget(exportButton);

        // Cancel button (fixed at bottom)
        cancelButton = new PonderButton(guiLeft + WINDOW_W - 110, guiTop + displayH - 30, 80, 18);
        cancelButton.withCallback(() -> this.onClose());
        addRenderableWidget(cancelButton);

        updateWidgetPositions();
    }

    private void saveFieldValues() {
        if (nameField != null) savedName = nameField.getValue();
        if (versionField != null) savedVersion = versionField.getValue();
        if (authorField != null) savedAuthor = authorField.getValue();
    }

    private void onSelectScenesClicked() {
        saveFieldValues();
        Minecraft.getInstance().setScreen(new PonderItemGridScreen(
                selectedIds -> {
                    this.selectedSceneIds = selectedIds;
                    updateScenesLabel();
                    Minecraft.getInstance().setScreen(this);
                },
                () -> Minecraft.getInstance().setScreen(this),
                true));
    }

    private void updateScenesLabel() {
        if (selectedSceneIds.isEmpty()) {
            scenesLabel = UIText.of("ponderer.ui.export.all_scenes");
        } else {
            scenesLabel = UIText.of("ponderer.ui.export.selected_scenes", selectedSceneIds.size());
        }
    }

    private void onExportClicked() {
        String name = nameField.getValue().trim();
        String version = versionField.getValue().trim();
        String author = authorField.getValue().trim();

        // Validation
        if (name.isEmpty()) {
            notifyUser(UIText.of("ponderer.ui.export.name_empty"));
            return;
        }

        if (!name.matches("[a-zA-Z0-9_-]+")) {
            notifyUser(UIText.of("ponderer.ui.export.name_invalid"));
            return;
        }

        if (!SafePaths.isValidWindowsFileNameSegment(name)) {
            notifyUser(UIText.of("ponderer.ui.export.name_invalid"));
            return;
        }

        if (version.isEmpty()) {
            notifyUser(UIText.of("ponderer.ui.export.version_empty"));
            return;
        }

        // Check for existing file
        Path resourcepacksDir = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");
        String filename = "[Ponderer] " + name + ".zip";
        Path targetPath = SafePaths.resolveFileName(resourcepacksDir, filename);
        if (targetPath == null) {
            notifyUser(UIText.of("ponderer.ui.export.name_invalid"));
            return;
        }

        if (Files.exists(targetPath)) {
            // Show confirmation dialog
            showOverwriteConfirmation(name, version, author, targetPath);
            return;
        }

        // Export directly
        doExport(name, version, author);
    }

    private void showOverwriteConfirmation(String name, String version, String author, Path targetPath) {
        // For now, directly overwrite (can enhance with dialog later)
        doExport(name, version, author);
    }

    private void doExport(String name, String version, String author) {
        try {
            boolean success = selectedSceneIds.isEmpty()
                    ? SceneStore.packScenesAndStructures(name, version, author)
                    : SceneStore.packSelectedScenesAndStructures(name, version, author, selectedSceneIds);
            if (success) {
                notifyUser(UIText.of("ponderer.ui.export.success", name));
                this.onClose();
            } else {
                notifyUser(UIText.of("ponderer.ui.export.failed"));
            }
        } catch (Exception e) {
            notifyUser(UIText.of("ponderer.ui.export.failed") + ": " + e.getMessage());
        }
    }

    private void notifyUser(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
        }
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Background
        new BoxElement()
                .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
                .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true), new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(WINDOW_W, displayH)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        // Header
        graphics.drawString(font, UIText.of("ponderer.ui.export"), guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WINDOW_W - 5, guiTop + 21, UILayoutConstants.COLOR_SEPARATOR);

        // Scrollable labels
        int vpTop = guiTop + HEADER_H;
        int vpBot = guiTop + displayH - FOOTER_H;
        if (maxScroll > 0) {
            graphics.enableScissor(guiLeft, vpTop, guiLeft + WINDOW_W, vpBot);
            graphics.pose().pushPose();
            graphics.pose().translate(0, -scrollOffset, 0);
        }

        int lx = guiLeft + 10;
        int y = guiTop + 35;
        int lc = UILayoutConstants.COLOR_LABEL;

        graphics.drawString(font, UIText.of("ponderer.ui.export.name"), lx, y + 2, lc);
        graphics.drawString(font, UIText.of("ponderer.ui.export.version"), lx, y + 32, lc);
        graphics.drawString(font, UIText.of("ponderer.ui.export.author"), lx, y + 62, lc);

        if (maxScroll > 0) {
            graphics.pose().popPose();
            graphics.disableScissor();
            renderScrollbar(graphics);
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        // Select Scenes button label (scrollable widget)
        if (maxScroll > 0) {
            graphics.enableScissor(guiLeft, guiTop + HEADER_H, guiLeft + WINDOW_W, guiTop + displayH - FOOTER_H);
        }
        graphics.drawCenteredString(font, scenesLabel,
                selectScenesButton.getX() + 95, selectScenesButton.getY() + 4, 0xFFFFFF);
        if (maxScroll > 0) {
            graphics.disableScissor();
        }

        // Export / Cancel button labels (fixed)
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.export"),
                exportButton.getX() + 40, exportButton.getY() + 4, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
                cancelButton.getX() + 40, cancelButton.getY() + 4, 0xFFFFFF);

        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta * UILayoutConstants.SCROLL_SPEED));
            updateWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void updateWidgetPositions() {
        int vpTop = guiTop + HEADER_H;
        int vpBot = guiTop + displayH - FOOTER_H;
        for (FormWidgetRecord rec : formWidgetRecords) {
            int newY = guiTop + rec.contentOffsetY - scrollOffset;
            rec.widget.setY(newY);
            if (maxScroll > 0) {
                rec.widget.visible = (newY + rec.widget.getHeight() > vpTop) && (newY < vpBot);
            } else {
                rec.widget.visible = true;
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) return;
        int barX = guiLeft + WINDOW_W - UILayoutConstants.SCROLLBAR_W - 2;
        int vpTop = guiTop + HEADER_H;
        int vpBot = guiTop + displayH - FOOTER_H;
        int trackH = vpBot - vpTop;
        graphics.fill(barX, vpTop, barX + UILayoutConstants.SCROLLBAR_W, vpBot, UILayoutConstants.COLOR_SCROLLBAR_BG);
        int contentH = WINDOW_H - HEADER_H - FOOTER_H;
        if (contentH <= 0) return;
        int thumbH = Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB, trackH * trackH / contentH);
        int thumbY = vpTop + (int) ((float) scrollOffset / maxScroll * (trackH - thumbH));
        graphics.fill(barX, thumbY, barX + UILayoutConstants.SCROLLBAR_W, thumbY + thumbH, UILayoutConstants.COLOR_SCROLLBAR_FG);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new FunctionScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(keyCode, scanCode, modifiers);
        if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
            return true;
        if (getFocused() instanceof net.minecraft.client.gui.components.EditBox)
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() != null && getFocused().charTyped(codePoint, modifiers))
            return true;
        return super.charTyped(codePoint, modifiers);
    }
}
