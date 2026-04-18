package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PonderPackInfo;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ReadonlyPackImportPromptScreen extends AbstractSimiScreen {

    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 8;
    private static final int PADDING = 16;
    private static final int LINE_SPACING = 2;
    private static final int MAX_TEXT_WIDTH = 280;
    private static final int MIN_BUTTON_WIDTH = 120;

    private final Screen source;
    private final String packId;
    private final String sceneKey;
    private final int sceneIndex;

    private final List<FormattedCharSequence> textLines = new ArrayList<>();
    private BoxWidget importButton;
    private BoxWidget cancelButton;
    private int titleLineCount;
    private int dialogX;
    private int dialogY;
    private int dialogWidth;
    private int dialogHeight;
    private int textX;
    private int textY;
    private int importButtonWidth;
    private int cancelButtonWidth;

    public ReadonlyPackImportPromptScreen(@Nonnull Screen source, String packId, String sceneKey, int sceneIndex) {
        this.source = source;
        this.packId = packId;
        this.sceneKey = sceneKey;
        this.sceneIndex = sceneIndex;
    }

    public void open() {
        Minecraft client = Minecraft.getInstance();
        this.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        client.screen = this;
    }

    @Override
    protected void init() {
        super.init();

        textLines.clear();
        textLines.add(Component.translatable("ponderer.ui.readonly_import_prompt.title").getVisualOrderText());
        titleLineCount = textLines.size();
        textLines.addAll(font.split(
            Component.translatable("ponderer.ui.readonly_import_prompt.message"),
            Math.min(MAX_TEXT_WIDTH, Math.max(120, width - PADDING * 2 - 40))));

        Component importLabel = Component.translatable(
            "ponderer.ui.readonly_import_prompt.import",
            "[" + packId + "]");
        Component cancelLabel = Component.translatable("ponderer.ui.cancel");

        importButtonWidth = Math.max(MIN_BUTTON_WIDTH, font.width(importLabel) + 18);
        cancelButtonWidth = Math.max(MIN_BUTTON_WIDTH, font.width(cancelLabel) + 18);

        int lineHeight = font.lineHeight + LINE_SPACING;
        int textHeight = Math.max(font.lineHeight, textLines.size() * lineHeight - LINE_SPACING);
        int totalButtonWidth = importButtonWidth + cancelButtonWidth + BUTTON_GAP;
        int textWidth = textLines.stream()
            .mapToInt(font::width)
            .max()
            .orElse(totalButtonWidth);

        dialogWidth = Math.min(width - 40, Math.max(textWidth + PADDING * 2, totalButtonWidth + PADDING * 2));
        dialogHeight = PADDING * 2 + textHeight + 14 + BUTTON_HEIGHT;
        dialogX = (width - dialogWidth) / 2;
        dialogY = (height - dialogHeight) / 2;
        textX = dialogX + PADDING;
        textY = dialogY + PADDING;

        int buttonX = dialogX + (dialogWidth - totalButtonWidth) / 2;
        int buttonY = dialogY + dialogHeight - PADDING - BUTTON_HEIGHT;

        importButton = createButton(buttonX, buttonY, importButtonWidth, this::importAndEdit);
        addRenderableWidget(importButton);

        cancelButton = createButton(buttonX + importButtonWidth + BUTTON_GAP, buttonY, cancelButtonWidth, this::closeToSource);
        addRenderableWidget(cancelButton);
    }

    @Override
    public void tick() {
        super.tick();
        source.tick();
    }

    @Override
    public void onClose() {
        closeToSource();
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        source.render(graphics, 0, 0, 10);
        graphics.fillGradient(0, 0, width, height, 0xE8101010, 0xF0101010);
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xF0202020);
        graphics.renderOutline(dialogX, dialogY, dialogWidth, dialogHeight, 0xFF6A6A6A);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        int y = textY;
        int lineHeight = font.lineHeight + LINE_SPACING;
        for (int i = 0; i < textLines.size(); i++) {
            FormattedCharSequence line = textLines.get(i);
            int color = i < titleLineCount ? 0xFFF0D080 : 0xFFEAEAEA;
            graphics.drawString(font, line, textX, y, color, false);
            y += lineHeight;
        }

        poseStack.popPose();
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderButtonLabel(graphics, importButton,
            Component.translatable("ponderer.ui.readonly_import_prompt.import", "[" + packId + "]"));
        renderButtonLabel(graphics, cancelButton, Component.translatable("ponderer.ui.cancel"));
    }

    @Override
    public void resize(@Nonnull Minecraft client, int width, int height) {
        super.resize(client, width, height);
        source.resize(client, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void importAndEdit() {
        PonderPackInfo info = findSourcePack(packId);
        if (info == null || info.sourcePath == null) {
            closeToSource();
            notifyUser(Component.literal(UIText.of(
                "ponderer.ui.import.failed",
                UIText.of("ponderer.ui.readonly_import_prompt.missing_source", packId))));
            return;
        }

        SceneStore.PackImportResult result = SceneStore.importPackFromResourcePack(info.sourcePath);
        if (!result.isSuccess()) {
            closeToSource();
            String key = result.uiMessageKey();
            notifyUser(key == null || key.isBlank()
                ? Component.literal(result.englishMessage())
                : Component.literal(UIText.of(key, result.uiMessageArgs())));
            return;
        }

        SceneStore.autoLoadPonderPacks();
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);

        DslScene importedScene = SceneRuntime.findByKey(sceneKey);
        notifyUser(Component.literal(UIText.of(result.uiMessageKey(), result.uiMessageArgs())));
        if (importedScene == null) {
            closeToSource();
            return;
        }

        SceneEditorScreen.markUiToEditorTransition(importedScene);
        Minecraft.getInstance().setScreen(new SceneEditorScreen(importedScene, sceneIndex));
    }

    @Nullable
    private static PonderPackInfo findSourcePack(String packId) {
        for (PonderPackInfo info : SceneStore.scanAvailableSourcePacks()) {
            if (packId.equals(info.name)) {
                return info;
            }
        }
        return null;
    }

    private void closeToSource() {
        ScreenOpener.open(source);
    }

    private void notifyUser(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }

    private BoxWidget createButton(int x, int y, int width, Runnable callback) {
        return new BoxWidget(x, y, width, BUTTON_HEIGHT).withCallback(callback);
    }

    private void renderButtonLabel(GuiGraphics graphics, @Nullable BoxWidget button, Component label) {
        if (button == null) {
            return;
        }
        int x = button.getX() + button.getWidth() / 2;
        int y = button.getY() + (button.getHeight() - font.lineHeight) / 2 + 1;
        graphics.drawCenteredString(font, label, x, y, 0xFFEAEAEA);
    }
}
