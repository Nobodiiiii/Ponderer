package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ai.AiSceneGenerator;
import com.nododiiiii.ponderer.ai.StructureDescriber;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    public AiGenerateScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.ai_generate.title",
            UILayoutConstants.EDITOR_LIST_W, JeiCompat::setActiveScreen);
    }

    @Override
    protected void init() {
        super.init();
        applyCachedStatus();
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.sectionHeader(this::structureSummaryLine));
        entries.add(FieldSpecs.sectionHeader(this::structureDetailsLine));
        entries.add(FieldSpecs.fullButton(
            UIText.of("ponderer.ui.ai_generate.add"),
            UIText.of("ponderer.ui.ai_generate.add.tooltip"),
            this::addStructure));

        if (!cachedStructurePaths.isEmpty()) {
            entries.add(FieldSpecs.fullButton(
                UIText.of("ponderer.ui.ai_generate.delete"),
                UIText.of("ponderer.ui.ai_generate.delete.tooltip"),
                this::deleteStructure));
            entries.add(FieldSpecs.fullButton(
                "< " + UIText.of("ponderer.ui.ai_generate.prev.tooltip"),
                UIText.of("ponderer.ui.ai_generate.prev.tooltip"),
                this::prevStructure));
            entries.add(FieldSpecs.fullButton(
                UIText.of("ponderer.ui.ai_generate.next.tooltip") + " >",
                UIText.of("ponderer.ui.ai_generate.next.tooltip"),
                this::nextStructure));
        }

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

    private void addStructure() {
        Path structuresDir = SceneStore.getStructureDir();
        CompletableFuture.supplyAsync(() -> {
            try {
                String defaultPath = Files.exists(structuresDir)
                    ? structuresDir.toAbsolutePath() + java.io.File.separator
                    : null;
                MemoryStack stack = MemoryStack.stackPush();
                try {
                    PointerBuffer filters = stack.mallocPointer(1);
                    filters.put(stack.UTF8("*.nbt"));
                    filters.flip();
                    return TinyFileDialogs.tinyfd_openFileDialog(
                        UIText.of("ponderer.ui.ai_generate.select_nbt"),
                        defaultPath,
                        filters,
                        "NBT files (*.nbt)",
                        false);
                } finally {
                    stack.pop();
                }
            } catch (Exception e) {
                return null;
            }
        }).thenAcceptAsync(result -> {
            if (result == null) {
                return;
            }
            Path selected = Path.of(result);

            Path target;
            if (selected.startsWith(structuresDir)) {
                target = selected;
            } else {
                String fileName = selected.getFileName().toString();
                if (fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".nbt")) {
                    fileName = SafePaths.sanitizeWindowsFileName(fileName.substring(0, fileName.length() - 4),
                        "structure") + ".nbt";
                } else {
                    fileName = SafePaths.sanitizeWindowsFileName(fileName, "structure.nbt");
                }
                target = SafePaths.resolveFileName(structuresDir, fileName);
                if (target == null) {
                    setCachedStatus("Failed to copy: invalid target filename", true);
                    refreshCurrentScreen();
                    return;
                }
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(selected, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    setCachedStatus("Failed to copy: " + e.getMessage(), true);
                    refreshCurrentScreen();
                    return;
                }
            }

            try {
                StructureDescriber.StructureInfo info = StructureDescriber.describe(target);
                cachedStructurePaths.add(target);
                cachedStructureInfos.add(info);
                cachedStructureIndex = cachedStructurePaths.size() - 1;
                setCachedStatus(null, false);
                refreshCurrentScreen();
            } catch (Exception e) {
                setCachedStatus("Failed to parse NBT: " + e.getMessage(), true);
                refreshCurrentScreen();
            }
        }, Minecraft.getInstance());
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
        rebuildEntries(currentListScroll());
    }

    private void prevStructure() {
        if (cachedStructurePaths.size() <= 1) {
            return;
        }
        cachedStructureIndex = (cachedStructureIndex - 1 + cachedStructurePaths.size()) % cachedStructurePaths.size();
        rebuildEntries(currentListScroll());
    }

    private void nextStructure() {
        if (cachedStructurePaths.size() <= 1) {
            return;
        }
        cachedStructureIndex = (cachedStructureIndex + 1) % cachedStructurePaths.size();
        rebuildEntries(currentListScroll());
    }

    private void addUrl() {
        referenceUrlManager.addManualUrl("");
        rebuildEntries(currentListScroll());
    }

    private void removeUrl(int index) {
        referenceUrlManager.removeUrl(index);
        rebuildEntries(currentListScroll());
    }

    private void toggleBuildTutorial() {
        cachedBuildTutorial = !cachedBuildTutorial;
        rebuildEntries(currentListScroll());
    }

    private void toggleIncludeImages() {
        cachedIncludeImages = !cachedIncludeImages;
        rebuildEntries(currentListScroll());
    }

    public void updateAutoUrl(@Nullable String url, String itemId) {
        referenceUrlManager.removeAutoUrlsForItem();
        if (url != null && !url.isBlank()) {
            referenceUrlManager.addUrl(url, itemId, true);
        }
        if (Minecraft.getInstance().screen == this) {
            rebuildEntries(currentListScroll());
        }
    }

    private boolean doGenerate() {
        if (cachedGenerating) {
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
                rebuildEntries(currentListScroll());
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
    }

    private static StructureDescriber.StructureInfo describeStructureSafe(Path path) {
        try {
            return StructureDescriber.describe(path);
        } catch (Exception ignored) {
            return new StructureDescriber.StructureInfo(0, 0, 0, "", List.of());
        }
    }

    private String structureSummaryLine() {
        if (cachedStructurePaths.isEmpty()) {
            return UIText.of("ponderer.ui.ai_generate.no_structure");
        }
        String fileName = cachedStructurePaths.get(cachedStructureIndex).getFileName().toString();
        return (cachedStructureIndex + 1) + "/" + cachedStructurePaths.size() + " - " + fileName;
    }

    private String structureDetailsLine() {
        if (cachedStructurePaths.isEmpty()) {
            return "";
        }
        StructureDescriber.StructureInfo info = cachedStructureInfos.get(cachedStructureIndex);
        String blockTypes = String.join(", ", info.blockTypes());
        if (blockTypes.length() > 64) {
            blockTypes = blockTypes.substring(0, 61) + "...";
        }
        return info.sizeX() + " x " + info.sizeY() + " x " + info.sizeZ() + " | " + blockTypes;
    }
}
