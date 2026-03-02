package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ai.AiSceneGenerator;
import com.nododiiiii.ponderer.ai.StructureDescriber;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main AI scene generation screen.
 * Layout: structure preview -> structure controls -> carrier item -> prompt -> URLs -> generate button.
 * All user-entered state is cached statically so it survives screen re-opens.
 */
public class AiGenerateScreen extends AbstractSimiScreen implements JeiAwareScreen {

    private static final int WIDTH = 260;
    private static final int PREVIEW_H = 60;
    private static final int MARGIN = 12;
    private static final int ROW_H = 20;
    private static final int FIELD_H = 16;

    // ---- Static cache: persists across screen open/close ----
    private static final List<Path> cachedStructurePaths = new ArrayList<>();
    private static final List<StructureDescriber.StructureInfo> cachedStructureInfos = new ArrayList<>();
    private static int cachedStructureIndex = 0;
    private static String cachedCarrier = "";
    private static String cachedPrompt = "";
    private static final List<String> cachedUrlValues = new ArrayList<>();
    private static boolean cachedBuildTutorial = true;
    private static boolean cachedIncludeImages = true;
    // Adjustment mode removed for now — each generation is a fresh request
    @Nullable private static String cachedStatusMessage = null;
    private static int cachedStatusColor = 0xCCCCCC;
    private static boolean cachedGenerating = false;

    // ---- Instance fields (widgets, rebuilt on init) ----
    private HintableTextFieldWidget carrierField;
    private HintableTextFieldWidget promptField;
    private final List<HintableTextFieldWidget> urlFields = new ArrayList<>();

    // JEI
    private boolean jeiActive = false;
    @Nullable private HintableTextFieldWidget jeiTargetField = null;

    // Buttons
    private record ClickableButton(int x, int y, int w, int h, String label, Runnable action, @Nullable String tooltip) {}
    private final List<ClickableButton> clickableButtons = new ArrayList<>();

    public AiGenerateScreen() {
        super(Component.translatable("ponderer.ui.ai_generate.title"));
    }

    private static final int LABEL_H = 12;  // height reserved for a label line above a field

    private int getWindowHeight() {
        int urlCount = cachedUrlValues.size();
        return 30 + PREVIEW_H + 6 + ROW_H + 6     // title + preview + struct buttons
            + FIELD_H + 6                             // carrier (inline label, no separate label row)
            + LABEL_H + FIELD_H + 4                   // prompt label + field
            + LABEL_H                                  // "Reference URLs" label
            + urlCount * ROW_H + ROW_H + 4             // URL fields + add button
            + ROW_H + 4                                // toggle options row
            + 12 + 24 + 10;                            // status + generate button
    }

    /** Sync all widget values back to the static cache. */
    private void syncToCache() {
        if (carrierField != null) cachedCarrier = carrierField.getValue();
        if (promptField != null) cachedPrompt = promptField.getValue();
        // Sync URL fields
        for (int i = 0; i < urlFields.size() && i < cachedUrlValues.size(); i++) {
            cachedUrlValues.set(i, urlFields.get(i).getValue());
        }
    }

    @Override
    protected void init() {
        setWindowSize(WIDTH, getWindowHeight());
        super.init();
        clickableButtons.clear();
        urlFields.clear();

        var font = Minecraft.getInstance().font;
        int fieldX = guiLeft + MARGIN;
        int fieldW = WIDTH - MARGIN * 2;
        int y = guiTop + 30;

        // -- Structure preview area --
        y += PREVIEW_H + 4;

        // -- Structure control buttons: Add, Delete, <, > --
        int btnW = 40;
        int btnGap = 4;
        int totalBtnW = 4 * btnW + 3 * btnGap;
        int btnX = guiLeft + (WIDTH - totalBtnW) / 2;

        clickableButtons.add(new ClickableButton(btnX, y, btnW, 16,
            UIText.of("ponderer.ui.ai_generate.add"), this::addStructure,
            "ponderer.ui.ai_generate.add.tooltip"));
        btnX += btnW + btnGap;
        clickableButtons.add(new ClickableButton(btnX, y, btnW, 16,
            UIText.of("ponderer.ui.ai_generate.delete"), this::deleteStructure,
            "ponderer.ui.ai_generate.delete.tooltip"));
        btnX += btnW + btnGap;
        clickableButtons.add(new ClickableButton(btnX, y, btnW, 16, "<", this::prevStructure,
            "ponderer.ui.ai_generate.prev.tooltip"));
        btnX += btnW + btnGap;
        clickableButtons.add(new ClickableButton(btnX, y, btnW, 16, ">", this::nextStructure,
            "ponderer.ui.ai_generate.next.tooltip"));
        y += ROW_H + 4;

        // -- Carrier item (label inline with field) --
        int carrierLabelW = font.width(UIText.of("ponderer.ui.ai_generate.carrier")) + 6;
        int carrierFieldW = fieldW - carrierLabelW;
        boolean hasJei = JeiCompat.isAvailable();
        if (hasJei) carrierFieldW -= 20;

        carrierField = new SoftHintTextFieldWidget(font, fieldX + carrierLabelW, y, carrierFieldW, FIELD_H);
        carrierField.setHint(UIText.of("ponderer.ui.ai_generate.carrier.hint"));
        carrierField.setMaxLength(128);
        carrierField.setValue(cachedCarrier);
        addRenderableWidget(carrierField);

        if (hasJei) {
            PonderButton jeiBtn = new PonderButton(fieldX + carrierLabelW + carrierFieldW + 4, y + 2, 14, 12);
            jeiBtn.withCallback(() -> {
                if (jeiActive && jeiTargetField == carrierField) {
                    deactivateJei();
                } else {
                    jeiActive = true;
                    jeiTargetField = carrierField;
                    JeiCompat.setActiveScreen(this, IdFieldMode.ITEM);
                }
            });
            addRenderableWidget(jeiBtn);
        }
        y += FIELD_H + 6;

        // -- Prompt --
        y += LABEL_H;  // space for label
        promptField = new SoftHintTextFieldWidget(font, fieldX, y, fieldW, FIELD_H);
        promptField.setHint(UIText.of("ponderer.ui.ai_generate.prompt.hint"));
        promptField.setMaxLength(2048);
        promptField.setValue(cachedPrompt);
        addRenderableWidget(promptField);
        y += FIELD_H + 4;

        // -- Reference URLs (starts empty, fully optional) --
        y += LABEL_H;  // space for "Reference URLs" label
        for (int i = 0; i < cachedUrlValues.size(); i++) {
            int urlFieldW = fieldW - 20;
            HintableTextFieldWidget urlField = new SoftHintTextFieldWidget(font, fieldX, y, urlFieldW, FIELD_H);
            urlField.setHint(UIText.of("ponderer.ui.ai_generate.url.hint"));
            urlField.setMaxLength(512);
            urlField.setValue(cachedUrlValues.get(i));
            addRenderableWidget(urlField);
            urlFields.add(urlField);

            // Remove button
            int rmBtnX = fieldX + urlFieldW + 4;
            final int idx = i;
            PonderButton rmBtn = new PonderButton(rmBtnX, y + 2, 14, 12);
            rmBtn.withCallback(() -> removeUrl(idx));
            addRenderableWidget(rmBtn);
            y += ROW_H;
        }

        // Add URL button
        clickableButtons.add(new ClickableButton(fieldX + 3, y + 1, fieldW - 6, 14,
            UIText.of("ponderer.ui.ai_generate.add_url"), this::addUrl,
            "ponderer.ui.ai_generate.add_url.tooltip"));
        y += ROW_H + 4;

        // -- Toggle options: Build Tutorial | Include Images --
        int toggleW = (fieldW - 6) / 2;
        clickableButtons.add(new ClickableButton(fieldX, y, toggleW, 16,
            UIText.of("ponderer.ui.ai_generate.build_tutorial") + ": " + (cachedBuildTutorial ? "ON" : "OFF"),
            this::toggleBuildTutorial,
            "ponderer.ui.ai_generate.build_tutorial.tooltip"));
        clickableButtons.add(new ClickableButton(fieldX + toggleW + 6, y, toggleW, 16,
            UIText.of("ponderer.ui.ai_generate.include_images") + ": " + (cachedIncludeImages ? "ON" : "OFF"),
            this::toggleIncludeImages,
            "ponderer.ui.ai_generate.include_images.tooltip"));
        y += ROW_H + 4;

        // -- Status message area --
        y += 12;

        // -- Generate / Back buttons --
        int genBtnW = 80;
        clickableButtons.add(new ClickableButton(guiLeft + MARGIN, y, genBtnW, 20,
            cachedGenerating ? UIText.of("ponderer.ui.ai_generate.generating") : UIText.of("ponderer.ui.ai_generate.generate"),
            this::doGenerate,
            "ponderer.ui.ai_generate.generate.tooltip"));
        clickableButtons.add(new ClickableButton(guiLeft + WIDTH - MARGIN - 60, y, 60, 20,
            UIText.of("ponderer.ui.function_page.back"), this::goBack, null));

        // Focus prompt field
        promptField.setFocused(true);
        setFocused(promptField);
    }

    // -- Structure management --

    private void addStructure() {
        syncToCache();
        Path structuresDir = SceneStore.getStructureDir();
        CompletableFuture.supplyAsync(() -> {
            try {
                String defaultPath = Files.exists(structuresDir)
                    ? structuresDir.toAbsolutePath().toString() + java.io.File.separator
                    : null;
                MemoryStack stack = MemoryStack.stackPush();
                try {
                    PointerBuffer filters = stack.mallocPointer(1);
                    filters.put(stack.UTF8("*.nbt"));
                    filters.flip();
                    return TinyFileDialogs.tinyfd_openFileDialog(
                        UIText.of("ponderer.ui.ai_generate.select_nbt"),
                        defaultPath, filters, "NBT files (*.nbt)", false);
                } finally {
                    stack.pop();
                }
            } catch (Exception e) {
                return null;
            }
        }).thenAcceptAsync(result -> {
            if (result == null) return;
            Path selected = Path.of(result);

            // Copy to structures dir if external
            Path target;
            if (selected.startsWith(structuresDir)) {
                target = selected;
            } else {
                String fileName = selected.getFileName().toString();
                target = structuresDir.resolve(fileName);
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(selected, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    cachedStatusMessage = "Failed to copy: " + e.getMessage();
                    cachedStatusColor = 0xFF6666;
                    return;
                }
            }

            try {
                StructureDescriber.StructureInfo info = StructureDescriber.describe(target);
                cachedStructurePaths.add(target);
                cachedStructureInfos.add(info);
                cachedStructureIndex = cachedStructurePaths.size() - 1;
                cachedStatusMessage = null;
                init(minecraft, width, height);
            } catch (Exception e) {
                cachedStatusMessage = "Failed to parse NBT: " + e.getMessage();
                cachedStatusColor = 0xFF6666;
            }
        }, Minecraft.getInstance());
    }

    private void deleteStructure() {
        if (cachedStructurePaths.isEmpty()) return;
        syncToCache();
        cachedStructurePaths.remove(cachedStructureIndex);
        cachedStructureInfos.remove(cachedStructureIndex);
        if (cachedStructureIndex >= cachedStructurePaths.size()) {
            cachedStructureIndex = Math.max(0, cachedStructurePaths.size() - 1);
        }
        init(minecraft, width, height);
    }

    private void prevStructure() {
        if (cachedStructurePaths.size() > 1) {
            cachedStructureIndex = (cachedStructureIndex - 1 + cachedStructurePaths.size()) % cachedStructurePaths.size();
        }
    }

    private void nextStructure() {
        if (cachedStructurePaths.size() > 1) {
            cachedStructureIndex = (cachedStructureIndex + 1) % cachedStructurePaths.size();
        }
    }

    // -- URL management --

    private void addUrl() {
        syncToCache();
        cachedUrlValues.add("");
        init(minecraft, width, height);
    }

    private void removeUrl(int index) {
        syncToCache();
        if (index >= 0 && index < cachedUrlValues.size()) {
            cachedUrlValues.remove(index);
        }
        init(minecraft, width, height);
    }

    // -- Toggle options --

    private void toggleBuildTutorial() {
        syncToCache();
        cachedBuildTutorial = !cachedBuildTutorial;
        init(minecraft, width, height);
    }

    private void toggleIncludeImages() {
        syncToCache();
        cachedIncludeImages = !cachedIncludeImages;
        init(minecraft, width, height);
    }

    // -- Generation --

    private void doGenerate() {
        if (cachedGenerating) return;
        syncToCache();

        if (cachedStructurePaths.isEmpty()) {
            cachedStatusMessage = null; // structures are optional, use built-in basic
        }
        String carrier = cachedCarrier.trim();
        if (carrier.isEmpty()) {
            cachedStatusMessage = UIText.of("ponderer.ui.ai_generate.error.no_carrier");
            cachedStatusColor = 0xFF6666;
            return;
        }
        String prompt = cachedPrompt.trim();
        if (prompt.isEmpty()) {
            cachedStatusMessage = UIText.of("ponderer.ui.ai_generate.error.no_prompt");
            cachedStatusColor = 0xFF6666;
            return;
        }

        List<String> urls = new ArrayList<>();
        for (String u : cachedUrlValues) {
            if (u != null && !u.isBlank()) urls.add(u.trim());
        }

        cachedGenerating = true;
        cachedStatusMessage = UIText.of("ponderer.ui.ai_generate.status.generating");
        cachedStatusColor = 0xAAAAFF;
        init(minecraft, width, height);

        AiSceneGenerator.generate(
            new ArrayList<>(cachedStructurePaths), carrier, prompt, urls, null,
            cachedBuildTutorial, cachedIncludeImages,
            filePath -> {
                cachedGenerating = false;
                cachedStatusMessage = UIText.of("ponderer.ui.ai_generate.status.success");
                cachedStatusColor = 0x55FF55;
                init(minecraft, width, height);
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("ponderer.ui.ai_generate.status.success"), false);
                }
            },
            error -> {
                cachedGenerating = false;
                cachedStatusMessage = error;
                cachedStatusColor = 0xFF6666;
                init(minecraft, width, height);
            },
            statusMsg -> {
                cachedStatusMessage = statusMsg;
                cachedStatusColor = 0xAAAAFF;
                init(minecraft, width, height);
            }
        );
    }

    private void goBack() {
        syncToCache();
        if (jeiActive) deactivateJei();
        Minecraft.getInstance().setScreen(new FunctionScreen());
    }

    // -- Rendering --

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int wH = getWindowHeight();
        new BoxElement()
            .withBackground(new Color(0xdd_000000, true))
            .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(WIDTH, wH)
            .render(graphics);

        var font = Minecraft.getInstance().font;

        // Title
        graphics.drawCenteredString(font, this.title, guiLeft + WIDTH / 2, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WIDTH - 5, guiTop + 21, 0x60_FFFFFF);

        int y = guiTop + 30;

        // -- Structure preview box --
        int previewX = guiLeft + MARGIN;
        int previewW = WIDTH - MARGIN * 2;
        graphics.fill(previewX, y, previewX + previewW, y + PREVIEW_H, 0x40_222244);
        graphics.fill(previewX, y, previewX + previewW, y + 1, 0x60_555588);
        graphics.fill(previewX, y + PREVIEW_H - 1, previewX + previewW, y + PREVIEW_H, 0x60_555588);
        graphics.fill(previewX, y, previewX + 1, y + PREVIEW_H, 0x60_555588);
        graphics.fill(previewX + previewW - 1, y, previewX + previewW, y + PREVIEW_H, 0x60_555588);

        if (cachedStructurePaths.isEmpty()) {
            graphics.drawCenteredString(font, UIText.of("ponderer.ui.ai_generate.no_structure"),
                guiLeft + WIDTH / 2, y + PREVIEW_H / 2 - 4, 0x666666);
        } else {
            StructureDescriber.StructureInfo info = cachedStructureInfos.get(cachedStructureIndex);
            String fileName = cachedStructurePaths.get(cachedStructureIndex).getFileName().toString();
            if (fileName.endsWith(".nbt")) fileName = fileName.substring(0, fileName.length() - 4);

            // Structure name and index
            String header = (cachedStructureIndex + 1) + "/" + cachedStructurePaths.size() + " - " + fileName;
            graphics.drawCenteredString(font, header, guiLeft + WIDTH / 2, y + 6, 0xFFFFFF);

            // Size
            String size = info.sizeX() + " x " + info.sizeY() + " x " + info.sizeZ();
            graphics.drawCenteredString(font, size, guiLeft + WIDTH / 2, y + 20, 0xAAAAFF);

            // Block types (truncated)
            List<String> types = info.blockTypes();
            String typesStr = String.join(", ", types);
            if (font.width(typesStr) > previewW - 10) {
                typesStr = font.plainSubstrByWidth(typesStr, previewW - 20) + "...";
            }
            graphics.drawString(font, typesStr, previewX + 5, y + 34, 0x888888);

            // Block count hint
            String countHint = types.size() + " block types";
            graphics.drawString(font, countHint, previewX + 5, y + PREVIEW_H - 14, 0x666666);
        }
        y += PREVIEW_H + 4;

        // -- Structure buttons (rendered via clickableButtons) --
        y += ROW_H + 4;

        // -- Carrier label (inline with field) --
        graphics.drawString(font, UIText.of("ponderer.ui.ai_generate.carrier"),
            guiLeft + MARGIN, y + 4, 0xCCCCCC);
        // carrier field is rendered by widget at this y (offset by carrierLabelW)
        y += FIELD_H + 6;

        // -- Prompt label --
        graphics.drawString(font, UIText.of("ponderer.ui.ai_generate.prompt"),
            guiLeft + MARGIN, y, 0xCCCCCC);
        y += LABEL_H;  // label height
        // prompt field is rendered by widget at this y
        y += FIELD_H + 4;

        // -- URL label --
        graphics.drawString(font, UIText.of("ponderer.ui.ai_generate.urls"),
            guiLeft + MARGIN, y, 0xCCCCCC);
        y += LABEL_H;  // label height

        y += cachedUrlValues.size() * ROW_H + ROW_H + 4;

        // -- Toggle options (rendered via clickableButtons) --
        y += ROW_H + 4;

        // -- Status --
        if (cachedStatusMessage != null) {
            String msg = cachedStatusMessage;
            if (font.width(msg) > WIDTH - MARGIN * 2) {
                msg = font.plainSubstrByWidth(msg, WIDTH - MARGIN * 2 - 10) + "...";
            }
            graphics.drawCenteredString(font, msg, guiLeft + WIDTH / 2, y, cachedStatusColor);
        }
        y += 12;

        // -- Buttons --
        for (ClickableButton btn : clickableButtons) {
            boolean hovered = mouseX >= btn.x && mouseX < btn.x + btn.w
                && mouseY >= btn.y && mouseY < btn.y + btn.h;
            boolean isGenerating = cachedGenerating && btn.label.equals(UIText.of("ponderer.ui.ai_generate.generating"));
            int bgColor = isGenerating ? 0x40_333355 : (hovered ? 0x80_4466aa : 0x60_333366);
            int borderColor = hovered ? 0xCC_6688cc : 0x60_555588;
            graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, bgColor);
            graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + 1, borderColor);
            graphics.fill(btn.x, btn.y + btn.h - 1, btn.x + btn.w, btn.y + btn.h, borderColor);
            graphics.fill(btn.x, btn.y, btn.x + 1, btn.y + btn.h, borderColor);
            graphics.fill(btn.x + btn.w - 1, btn.y, btn.x + btn.w, btn.y + btn.h, borderColor);
            int textX = btn.x + (btn.w - font.width(btn.label)) / 2;
            int textY = btn.y + (btn.h - font.lineHeight) / 2 + 1;
            graphics.drawString(font, btn.label, textX, textY,
                isGenerating ? 0x888888 : (hovered ? 0xFFFFFF : 0xCCCCCC));
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        var font = Minecraft.getInstance().font;

        // JEI button label
        if (JeiCompat.isAvailable()) {
            // Find the JEI PonderButton and render its label
            for (var child : children()) {
                if (child instanceof PonderButton pb && pb.getWidth() == 14 && pb.getHeight() == 12) {
                    int pbx = pb.getX();
                    int pby = pb.getY();
                    // Check if it's the JEI button (near carrier field)
                    if (pby >= guiTop + 30 + PREVIEW_H && pby < guiTop + 30 + PREVIEW_H + ROW_H + 30) {
                        int color = (jeiActive && jeiTargetField == carrierField) ? 0x55FF55 : 0xAAAAFF;
                        graphics.drawCenteredString(font, "J", pbx + 7, pby + 2, color);
                    } else {
                        // URL remove button
                        graphics.drawCenteredString(font, "-", pbx + 7, pby + 2, 0xFF6666);
                    }
                }
            }
        } else {
            // Just render "-" on URL remove buttons
            for (var child : children()) {
                if (child instanceof PonderButton pb && pb.getWidth() == 14 && pb.getHeight() == 12) {
                    graphics.drawCenteredString(font, "-", pb.getX() + 7, pb.getY() + 2, 0xFF6666);
                }
            }
        }

        // Button tooltips
        for (ClickableButton btn : clickableButtons) {
            if (btn.tooltip != null && mouseX >= btn.x && mouseX < btn.x + btn.w
                && mouseY >= btn.y && mouseY < btn.y + btn.h) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 100);
                graphics.renderComponentTooltip(font,
                    List.of(Component.translatable(btn.tooltip)),
                    mouseX, mouseY);
                graphics.pose().popPose();
                break;
            }
        }

        graphics.pose().popPose();
    }

    // -- Input handling --

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickableButton btn : clickableButtons) {
                if (mouseX >= btn.x && mouseX < btn.x + btn.w
                    && mouseY >= btn.y && mouseY < btn.y + btn.h) {
                    btn.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    @Override
    public void onClose() {
        goBack();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // -- JeiAwareScreen --

    @Override @Nullable
    public HintableTextFieldWidget getJeiTargetField() { return jeiTargetField; }

    @Override
    public void deactivateJei() {
        jeiActive = false;
        jeiTargetField = null;
        JeiCompat.clearActiveEditor();
    }

    @Override
    public void showJeiIncompatibleWarning(IdFieldMode mode) {
        cachedStatusMessage = UIText.of("ponderer.ui.jei.error.not_block");
        cachedStatusColor = 0xFF6666;
    }

    @Override public int getGuiLeft() { return guiLeft; }
    @Override public int getGuiTop() { return guiTop; }
    @Override public int getGuiWidth() { return WIDTH; }
    @Override public int getGuiHeight() { return getWindowHeight(); }
}
