package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ai.AiSceneGenerator;
import com.nododiiiii.ponderer.ai.StructureDescriber;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.ui.catnip.PonderIconStencils;
import com.nododiiiii.ponderer.util.SafePaths;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AiGenerateScreen extends AbstractJeiAwareFormScreen {

    private static final List<Path> cachedStructurePaths = new ArrayList<>();
    private static final List<StructureDescriber.StructureInfo> cachedStructureInfos = new ArrayList<>();
    private static int cachedStructureIndex = 0;
    private static String cachedCarrier = "";
    private static String cachedPrompt = "";
    private static final ReferenceUrlManager referenceUrlManager = new ReferenceUrlManager();
    private static boolean cachedBuildTutorial = false;
    private static boolean cachedIncludeImages = false;
    @Nullable
    private static String cachedStatusMessage = null;
    private static boolean cachedStatusIsError = false;
    private static boolean cachedGenerating = false;

    private static final int TOP_PANEL_HEADER_ROWS = 4;
    private static final int TOP_PANEL_GAP = 8;
    private static final int PREVIEW_HEIGHT = 80;
    private static final int STRUCTURE_NAME_HEIGHT = 22;
    private static final int NAV_ROW_HEIGHT = 42;
    private static final int BUTTON_ROW_HEIGHT = 24;
    private static final int PANEL_INSET = 4;
    private static final int PANEL_SPLIT_GAP = 8;
    private static final int PANEL_BORDER = 0x60FFFFFF;
    private static final int PANEL_BACKGROUND = 0x40000000;
    private static final int PANEL_TEXT = 0xFFFFFF;
    private static final int PANEL_MUTED_TEXT = 0xFFCCCC77;

    private final StructurePreviewWidget preview = new StructurePreviewWidget(0, 0, 0, 0);
    private final BoxWidget previousStructureButton = createTopPanelButton(this::selectPreviousStructure);
    private final BoxWidget nextStructureButton = createTopPanelButton(this::selectNextStructure);
    private final BoxWidget addStructureButton = createTopPanelButton(this::openStructurePicker);
    private final BoxWidget deleteStructureButton = createTopPanelButton(this::deleteStructure);

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int previewY;
    private int nameY;
    private int navRowY;
    private int actionRowY;
    private boolean topPanelVisible;

    @Nullable
    private Path loadedPreviewPath;

    public AiGenerateScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.ai_generate.title",
            UILayoutConstants.EDITOR_LIST_W, JeiCompat::setActiveScreen);
    }

    @Override
    protected void init() {
        super.init();
        applyCachedStatus();
        layoutTopPanel();
        updateTopPanelButtons();
        if (!cachedStructurePaths.isEmpty() && topPanelVisible) {
            loadPreviewForCurrent();
        }
    }

    @Override
    public void resize(Minecraft client, int newWidth, int newHeight) {
        super.resize(client, newWidth, newHeight);
        layoutTopPanel();
        updateTopPanelButtons();
    }

    @Override
    public void removed() {
        super.removed();
        preview.dispose();
        loadedPreviewPath = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (topPanelVisible) {
            if (previousStructureButton.mouseClicked(mouseX, mouseY, button)
                || nextStructureButton.mouseClicked(mouseX, mouseY, button)
                || addStructureButton.mouseClicked(mouseX, mouseY, button)
                || deleteStructureButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (preview.onMouseDown(mouseX, mouseY)) {
                setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (topPanelVisible && preview.onMouseDrag(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean topPanelHandled = previousStructureButton.mouseReleased(mouseX, mouseY, button)
            || nextStructureButton.mouseReleased(mouseX, mouseY, button)
            || addStructureButton.mouseReleased(mouseX, mouseY, button)
            || deleteStructureButton.mouseReleased(mouseX, mouseY, button)
            || preview.onMouseUp();
        boolean superHandled = super.mouseReleased(mouseX, mouseY, button);
        return topPanelHandled || superHandled;
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        for (int i = 0; i < TOP_PANEL_HEADER_ROWS; i++) {
            entries.add(new SpacerHeaderEntry());
        }
    }

    @Override
    protected int headerListGap() {
        return TOP_PANEL_GAP;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindowForeground(graphics, mouseX, mouseY, partialTicks);
        if (!topPanelVisible) {
            return;
        }
        renderTopPanel(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> cachedCarrier, value -> cachedCarrier = value),
            "ponderer.ui.ai_generate.carrier",
            null,
            "ponderer.ui.ai_generate.carrier.hint",
            -1,
            entry -> entry.field().setMaxLength(128),
            FieldDecorators.jei(IdFieldMode.ITEM)));

        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> cachedPrompt, value -> cachedPrompt = value),
            "ponderer.ui.ai_generate.prompt",
            null,
            "ponderer.ui.ai_generate.prompt.hint",
            -1,
            entry -> entry.field().setMaxLength(2048)));

        entries.add(FieldSpecs.sectionHeader(UIText.of("ponderer.ui.ai_generate.urls")));
        List<String> urlValues = referenceUrlManager.getUrlValues();
        for (int i = 0; i < urlValues.size(); i++) {
            final int index = i;
            entries.add(FieldSpecs.text(
                FieldBindings.transientString(
                    () -> referenceUrlManager.getUrlValues().get(index),
                    value -> referenceUrlManager.updateUrl(index, value)),
                i == 0 ? "ponderer.ui.ai_generate.urls" : "",
                null,
                "ponderer.ui.ai_generate.url.hint",
                -1,
                entry -> entry.field().setMaxLength(512),
                FieldDecorators.textAction("-", 0xFF6666, null, () -> removeUrl(index))));
        }
        entries.add(FieldSpecs.fullButton(
            UIText.of("ponderer.ui.ai_generate.add_url"),
            UIText.of("ponderer.ui.ai_generate.add_url.tooltip"),
            this::addUrl));

        entries.add(FieldSpecs.toggle(
            "ponderer.ui.ai_generate.build_tutorial",
            "ponderer.ui.ai_generate.build_tutorial.tooltip",
            () -> cachedBuildTutorial,
            this::toggleBuildTutorial));
        entries.add(FieldSpecs.toggle(
            "ponderer.ui.ai_generate.include_images",
            "ponderer.ui.ai_generate.include_images.tooltip",
            () -> cachedIncludeImages,
            this::toggleIncludeImages));
    }

    @Override
    protected boolean saveEdits() {
        return doGenerate();
    }

    @Override
    protected void prepareSnapshotForBuild(Map<String, String> snapshot) {
        restoreSnapshot(snapshot);
    }

    @Override
    protected void afterSnapshotRestored(Map<String, String> snapshot) {
        applyCachedStatus();
    }

    @Override
    protected boolean rebuildOnJeiStateChange() {
        return true;
    }

    @Override
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("structure_index", String.valueOf(cachedStructureIndex));
        snapshot.put("structure_count", String.valueOf(cachedStructurePaths.size()));
        for (int i = 0; i < cachedStructurePaths.size(); i++) {
            snapshot.put("structure_" + i, cachedStructurePaths.get(i).toString());
        }
        snapshot.put("carrier", cachedCarrier);
        snapshot.put("prompt", cachedPrompt);
        snapshot.put("build_tutorial", String.valueOf(cachedBuildTutorial));
        snapshot.put("include_images", String.valueOf(cachedIncludeImages));
        referenceUrlManager.snapshot(snapshot);
        return snapshot;
    }

    private void layoutTopPanel() {
        panelW = currentListWidthValue();
        panelX = width / 2 - panelW / 2 + listHorizontalOffset();
        panelY = contentAreaTop();
        panelH = topPanelHeight();
        topPanelVisible = panelW > 0 && panelH > 0;
        previewY = panelY + 1;
        nameY = panelY + PREVIEW_HEIGHT + 8;
        navRowY = panelY + PREVIEW_HEIGHT + STRUCTURE_NAME_HEIGHT + 2;
        actionRowY = panelY + panelH - BUTTON_ROW_HEIGHT - PANEL_INSET;
        preview.setBounds(panelX + 1, previewY, Math.max(0, panelW - 2), PREVIEW_HEIGHT - 1);
        layoutTopPanelButtons();
    }

    private void layoutTopPanelButtons() {
        if (!topPanelVisible) {
            hideTopPanelButtons();
            return;
        }

        int navButtonSize = 18;
        int navInset = 8;
        int previewCenterY = navRowY + (NAV_ROW_HEIGHT - navButtonSize) / 2;

        previousStructureButton.setX(panelX + navInset);
        previousStructureButton.setY(previewCenterY);
        previousStructureButton.setWidth(navButtonSize);
        previousStructureButton.setHeight(navButtonSize);
        previousStructureButton.showingElement(PonderIconStencils.centered(PonderGuiTextures.ICON_PONDER_LEFT));

        nextStructureButton.setX(panelX + panelW - navInset - navButtonSize);
        nextStructureButton.setY(previewCenterY);
        nextStructureButton.setWidth(navButtonSize);
        nextStructureButton.setHeight(navButtonSize);
        nextStructureButton.showingElement(PonderIconStencils.centered(PonderGuiTextures.ICON_PONDER_RIGHT));

        int buttonWidth = (panelW - PANEL_INSET * 2 - PANEL_SPLIT_GAP) / 2;
        addStructureButton.setX(panelX + PANEL_INSET);
        addStructureButton.setY(actionRowY);
        addStructureButton.setWidth(buttonWidth);
        addStructureButton.setHeight(BUTTON_ROW_HEIGHT);

        deleteStructureButton.setX(addStructureButton.getX() + buttonWidth + PANEL_SPLIT_GAP);
        deleteStructureButton.setY(actionRowY);
        deleteStructureButton.setWidth(buttonWidth);
        deleteStructureButton.setHeight(BUTTON_ROW_HEIGHT);
    }

    private void hideTopPanelButtons() {
        previousStructureButton.visible = false;
        nextStructureButton.visible = false;
        addStructureButton.visible = false;
        deleteStructureButton.visible = false;
    }

    private void updateTopPanelButtons() {
        boolean hasStructures = !cachedStructurePaths.isEmpty();
        boolean canTurn = cachedStructurePaths.size() > 1;

        previousStructureButton.visible = topPanelVisible;
        nextStructureButton.visible = topPanelVisible;
        addStructureButton.visible = topPanelVisible;
        deleteStructureButton.visible = topPanelVisible;

        previousStructureButton.active = topPanelVisible && canTurn;
        nextStructureButton.active = topPanelVisible && canTurn;
        addStructureButton.active = topPanelVisible;
        deleteStructureButton.active = topPanelVisible && hasStructures;

        previousStructureButton.updateGradientFromState();
        nextStructureButton.updateGradientFromState();
        addStructureButton.updateGradientFromState();
        deleteStructureButton.updateGradientFromState();

        previousStructureButton.getToolTip().clear();
        previousStructureButton.getToolTip().add(Component.translatable("ponderer.ui.ai_generate.prev.tooltip"));
        nextStructureButton.getToolTip().clear();
        nextStructureButton.getToolTip().add(Component.translatable("ponderer.ui.ai_generate.next.tooltip"));
        addStructureButton.getToolTip().clear();
        addStructureButton.getToolTip().add(Component.translatable("ponderer.ui.ai_generate.add.tooltip"));
        deleteStructureButton.getToolTip().clear();
        deleteStructureButton.getToolTip().add(Component.translatable("ponderer.ui.ai_generate.delete.tooltip"));
    }

    private void renderTopPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int nameSeparatorY = panelY + PREVIEW_HEIGHT;
        int navSeparatorY = navRowY + NAV_ROW_HEIGHT;
        int navCenterY = navRowY + (NAV_ROW_HEIGHT - 8) / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BACKGROUND);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, PANEL_BORDER);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, PANEL_BORDER);
        graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, PANEL_BORDER);
        graphics.fill(panelX, nameSeparatorY, panelX + panelW, nameSeparatorY + 1, PANEL_BORDER);
        graphics.fill(panelX, navSeparatorY, panelX + panelW, navSeparatorY + 1, PANEL_BORDER);

        graphics.enableScissor(panelX + 1, previewY, panelX + panelW - 1, previewY + PREVIEW_HEIGHT - 1);
        preview.render(graphics, partialTicks);
        graphics.disableScissor();

        graphics.drawCenteredString(font,
            UIText.of("ponderer.ui.ai_generate.preview_title"),
            panelX + panelW / 2,
            panelY + 8,
            PANEL_TEXT);

        String structureName = currentStructureName();
        graphics.drawCenteredString(font,
            font.plainSubstrByWidth(structureName, Math.max(20, panelW - 16)),
            panelX + panelW / 2,
            nameY,
            hasStructures() ? PANEL_TEXT : PANEL_MUTED_TEXT);

        graphics.drawCenteredString(font,
            currentStructureCounter(),
            panelX + panelW / 2,
            navCenterY,
            PANEL_TEXT);

        previousStructureButton.render(graphics, mouseX, mouseY, partialTicks);
        nextStructureButton.render(graphics, mouseX, mouseY, partialTicks);
        addStructureButton.render(graphics, mouseX, mouseY, partialTicks);
        deleteStructureButton.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(font,
            UIText.of("ponderer.ui.ai_generate.add"),
            addStructureButton.getX() + addStructureButton.getWidth() / 2,
            addStructureButton.getY() + (addStructureButton.getHeight() - 8) / 2,
            addStructureButton.active ? PANEL_TEXT : 0x777777);
        graphics.drawCenteredString(font,
            UIText.of("ponderer.ui.ai_generate.delete"),
            deleteStructureButton.getX() + deleteStructureButton.getWidth() / 2,
            deleteStructureButton.getY() + (deleteStructureButton.getHeight() - 8) / 2,
            deleteStructureButton.active ? PANEL_TEXT : 0x777777);
    }

    private boolean hasStructures() {
        return !cachedStructurePaths.isEmpty();
    }

    private void openStructurePicker() {
        Minecraft.getInstance().setScreen(new StructurePickerScreen(
            this::reopenFromStructurePicker,
            snapshotState(),
            "ai_generate_structure",
            currentStructureReference()));
    }

    private void reopenFromStructurePicker(Map<String, String> snapshot) {
        String selected = snapshot.get("ai_generate_structure");
        if (selected != null && !selected.isBlank()) {
            addStructureFromPicker(selected);
        }
        Minecraft.getInstance().setScreen(new AiGenerateScreen());
    }

    private void addStructureFromPicker(String structureId) {
        Path path = resolveStructurePath(structureId);
        if (path == null) {
            setCachedStatus(UIText.of("ponderer.ui.ai_generate.error.invalid_structure"), true);
            return;
        }

        for (int i = 0; i < cachedStructurePaths.size(); i++) {
            if (cachedStructurePaths.get(i).equals(path)) {
                cachedStructureIndex = i;
                loadPreviewForCurrent();
                setCachedStatus(null, false);
                return;
            }
        }

        try {
            StructureDescriber.StructureInfo info = StructureDescriber.describe(path);
            cachedStructurePaths.add(path);
            cachedStructureInfos.add(info);
            cachedStructureIndex = cachedStructurePaths.size() - 1;
            setCachedStatus(null, false);
            loadPreviewForCurrent();
        } catch (Exception e) {
            setCachedStatus("Failed to parse NBT: " + e.getMessage(), true);
        }
    }

    @Nullable
    private Path resolveStructurePath(String structureId) {
        String trimmed = structureId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT).startsWith("ponderer:")
            ? trimmed
            : "ponderer:" + trimmed;
        ResourceLocation resourceId = ResourceLocation.tryParse(normalized);
        if (resourceId == null) {
            return null;
        }
        return SafePaths.resolveNamespacedPath(SceneStore.getStructureDir(), resourceId, "ponderer", ".nbt");
    }

    @Nullable
    private String currentStructureReference() {
        if (cachedStructurePaths.isEmpty()) {
            return null;
        }
        Path structureDir = SceneStore.getStructureDir();
        Path selected = cachedStructurePaths.get(cachedStructureIndex);
        if (!selected.startsWith(structureDir)) {
            return null;
        }
        String relative = structureDir.relativize(selected).toString().replace('\\', '/');
        if (relative.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            relative = relative.substring(0, relative.length() - 4);
        }
        return "ponderer:" + relative;
    }

    private void deleteStructure() {
        if (cachedStructurePaths.isEmpty()) {
            return;
        }
        cachedStructurePaths.remove(cachedStructureIndex);
        cachedStructureInfos.remove(cachedStructureIndex);
        if (cachedStructureIndex >= cachedStructurePaths.size()) {
            cachedStructureIndex = Math.max(0, cachedStructurePaths.size() - 1);
        }
        if (cachedStructurePaths.isEmpty()) {
            preview.clear();
            loadedPreviewPath = null;
        } else {
            loadPreviewForCurrent();
        }
        updateTopPanelButtons();
        rebuildListPreservingScroll();
    }

    private void selectPreviousStructure() {
        if (cachedStructurePaths.size() <= 1) {
            return;
        }
        int target = cachedStructureIndex - 1;
        if (target < 0) {
            target = cachedStructurePaths.size() - 1;
        }
        selectStructure(target);
    }

    private void selectNextStructure() {
        if (cachedStructurePaths.size() <= 1) {
            return;
        }
        selectStructure((cachedStructureIndex + 1) % cachedStructurePaths.size());
    }

    private void selectStructure(int index) {
        if (index < 0 || index >= cachedStructurePaths.size()) {
            return;
        }
        if (index == cachedStructureIndex
            && topPanelVisible
            && cachedStructurePaths.get(index).equals(loadedPreviewPath)) {
            return;
        }
        cachedStructureIndex = index;
        loadPreviewForCurrent();
        updateTopPanelButtons();
        rebuildListPreservingScroll();
    }

    private void loadPreviewForCurrent() {
        if (!topPanelVisible || cachedStructurePaths.isEmpty()) {
            return;
        }
        Path file = cachedStructurePaths.get(cachedStructureIndex);
        int count = StructurePreviewWidget.countBlocks(file);
        if (count <= 0) {
            preview.setStatus("ponderer.ui.structure_picker.preview.empty");
            loadedPreviewPath = null;
            return;
        }
        if (count > StructurePreviewWidget.DEFAULT_LARGE_THRESHOLD) {
            preview.setStatus("ponderer.ui.structure_picker.preview.confirm_pending");
            loadedPreviewPath = null;
            new ConfirmationScreen()
                .centered()
                .withText(Component.translatable("ponderer.ui.structure_picker.preview.large", count))
                .withAction(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        preview.load(file);
                        loadedPreviewPath = file;
                    } else {
                        preview.setStatus("ponderer.ui.structure_picker.preview.skipped");
                    }
                })
                .open(this);
            return;
        }
        preview.load(file);
        loadedPreviewPath = file;
    }

    private void addUrl() {
        referenceUrlManager.addManualUrl("");
        rebuildListPreservingScroll();
    }

    private void removeUrl(int index) {
        referenceUrlManager.removeUrl(index);
        rebuildListPreservingScroll();
    }

    private void toggleBuildTutorial() {
        cachedBuildTutorial = !cachedBuildTutorial;
        rebuildListPreservingScroll();
    }

    private void toggleIncludeImages() {
        cachedIncludeImages = !cachedIncludeImages;
        rebuildListPreservingScroll();
    }

    public void updateAutoUrl(@Nullable String url, String itemId) {
        referenceUrlManager.removeAutoUrlsForItem();
        if (url != null && !url.isBlank()) {
            referenceUrlManager.addUrl(url, itemId, true);
        }
        if (Minecraft.getInstance().screen == this) {
            rebuildListPreservingScroll();
        }
    }

    private boolean doGenerate() {
        if (cachedGenerating) {
            return false;
        }
        if (cachedStructurePaths.isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.ai_generate.error.no_structure"));
            return false;
        }
        if (cachedCarrier.trim().isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.ai_generate.error.no_carrier"));
            return false;
        }
        if (cachedPrompt.trim().isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.ai_generate.error.no_prompt"));
            return false;
        }

        List<String> urls = new ArrayList<>();
        for (String url : referenceUrlManager.getUrlValues()) {
            if (url != null && !url.isBlank()) {
                urls.add(url.trim());
            }
        }

        cachedGenerating = true;
        setCachedStatus(UIText.of("ponderer.ui.ai_generate.status.generating"), false);
        applyCachedStatus();

        AiSceneGenerator.generate(
            new ArrayList<>(cachedStructurePaths),
            cachedCarrier.trim(),
            cachedPrompt.trim(),
            urls,
            null,
            cachedBuildTutorial,
            cachedIncludeImages,
            filePath -> Minecraft.getInstance().execute(() -> {
                cachedGenerating = false;
                setCachedStatus(UIText.of("ponderer.ui.ai_generate.status.success"), false);
                applyCachedStatus();
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("ponderer.ui.ai_generate.status.success"),
                        false);
                }
            }),
            error -> Minecraft.getInstance().execute(() -> {
                cachedGenerating = false;
                setCachedStatus(error, true);
                applyCachedStatus();
            }),
            statusMsg -> Minecraft.getInstance().execute(() -> {
                setCachedStatus(statusMsg, false);
                applyCachedStatus();
            }));
        return true;
    }

    private void applyCachedStatus() {
        if (cachedStatusMessage == null || cachedStatusMessage.isBlank()) {
            clearStatusMessages();
            return;
        }
        if (cachedStatusIsError) {
            setErrorMessage(cachedStatusMessage);
        } else {
            setInfoMessage(cachedStatusMessage);
        }
    }

    private void refreshCurrentScreen() {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen == this) {
                applyCachedStatus();
                updateTopPanelButtons();
                rebuildListPreservingScroll();
            }
        });
    }

    private static void setCachedStatus(@Nullable String status, boolean isError) {
        cachedStatusMessage = status;
        cachedStatusIsError = status != null && isError;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        cachedStructurePaths.clear();
        cachedStructureInfos.clear();

        int count = 0;
        try {
            count = Integer.parseInt(snapshot.getOrDefault("structure_count", "0"));
        } catch (NumberFormatException ignored) {
        }
        for (int i = 0; i < count; i++) {
            String rawPath = snapshot.get("structure_" + i);
            if (rawPath == null || rawPath.isBlank()) {
                continue;
            }
            Path path = Path.of(rawPath);
            cachedStructurePaths.add(path);
            cachedStructureInfos.add(describeStructureSafe(path));
        }

        try {
            cachedStructureIndex = Integer.parseInt(snapshot.getOrDefault("structure_index", "0"));
        } catch (NumberFormatException ignored) {
            cachedStructureIndex = 0;
        }
        if (cachedStructureIndex < 0 || cachedStructureIndex >= cachedStructurePaths.size()) {
            cachedStructureIndex = Math.max(0, cachedStructurePaths.size() - 1);
        }

        cachedCarrier = snapshot.getOrDefault("carrier", "");
        cachedPrompt = snapshot.getOrDefault("prompt", "");
        cachedBuildTutorial = Boolean.parseBoolean(snapshot.getOrDefault("build_tutorial", "false"));
        cachedIncludeImages = Boolean.parseBoolean(snapshot.getOrDefault("include_images", "false"));
        referenceUrlManager.restore(snapshot);
        updateTopPanelButtons();
    }

    private static StructureDescriber.StructureInfo describeStructureSafe(Path path) {
        try {
            return StructureDescriber.describe(path);
        } catch (Exception ignored) {
            return new StructureDescriber.StructureInfo(0, 0, 0, "", List.of());
        }
    }

    private int topPanelHeight() {
        return PREVIEW_HEIGHT + STRUCTURE_NAME_HEIGHT + NAV_ROW_HEIGHT + BUTTON_ROW_HEIGHT + 2;
    }

    private String currentStructureName() {
        if (cachedStructurePaths.isEmpty()) {
            return UIText.of("ponderer.ui.ai_generate.no_structure_name");
        }
        return cachedStructurePaths.get(cachedStructureIndex).getFileName().toString();
    }

    private String currentStructureCounter() {
        if (cachedStructurePaths.isEmpty()) {
            return "0/0";
        }
        return (cachedStructureIndex + 1) + "/" + cachedStructurePaths.size();
    }

    private static BoxWidget createTopPanelButton(Runnable callback) {
        return new BoxWidget(0, 0, 20, 20).withPadding(2, 2).withCallback(callback);
    }

    private static class SpacerHeaderEntry extends ConfigScreenList.Entry {
        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
        }

        @Override
        public Component getNarration() {
            return CommonComponents.EMPTY;
        }
    }
}
