package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Screen for exporting Ponderer packs with metadata and scene selection.
 */
public class ExportPackScreen extends AbstractSimiScreen {

    private static final int WINDOW_W = 250;
    private static final int WINDOW_H = 240;

    private HintableTextFieldWidget nameField;
    private HintableTextFieldWidget versionField;
    private HintableTextFieldWidget authorField;

    private PonderButton selectScenesButton;
    private PonderButton exportButton;
    private PonderButton cancelButton;

    private Set<String> selectedSceneIds = new HashSet<>();
    private String scenesLabel = UIText.of("ponderer.ui.export.all_scenes");

    public ExportPackScreen() {
        super(Component.literal("Export Ponderer Pack"));
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, WINDOW_H);
        super.init();

        var font = Minecraft.getInstance().font;
        int x = guiLeft + 80;
        int y = guiTop + 35;
        int fieldW = 140;

        // Package Name field
        nameField = new SoftHintTextFieldWidget(font, x, y, fieldW, 18);
        nameField.setHint(UIText.of("ponderer.ui.export.name"));
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);

        // Version field
        versionField = new SoftHintTextFieldWidget(font, x, y + 30, fieldW, 18);
        versionField.setHint(UIText.of("ponderer.ui.export.version"));
        versionField.setValue("1.0.0");
        versionField.setMaxLength(32);
        addRenderableWidget(versionField);

        // Author field
        authorField = new SoftHintTextFieldWidget(font, x, y + 60, fieldW, 18);
        authorField.setHint(UIText.of("ponderer.ui.export.author"));
        authorField.setMaxLength(64);
        addRenderableWidget(authorField);

        // Select Scenes button
        selectScenesButton = new PonderButton(guiLeft + 30, guiTop + WINDOW_H - 70, 190, 18);
        selectScenesButton.withCallback(() -> onSelectScenesClicked());
        addRenderableWidget(selectScenesButton);

        // Export button
        exportButton = new PonderButton(guiLeft + 30, guiTop + WINDOW_H - 30, 80, 18);
        exportButton.withCallback(() -> onExportClicked());
        addRenderableWidget(exportButton);

        // Cancel button
        cancelButton = new PonderButton(guiLeft + WINDOW_W - 110, guiTop + WINDOW_H - 30, 80, 18);
        cancelButton.withCallback(() -> this.onClose());
        addRenderableWidget(cancelButton);
    }

    private void onSelectScenesClicked() {
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

        if (version.isEmpty()) {
            notifyUser(UIText.of("ponderer.ui.export.version_empty"));
            return;
        }

        // Check for existing file
        Path resourcepacksDir = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");
        String filename = "[Ponderer] " + name + ".zip";
        Path targetPath = resourcepacksDir.resolve(filename);

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
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(WINDOW_W, WINDOW_H)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        // Header
        graphics.drawString(font, UIText.of("ponderer.ui.export"), guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WINDOW_W - 5, guiTop + 21, 0x60_FFFFFF);

        int lx = guiLeft + 10;
        int y = guiTop + 35;
        int lc = 0xCCCCCC;

        graphics.drawString(font, UIText.of("ponderer.ui.export.name"), lx, y + 2, lc);
        graphics.drawString(font, UIText.of("ponderer.ui.export.version"), lx, y + 32, lc);
        graphics.drawString(font, UIText.of("ponderer.ui.export.author"), lx, y + 62, lc);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        // Select Scenes button label
        graphics.drawCenteredString(font, scenesLabel,
                selectScenesButton.getX() + 95, selectScenesButton.getY() + 4, 0xFFFFFF);

        // Export / Cancel button labels
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.export"),
                exportButton.getX() + 40, exportButton.getY() + 4, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
                cancelButton.getX() + 40, cancelButton.getY() + 4, 0xFFFFFF);

        graphics.pose().popPose();
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
