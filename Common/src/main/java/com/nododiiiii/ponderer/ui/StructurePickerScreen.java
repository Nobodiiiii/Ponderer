package com.nododiiiii.ponderer.ui;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.ActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import com.nododiiiii.ponderer.util.SafePaths;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class StructurePickerScreen extends AbstractDeclarativeListScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Source { PONDERER, CREATE }

    private final SnapshotReturnContext context;
    private final Map<String, String> formSnapshot;
    private final String snapshotKey;

    private Source currentSource = Source.PONDERER;
    private final List<Path> currentEntries = new ArrayList<>();

    @Nullable
    private Path selectedPath;
    private Source selectedSource = Source.PONDERER;
    /** True when the selection comes from outside the ponderer/create dirs (file manager). */
    private boolean selectedExternal;
    @Nullable
    private String selectedExternalName;

    private final StructurePreviewWidget preview = new StructurePreviewWidget(0, 0, 0, 0);
    private static final int PREVIEW_WIDTH = 220;
    private static final int PREVIEW_GAP = 10;
    /** Reserved space on the right of the list for the save/discard/back action buttons. */
    private static final int ACTION_BUTTONS_RESERVE = 40;
    private int previewX, previewY, previewW, previewH;
    private boolean previewVisible;

    public StructurePickerScreen(SnapshotReturnContext context,
                                 Map<String, String> formSnapshot,
                                 String snapshotKey,
                                 @Nullable String initialValue) {
        super(null,
            "ponderer.ui.scope.editor",
            "ponderer.ui.structure_picker.title",
            UILayoutConstants.EDITOR_LIST_W);
        this.context = context;
        this.formSnapshot = new HashMap<>(formSnapshot);
        this.snapshotKey = snapshotKey;
        preselectFromInitialValue(initialValue);
    }

    private void preselectFromInitialValue(@Nullable String value) {
        if (value == null || value.isBlank()) return;
        String trimmed = value.trim();
        String prefix = "ponderer:";
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(prefix)) return;
        String relative = trimmed.substring(prefix.length());
        Path candidate = SafePaths.resolveRelativePath(SceneStore.getStructureDir(), relative + ".nbt");
        if (candidate != null && Files.exists(candidate)) {
            selectedSource = Source.PONDERER;
            selectedPath = candidate;
            selectedExternal = false;
            currentSource = Source.PONDERER;
        }
    }

    @Override
    protected void init() {
        rescanEntries();
        super.init();
        if (saveChanges != null) {
            saveChanges.withCallback(this::confirmSelection);
            saveChanges.getToolTip().clear();
            saveChanges.getToolTip().add(Component.translatable("ponderer.ui.confirm"));
        }
        if (discardChanges != null) {
            discardChanges.withCallback(this::clearSelection);
            discardChanges.getToolTip().clear();
            discardChanges.getToolTip().add(Component.translatable("ponderer.ui.structure_picker.clear_selection"));
        }
        layoutPreviewPanel();
        if (selectedPath != null && !selectedExternal && previewVisible) {
            preview.load(selectedPath);
        }
    }

    private void layoutPreviewPanel() {
        int listLeft = width / 2 - currentListWidthValue() / 2 + listHorizontalOffset();
        int availableLeft = listLeft - PREVIEW_GAP;
        int desiredWidth = PREVIEW_WIDTH;
        if (availableLeft < desiredWidth + PREVIEW_GAP) {
            previewVisible = false;
            preview.setBounds(0, 0, 0, 0);
            return;
        }
        previewVisible = true;
        previewW = desiredWidth;
        previewX = listLeft - PREVIEW_GAP - previewW;
        previewY = contentAreaTop();
        previewH = contentAreaHeight();
        preview.setBounds(previewX, previewY, previewW, previewH);
    }

    @Override
    protected int listHorizontalOffset() {
        int listW = currentListWidthValue();
        int composite = PREVIEW_WIDTH + PREVIEW_GAP + listW + ACTION_BUTTONS_RESERVE;
        if (composite > width - 20) {
            return 0;
        }
        return (PREVIEW_WIDTH + PREVIEW_GAP) / 2;
    }

    @Override
    public void resize(Minecraft client, int newWidth, int newHeight) {
        super.resize(client, newWidth, newHeight);
        layoutPreviewPanel();
    }

    @Override
    public void removed() {
        super.removed();
        preview.dispose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (previewVisible && preview.onMouseDown(mouseX, mouseY)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (preview.onMouseDrag(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = preview.onMouseUp();
        boolean superHandled = super.mouseReleased(mouseX, mouseY, button);
        return handled || superHandled;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
        if (previewVisible) {
            graphics.enableScissor(previewX, previewY, previewX + previewW, previewY + previewH);
            preview.render(graphics, partialTicks);
            graphics.disableScissor();
        }
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        List<ActionStripListEntry.ButtonModel> buttons = new ArrayList<>();
        buttons.add(ActionStripListEntry.button(
            () -> tabLabel("ponderer.ui.structure_picker.tab.ponderer", Source.PONDERER),
            () -> List.of(Component.translatable("ponderer.ui.structure_picker.tab.ponderer.tooltip")),
            () -> switchTab(Source.PONDERER),
            () -> currentSource == Source.PONDERER ? 0xFFE082 : 0xFFFFFF,
            () -> true));
        buttons.add(ActionStripListEntry.button(
            () -> tabLabel("ponderer.ui.structure_picker.tab.create", Source.CREATE),
            () -> List.of(Component.translatable("ponderer.ui.structure_picker.tab.create.tooltip")),
            () -> switchTab(Source.CREATE),
            () -> currentSource == Source.CREATE ? 0xFFE082 : 0xFFFFFF,
            () -> true));
        buttons.add(ActionStripListEntry.button(
            () -> UIText.of("ponderer.ui.structure_picker.tab.file_manager"),
            () -> List.of(Component.translatable("ponderer.ui.structure_picker.tab.file_manager.tooltip")),
            this::openFileManager,
            () -> 0xFFFFFF,
            () -> true));
        entries.add(new ActionStripListEntry(buttons));
    }

    private String tabLabel(String key, Source source) {
        String base = UIText.of(key);
        return currentSource == source ? "▶ " + base : base;
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (selectedExternal && selectedExternalName != null) {
            entries.add(new SectionHeaderListEntry(
                UIText.of("ponderer.ui.structure_picker.external_selected", selectedExternalName)));
        }

        if (currentEntries.isEmpty()) {
            entries.add(new SectionHeaderListEntry(
                UIText.of(currentSource == Source.PONDERER
                    ? "ponderer.ui.structure_picker.empty.ponderer"
                    : "ponderer.ui.structure_picker.empty.create")));
            return;
        }

        Path baseDir = currentBaseDir();
        for (Path file : currentEntries) {
            String relative = baseDir.relativize(file).toString().replace('\\', '/');
            boolean isSelected = !selectedExternal
                && selectedPath != null
                && selectedSource == currentSource
                && selectedPath.equals(file);
            entries.add(new FullButtonListEntry(
                () -> (isSelected ? "● " : "  ") + relative,
                null,
                () -> selectFromCurrentTab(file),
                () -> isSelected ? 0xFFE082 : 0xFFFFFF,
                () -> true));
        }
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return false;
    }

    @Override
    protected int getUnsavedChangeCount() {
        return 0;
    }

    @Override
    protected boolean saveEdits() {
        return confirmSelection();
    }

    @Override
    protected void discardEdits() {
        clearSelection();
    }

    @Override
    protected boolean isSaveButtonActive() {
        return selectedPath != null;
    }

    @Override
    protected boolean isDiscardButtonActive() {
        return selectedPath != null;
    }

    @Override
    protected void attemptBackToParent() {
        reopenEditorWithoutChange();
    }

    private void switchTab(Source source) {
        if (currentSource == source) return;
        currentSource = source;
        rescanEntries();
        rebuildListAtTop();
    }

    private void selectFromCurrentTab(Path file) {
        selectedSource = currentSource;
        selectedPath = file;
        selectedExternal = false;
        selectedExternalName = null;
        rebuildListPreservingScroll();
        triggerPreviewLoad(file);
    }

    private void clearSelection() {
        selectedPath = null;
        selectedExternal = false;
        selectedExternalName = null;
        preview.clear();
        rebuildListPreservingScroll();
    }

    private void triggerPreviewLoad(Path file) {
        if (!previewVisible) return;
        int count = StructurePreviewWidget.countBlocks(file);
        if (count <= 0) {
            preview.setStatus("ponderer.ui.structure_picker.preview.empty");
            return;
        }
        if (count > StructurePreviewWidget.DEFAULT_LARGE_THRESHOLD) {
            preview.setStatus("ponderer.ui.structure_picker.preview.confirm_pending");
            new ConfirmationScreen()
                .centered()
                .withText(Component.translatable("ponderer.ui.structure_picker.preview.large", count))
                .withAction(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        preview.load(file);
                    } else {
                        preview.setStatus("ponderer.ui.structure_picker.preview.skipped");
                    }
                })
                .open(this);
            return;
        }
        preview.load(file);
    }

    private boolean confirmSelection() {
        if (selectedPath == null) {
            return false;
        }
        String structureId = applySelectionToStructuresDir();
        if (structureId == null) {
            setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.copy_failed"));
            return false;
        }
        formSnapshot.put(snapshotKey, structureId);
        context.reopenEditor(formSnapshot);
        return true;
    }

    private void reopenEditorWithoutChange() {
        context.reopenEditor(formSnapshot);
    }

    @Nullable
    private String applySelectionToStructuresDir() {
        Path structuresDir = SceneStore.getStructureDir();
        Path selected = selectedPath;
        if (selected == null) return null;

        if (!selectedExternal && selectedSource == Source.PONDERER && selected.startsWith(structuresDir)) {
            String relative = structuresDir.relativize(selected).toString().replace('\\', '/');
            if (relative.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
                relative = relative.substring(0, relative.length() - 4);
            }
            return "ponderer:" + relative;
        }

        String fileName = selected.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        fileName = SafePaths.sanitizeWindowsFileName(fileName, "structure");
        Path target = SafePaths.resolveFileName(structuresDir, fileName + ".nbt");
        if (target == null) return null;
        try {
            Files.createDirectories(target.getParent());
            Files.copy(selected, target, StandardCopyOption.REPLACE_EXISTING);
            return "ponderer:" + fileName;
        } catch (IOException e) {
            LOGGER.warn("Failed to copy selected structure {} -> {}", selected, target, e);
            return null;
        }
    }

    private Path currentBaseDir() {
        return currentSource == Source.PONDERER ? SceneStore.getStructureDir() : createSchematicsDir();
    }

    private static Path createSchematicsDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
    }

    private void rescanEntries() {
        currentEntries.clear();
        Path baseDir = currentBaseDir();
        if (!Files.exists(baseDir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(baseDir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt"))
                .sorted(Comparator.comparing(p -> p.toString().toLowerCase(Locale.ROOT)))
                .forEach(currentEntries::add);
        } catch (IOException e) {
            LOGGER.warn("Failed to scan structure dir {}", baseDir, e);
        }
    }

    private void openFileManager() {
        Path defaultDir = currentBaseDir();
        CompletableFuture.supplyAsync(() -> {
            try {
                String defaultPath = Files.exists(defaultDir)
                    ? defaultDir.toAbsolutePath().toString() + java.io.File.separator
                    : null;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer filters = stack.mallocPointer(1);
                    filters.put(stack.UTF8("*.nbt"));
                    filters.flip();
                    return TinyFileDialogs.tinyfd_openFileDialog(
                        UIText.of("ponderer.ui.show_structure.browse"),
                        defaultPath,
                        filters,
                        "NBT files (*.nbt)",
                        false);
                }
            } catch (Exception e) {
                return null;
            }
        }).thenAcceptAsync(result -> {
            if (result == null) return;
            applyExternalSelection(Path.of(result));
        }, Minecraft.getInstance());
    }

    private void applyExternalSelection(Path picked) {
        Path structuresDir = SceneStore.getStructureDir();
        Path schematicsDir = createSchematicsDir();
        boolean wasExternal;
        if (picked.startsWith(structuresDir)) {
            selectedSource = Source.PONDERER;
            selectedPath = picked;
            selectedExternal = false;
            selectedExternalName = null;
            currentSource = Source.PONDERER;
            wasExternal = false;
        } else if (picked.startsWith(schematicsDir)) {
            selectedSource = Source.CREATE;
            selectedPath = picked;
            selectedExternal = false;
            selectedExternalName = null;
            currentSource = Source.CREATE;
            wasExternal = false;
        } else {
            selectedPath = picked;
            selectedExternal = true;
            selectedExternalName = picked.getFileName().toString();
            wasExternal = true;
        }
        rescanEntries();
        rebuildListAtTop();
        if (!wasExternal) {
            triggerPreviewLoad(picked);
        } else {
            // External file — still try to preview (read directly, no on-disk path normalization needed).
            triggerPreviewLoad(picked);
        }
    }
}
