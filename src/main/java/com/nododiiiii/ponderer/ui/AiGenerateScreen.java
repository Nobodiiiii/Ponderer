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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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

    private static final int WIDTH = UILayoutConstants.WIDE_WINDOW_W;
    private static final int PREVIEW_H = 60;
    private static final int MARGIN = 12;
    private static final int ROW_H = 20;
    private static final int FIELD_H = 16;

    // ─── Scroll state ────────────────────────────────────────────────────
    private static final int FORM_TOP_Y = 26;
    private static final int BOTTOM_H = 34;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private record FormWidgetRecord(AbstractWidget widget, int contentOffsetY) {}
    private final List<FormWidgetRecord> formWidgetRecords = new ArrayList<>();

    // ---- Static cache: persists across screen open/close ----
    private static final List<Path> cachedStructurePaths = new ArrayList<>();
    private static final List<StructureDescriber.StructureInfo> cachedStructureInfos = new ArrayList<>();
    private static int cachedStructureIndex = 0;
    private static String cachedCarrier = "";
    private static String cachedPrompt = "";
    private static final ReferenceUrlManager referenceUrlManager = new ReferenceUrlManager();
    private static boolean cachedBuildTutorial = false;
    private static boolean cachedIncludeImages = false;
    // Adjustment mode removed for now — each generation is a fresh request
    @Nullable private static String cachedStatusMessage = null;
    private static int cachedStatusColor = UILayoutConstants.COLOR_LABEL;
    private static boolean cachedGenerating = false;

    // ---- Instance fields (widgets, rebuilt on init) ----
    private HintableTextFieldWidget carrierField;
    private HintableTextFieldWidget promptField;
    private final List<HintableTextFieldWidget> urlFields = new ArrayList<>();

    // JEI
    private boolean jeiActive = false;
    @Nullable private HintableTextFieldWidget jeiTargetField = null;

    // Buttons
    private record ClickableButton(int x, int y, int w, int h, String label, Runnable action, @Nullable String tooltip, boolean scrollable) {}
    private final List<ClickableButton> clickableButtons = new ArrayList<>();

    public AiGenerateScreen() {
        super(Component.translatable("ponderer.ui.ai_generate.title"));
    }

    private static final int LABEL_H = 12;  // height reserved for a label line above a field

    /** Raw content height (form area only, excluding title and bottom). */
    private int getContentHeight() {
        int urlCount = referenceUrlManager.getUrlValues().size();
        return 4 + PREVIEW_H + 6 + ROW_H + 6        // preview + struct buttons
            + FIELD_H + 6                             // carrier
            + LABEL_H + FIELD_H + 4                   // prompt label + field
            + LABEL_H                                  // "Reference URLs" label
            + urlCount * ROW_H + ROW_H + 4             // URL fields + add button
            + ROW_H + 4                                // toggle options row
            + 12;                                       // status
    }

    private int getWindowHeight() {
        int raw = FORM_TOP_Y + getContentHeight() + BOTTOM_H;
        int maxH = height - UILayoutConstants.SCREEN_MARGIN * 2;
        return Math.min(raw, maxH);
    }

    private boolean isScrollEnabled() { return maxScroll > 0; }
    private int viewportTop() { return guiTop + FORM_TOP_Y; }
    private int viewportBottom() { return guiTop + getWindowHeight() - BOTTOM_H; }

    private void updateScrollPositions() {
        int vpTop = viewportTop();
        int vpBot = viewportBottom();
        for (FormWidgetRecord rec : formWidgetRecords) {
            int newY = vpTop + rec.contentOffsetY - scrollOffset;
            rec.widget.setY(newY);
            rec.widget.visible = (newY + rec.widget.getHeight() > vpTop && newY < vpBot);
            rec.widget.active = rec.widget.visible;
        }
    }

    /** Sync all widget values back to the static cache. */
    private void syncToCache() {
        if (carrierField != null) cachedCarrier = carrierField.getValue();
        if (promptField != null) cachedPrompt = promptField.getValue();
        // Sync URL fields
        List<String> urlValues = referenceUrlManager.getUrlValues();
        for (int i = 0; i < urlFields.size() && i < urlValues.size(); i++) {
            referenceUrlManager.updateUrl(i, urlFields.get(i).getValue());
        }
    }

    @Override
    protected void init() {
        setWindowSize(WIDTH, getWindowHeight());
        super.init();
        clickableButtons.clear();
        urlFields.clear();
        formWidgetRecords.clear();

        // Compute scroll bounds
        int contentH = getContentHeight();
        int viewportH = getWindowHeight() - FORM_TOP_Y - BOTTOM_H;
        maxScroll = Math.max(0, contentH - viewportH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        var font = Minecraft.getInstance().font;
        int fieldX = guiLeft + MARGIN;
        int fieldW = WIDTH - MARGIN * 2;
        int formTop = guiTop + FORM_TOP_Y;
        int cy = 4; // content-relative Y offset

        // -- Structure preview area --
        cy += PREVIEW_H + 4;

        // -- Structure control buttons: Add, Delete, <, > --
        int btnW = 40;
        int btnGap = 4;
        int totalBtnW = 4 * btnW + 3 * btnGap;
        int btnX = guiLeft + (WIDTH - totalBtnW) / 2;

        clickableButtons.add(new ClickableButton(btnX, formTop + cy, btnW, 16,
            UIText.of("ponderer.ui.ai_generate.add"), this::addStructure,
            "ponderer.ui.ai_generate.add.tooltip", true));
        btnX += btnW + btnGap;
        clickableButtons.add(new ClickableButton(btnX, formTop + cy, btnW, 16,
            UIText.of("ponderer.ui.ai_generate.delete"), this::deleteStructure,
            "ponderer.ui.ai_generate.delete.tooltip", true));
        btnX += btnW + btnGap;
        clickableButtons.add(new ClickableButton(btnX, formTop + cy, btnW, 16, "<", this::prevStructure,
            "ponderer.ui.ai_generate.prev.tooltip", true));
        btnX += btnW + btnGap;
        clickableButtons.add(new ClickableButton(btnX, formTop + cy, btnW, 16, ">", this::nextStructure,
            "ponderer.ui.ai_generate.next.tooltip", true));
        cy += ROW_H + 4;

        // -- Carrier item (label inline with field) --
        int carrierLabelW = font.width(UIText.of("ponderer.ui.ai_generate.carrier")) + 6;
        int carrierFieldW = fieldW - carrierLabelW;
        boolean hasJei = JeiCompat.isAvailable();
        if (hasJei) carrierFieldW -= 20;

        carrierField = new SoftHintTextFieldWidget(font, fieldX + carrierLabelW, formTop + cy, carrierFieldW, FIELD_H);
        carrierField.setHint(UIText.of("ponderer.ui.ai_generate.carrier.hint"));
        carrierField.setMaxLength(128);
        carrierField.setValue(cachedCarrier);
        addRenderableWidget(carrierField);
        formWidgetRecords.add(new FormWidgetRecord(carrierField, cy));

        if (hasJei) {
            PonderButton jeiBtn = new PonderButton(fieldX + carrierLabelW + carrierFieldW + 4, formTop + cy + 2, 14, 12);
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
            formWidgetRecords.add(new FormWidgetRecord(jeiBtn, cy + 2));
        }
        cy += FIELD_H + 6;

        // -- Prompt --
        cy += LABEL_H;  // space for label
        promptField = new SoftHintTextFieldWidget(font, fieldX, formTop + cy, fieldW, FIELD_H);
        promptField.setHint(UIText.of("ponderer.ui.ai_generate.prompt.hint"));
        promptField.setMaxLength(2048);
        promptField.setValue(cachedPrompt);
        addRenderableWidget(promptField);
        formWidgetRecords.add(new FormWidgetRecord(promptField, cy));
        cy += FIELD_H + 4;

        // -- Reference URLs (starts empty, fully optional) --
        cy += LABEL_H;  // space for "Reference URLs" label
        List<String> urlValues = referenceUrlManager.getUrlValues();
        List<Boolean> urlAutoAdded = referenceUrlManager.getUrlAutoAdded();
        for (int i = 0; i < urlValues.size(); i++) {
            String url = urlValues.get(i);
            boolean isAutoAdded = i < urlAutoAdded.size() && urlAutoAdded.get(i);
            boolean isMcmodUrl = isMcModUrl(url);

            int urlFieldW = fieldW - 20;
            if (isMcmodUrl)
                urlFieldW -= 40; // Extra space for MCMod label

            if (isAutoAdded) {
                CustomBackgroundTextFieldWidget urlField = new CustomBackgroundTextFieldWidget(font, fieldX, formTop + cy,
                        urlFieldW, FIELD_H);
                urlField.setHint(UIText.of("ponderer.ui.ai_generate.url.hint"));
                urlField.setMaxLength(512);
                urlField.setValue(url);
                urlField.setEditable(false);
                urlField.setBackgroundColor(0xFF_333333);
                addRenderableWidget(urlField);
                urlFields.add(urlField);
                formWidgetRecords.add(new FormWidgetRecord(urlField, cy));
            } else {
                SoftHintTextFieldWidget urlField = new SoftHintTextFieldWidget(font, fieldX, formTop + cy, urlFieldW, FIELD_H);
                urlField.setHint(UIText.of("ponderer.ui.ai_generate.url.hint"));
                urlField.setMaxLength(512);
                urlField.setValue(url);
                addRenderableWidget(urlField);
                urlFields.add(urlField);
                formWidgetRecords.add(new FormWidgetRecord(urlField, cy));
            }

            // Remove button
            int rmBtnX = fieldX + urlFieldW + 4;
            final int idx = i;
            PonderButton rmBtn = new PonderButton(rmBtnX, formTop + cy + 2, 14, 12);
            rmBtn.withCallback(() -> removeUrl(idx));
            addRenderableWidget(rmBtn);
            formWidgetRecords.add(new FormWidgetRecord(rmBtn, cy + 2));

            cy += ROW_H;
        }

        // Add URL button
        clickableButtons.add(new ClickableButton(fieldX + 3, formTop + cy + 1, fieldW - 6, 14,
            UIText.of("ponderer.ui.ai_generate.add_url"), this::addUrl,
            "ponderer.ui.ai_generate.add_url.tooltip", true));
        cy += ROW_H + 4;

        // -- Toggle options: Build Tutorial | Include Images --
        int toggleW = (fieldW - 6) / 2;
        clickableButtons.add(new ClickableButton(fieldX, formTop + cy, toggleW, 16,
            UIText.of("ponderer.ui.ai_generate.build_tutorial") + ": " + (cachedBuildTutorial ? "ON" : "OFF"),
            this::toggleBuildTutorial,
            "ponderer.ui.ai_generate.build_tutorial.tooltip", true));
        clickableButtons.add(new ClickableButton(fieldX + toggleW + 6, formTop + cy, toggleW, 16,
            UIText.of("ponderer.ui.ai_generate.include_images") + ": " + (cachedIncludeImages ? "ON" : "OFF"),
            this::toggleIncludeImages,
            "ponderer.ui.ai_generate.include_images.tooltip", true));
        cy += ROW_H + 4;

        // -- Status message area (scrollable) --
        // status is rendered inline in renderWindow, no widget needed
        cy += 12;

        // -- Generate / Back buttons (fixed bottom) --
        int bottomY = guiTop + getWindowHeight() - BOTTOM_H + 4;
        int genBtnW = 80;
        clickableButtons.add(new ClickableButton(guiLeft + MARGIN, bottomY, genBtnW, 20,
            cachedGenerating ? UIText.of("ponderer.ui.ai_generate.generating") : UIText.of("ponderer.ui.ai_generate.generate"),
            this::doGenerate,
            "ponderer.ui.ai_generate.generate.tooltip", false));
        clickableButtons.add(new ClickableButton(guiLeft + WIDTH - MARGIN - 60, bottomY, 60, 20,
            UIText.of("ponderer.ui.function_page.back"), this::goBack, null, false));

        // Apply scroll positions
        updateScrollPositions();

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
        referenceUrlManager.addManualUrl("");
        init(minecraft, width, height);
    }

    private void removeUrl(int index) {
        syncToCache();
        referenceUrlManager.removeUrl(index);
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
        for (String u : referenceUrlManager.getUrlValues()) {
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
            .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
            .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true), new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(WIDTH, wH)
            .render(graphics);

        var font = Minecraft.getInstance().font;

        // Title (fixed)
        graphics.drawCenteredString(font, this.title, guiLeft + WIDTH / 2, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WIDTH - 5, guiTop + 21, UILayoutConstants.COLOR_SEPARATOR);

        // ── Scrollable content area ──
        int vpTop = viewportTop();
        int vpBot = viewportBottom();
        graphics.enableScissor(guiLeft, vpTop, guiLeft + WIDTH, vpBot);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        int formTop = guiTop + FORM_TOP_Y;
        int cy = 4; // content-relative Y (same as in init)

        // -- Structure preview box --
        int previewX = guiLeft + MARGIN;
        int previewW = WIDTH - MARGIN * 2;
        int py = formTop + cy;
        graphics.fill(previewX, py, previewX + previewW, py + PREVIEW_H, 0x40_222244);
        graphics.fill(previewX, py, previewX + previewW, py + 1, 0x60_555588);
        graphics.fill(previewX, py + PREVIEW_H - 1, previewX + previewW, py + PREVIEW_H, 0x60_555588);
        graphics.fill(previewX, py, previewX + 1, py + PREVIEW_H, 0x60_555588);
        graphics.fill(previewX + previewW - 1, py, previewX + previewW, py + PREVIEW_H, 0x60_555588);

        if (cachedStructurePaths.isEmpty()) {
            graphics.drawCenteredString(font, UIText.of("ponderer.ui.ai_generate.no_structure"),
                guiLeft + WIDTH / 2, py + PREVIEW_H / 2 - 4, 0x666666);
        } else {
            StructureDescriber.StructureInfo info = cachedStructureInfos.get(cachedStructureIndex);
            String fileName = cachedStructurePaths.get(cachedStructureIndex).getFileName().toString();
            if (fileName.endsWith(".nbt")) fileName = fileName.substring(0, fileName.length() - 4);

            String header = (cachedStructureIndex + 1) + "/" + cachedStructurePaths.size() + " - " + fileName;
            graphics.drawCenteredString(font, header, guiLeft + WIDTH / 2, py + 6, 0xFFFFFF);

            String size = info.sizeX() + " x " + info.sizeY() + " x " + info.sizeZ();
            graphics.drawCenteredString(font, size, guiLeft + WIDTH / 2, py + 20, 0xAAAAFF);

            List<String> types = info.blockTypes();
            String typesStr = String.join(", ", types);
            if (font.width(typesStr) > previewW - 10) {
                typesStr = font.plainSubstrByWidth(typesStr, previewW - 20) + "...";
            }
            graphics.drawString(font, typesStr, previewX + 5, py + 34, 0x888888);

            String countHint = types.size() + " block types";
            graphics.drawString(font, countHint, previewX + 5, py + PREVIEW_H - 14, 0x666666);
        }
        cy += PREVIEW_H + 4;

        // -- Structure buttons (via clickableButtons, scrollable) --
        cy += ROW_H + 4;

        // -- Carrier label (inline with field) --
        int lc = UILayoutConstants.COLOR_LABEL;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_generate.carrier"),
            guiLeft + MARGIN, formTop + cy + 4, lc);
        cy += FIELD_H + 6;

        // -- Prompt label --
        graphics.drawString(font, UIText.of("ponderer.ui.ai_generate.prompt"),
            guiLeft + MARGIN, formTop + cy, lc);
        cy += LABEL_H;
        cy += FIELD_H + 4;

        // -- URL label --
        graphics.drawString(font, UIText.of("ponderer.ui.ai_generate.urls"),
            guiLeft + MARGIN, formTop + cy, lc);
        cy += LABEL_H;

        // Render MCMod labels for generated URLs
        int fieldX = guiLeft + MARGIN;
        int fieldW = WIDTH - MARGIN * 2;
        List<String> urlValues = referenceUrlManager.getUrlValues();
        for (int i = 0; i < urlValues.size(); i++) {
            String url = urlValues.get(i);
            if (isMcModUrl(url)) {
                int urlFieldW = fieldW - 20 - 40;
                int rmBtnX = fieldX + urlFieldW + 4;
                int labelX = rmBtnX + 14 + 4;
                graphics.drawString(font, UIText.of("ponderer.ui.ai_generate.url.mcmod"), labelX, formTop + cy + 4, 0xAAAAFF);
            }
            cy += ROW_H;
        }

        cy += ROW_H + 4;

        // -- Toggle options (rendered via clickableButtons, scrollable) --
        cy += ROW_H + 4;

        // -- Status --
        if (cachedStatusMessage != null) {
            String msg = cachedStatusMessage;
            if (font.width(msg) > WIDTH - MARGIN * 2) {
                msg = font.plainSubstrByWidth(msg, WIDTH - MARGIN * 2 - 10) + "...";
            }
            graphics.drawCenteredString(font, msg, guiLeft + WIDTH / 2, formTop + cy, cachedStatusColor);
        }

        // Render scrollable ClickableButtons inside translate
        int adjMouseY = (int)(mouseY + scrollOffset);
        for (ClickableButton btn : clickableButtons) {
            if (!btn.scrollable) continue;
            renderClickableButton(graphics, font, btn, mouseX, adjMouseY, false);
        }

        graphics.pose().popPose();
        graphics.disableScissor();

        // ── Fixed bottom buttons ──
        for (ClickableButton btn : clickableButtons) {
            if (btn.scrollable) continue;
            renderClickableButton(graphics, font, btn, mouseX, mouseY, 
                cachedGenerating && btn.label.equals(UIText.of("ponderer.ui.ai_generate.generating")));
        }

        // ── Scrollbar ──
        if (isScrollEnabled()) {
            renderScrollbar(graphics, vpTop, vpBot);
        }
    }

    private void renderClickableButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        ClickableButton btn, int mouseX, int mouseY, boolean disabled) {
        boolean hovered = mouseX >= btn.x && mouseX < btn.x + btn.w
            && mouseY >= btn.y && mouseY < btn.y + btn.h;
        int bgColor = disabled ? 0x40_333355 : (hovered ? 0x80_4466aa : 0x60_333366);
        int borderColor = hovered ? 0xCC_6688cc : 0x60_555588;
        graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, bgColor);
        graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + 1, borderColor);
        graphics.fill(btn.x, btn.y + btn.h - 1, btn.x + btn.w, btn.y + btn.h, borderColor);
        graphics.fill(btn.x, btn.y, btn.x + 1, btn.y + btn.h, borderColor);
        graphics.fill(btn.x + btn.w - 1, btn.y, btn.x + btn.w, btn.y + btn.h, borderColor);
        int textX = btn.x + (btn.w - font.width(btn.label)) / 2;
        int textY = btn.y + (btn.h - font.lineHeight) / 2 + 1;
        graphics.drawString(font, btn.label, textX, textY,
            disabled ? 0x888888 : (hovered ? 0xFFFFFF : UILayoutConstants.COLOR_LABEL));
    }

    private void renderScrollbar(GuiGraphics graphics, int vpTop, int vpBot) {
        int trackX = guiLeft + WIDTH - UILayoutConstants.SCROLLBAR_W - 2;
        int trackH = vpBot - vpTop;
        graphics.fill(trackX, vpTop, trackX + UILayoutConstants.SCROLLBAR_W, vpBot, UILayoutConstants.COLOR_SCROLLBAR_BG);
        int contentH = getContentHeight();
        int thumbH = Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB,
            (int) ((float) trackH * trackH / contentH));
        int thumbY = vpTop + (int) ((float) scrollOffset / maxScroll * (trackH - thumbH));
        graphics.fill(trackX, thumbY, trackX + UILayoutConstants.SCROLLBAR_W, thumbY + thumbH,
            UILayoutConstants.COLOR_SCROLLBAR_FG);
    }

    private boolean isMcModUrl(String url) {
        return url.startsWith("https://www.mcmod.cn/item/");
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int vpTop = viewportTop();
        int vpBot = viewportBottom();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        var font = Minecraft.getInstance().font;

        // JEI & URL remove button labels — scissored to viewport
        graphics.enableScissor(guiLeft, vpTop, guiLeft + WIDTH, vpBot);

        if (JeiCompat.isAvailable()) {
            for (var child : children()) {
                if (child instanceof PonderButton pb && pb.getWidth() == 14 && pb.getHeight() == 12) {
                    if (!pb.visible) continue;
                    int pbx = pb.getX();
                    int pby = pb.getY();
                    // Check if it's the JEI button (near carrier field)
                    if (pby >= vpTop && pby <= vpTop + PREVIEW_H + ROW_H + 60 - scrollOffset) {
                        int color = (jeiActive && jeiTargetField == carrierField) ? 0x55FF55 : 0xAAAAFF;
                        graphics.drawCenteredString(font, "J", pbx + 7, pby + 2, color);
                    } else {
                        graphics.drawCenteredString(font, "-", pbx + 7, pby + 2, 0xFF6666);
                    }
                }
            }
        } else {
            for (var child : children()) {
                if (child instanceof PonderButton pb && pb.getWidth() == 14 && pb.getHeight() == 12) {
                    if (!pb.visible) continue;
                    graphics.drawCenteredString(font, "-", pb.getX() + 7, pb.getY() + 2, 0xFF6666);
                }
            }
        }

        graphics.disableScissor();

        // Button tooltips (scrollable buttons need scroll-adjusted Y in hit test)
        for (ClickableButton btn : clickableButtons) {
            if (btn.tooltip == null) continue;
            double adjY = btn.scrollable ? mouseY + scrollOffset : mouseY;
            if (btn.scrollable) {
                int screenBtnY = btn.y - scrollOffset;
                if (screenBtnY + btn.h < vpTop || screenBtnY > vpBot) continue;
            }
            if (mouseX >= btn.x && mouseX < btn.x + btn.w
                && adjY >= btn.y && adjY < btn.y + btn.h) {
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
                double adjY = btn.scrollable ? mouseY + scrollOffset : mouseY;
                if (mouseX >= btn.x && mouseX < btn.x + btn.w
                    && adjY >= btn.y && adjY < btn.y + btn.h) {
                    if (btn.scrollable) {
                        int vpTop = viewportTop();
                        int vpBot = viewportBottom();
                        if (mouseY < vpTop || mouseY >= vpBot) continue;
                    }
                    btn.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isScrollEnabled()) {
            scrollOffset = Mth.clamp(scrollOffset - (int)(scrollY * UILayoutConstants.SCROLL_SPEED), 0, maxScroll);
            updateScrollPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    /**
     * Add an automatically generated URL to the reference URLs list.
     * This method is used for URLs that are automatically fetched by the mod,
     * such as MCMod encyclopedia links for items selected via JEI.
     * Automatically added URLs are marked as non-editable.
     * 
     * @param url    The URL to add
     * @param itemId The item identifier (registry name) associated with this URL
     */
    public void addAutoUrl(String url, String itemId) {
        syncToCache();
        // Add auto-added URL using ReferenceUrlManager
        referenceUrlManager.addUrl(url, itemId, true);
        // Reinitialize screen to reflect changes
        init(minecraft, width, height);
    }

    /**
     * Remove all auto-added URLs for the current item.
     */
    public void removeAutoUrlsForItem() {
        referenceUrlManager.removeAutoUrlsForItem();
    }

    /**
     * Update auto-added URL for the given item.
     * This is a convenience method that handles the entire process of updating
     * auto-added URLs, including syncing, removing old URLs, and reinitializing the
     * screen.
     * 
     * @param url    The new URL to add, or null to remove existing auto-added URLs
     * @param itemId The item identifier (registry name) associated with this URL
     */
    public void updateAutoUrl(@Nullable String url, String itemId) {
        syncToCache();

        if (url != null) {
            // Add new auto-added URL
            addAutoUrl(url, itemId);
        } else {
            // Remove existing auto-added URLs
            removeAutoUrlsForItem();
            // Reinitialize screen to reflect changes
            init(minecraft, width, height);
        }
    }

    @Override public int getGuiLeft() { return guiLeft; }
    @Override public int getGuiTop() { return guiTop; }
    @Override public int getGuiWidth() { return WIDTH; }
    @Override public int getGuiHeight() { return getWindowHeight(); }
}
