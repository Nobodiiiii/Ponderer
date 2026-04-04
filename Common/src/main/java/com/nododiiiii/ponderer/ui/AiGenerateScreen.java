package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ai.AiSceneGenerator;
import com.nododiiiii.ponderer.ai.StructureDescriber;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import com.nododiiiii.ponderer.ui.catnip.ToggleListEntry;
import com.nododiiiii.ponderer.util.SafePaths;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class AiGenerateScreen extends AbstractDeclarativeListScreen implements JeiAwareScreen {

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

    private final List<Path> baselineStructurePaths = new ArrayList<>();
    private final List<StructureDescriber.StructureInfo> baselineStructureInfos = new ArrayList<>();
    private final List<String> baselineUrls = new ArrayList<>();
    private final List<Boolean> baselineUrlAutoAdded = new ArrayList<>();
    private int baselineStructureIndex = 0;
    private String baselineCarrier = "";
    private String baselinePrompt = "";
    private boolean baselineBuildTutorial = false;
    private boolean baselineIncludeImages = false;
    private boolean initialSnapshotCaptured = false;

    private boolean jeiActive = false;
    @Nullable
    private HintableTextFieldWidget carrierField = null;

    public AiGenerateScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.ai_generate.title", 420);
    }

    @Override
    protected void init() {
        super.init();
        applyCachedStatus();
        if (!initialSnapshotCaptured) {
            captureBaseline();
            initialSnapshotCaptured = true;
        }
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new SectionHeaderListEntry(this::structureSummaryLine));
        entries.add(new SectionHeaderListEntry(this::structureDetailsLine));
        entries.add(new FullButtonListEntry(UIText.of("ponderer.ui.ai_generate.add"),
            UIText.of("ponderer.ui.ai_generate.add.tooltip"), this::addStructure));

        if (!cachedStructurePaths.isEmpty()) {
            entries.add(new FullButtonListEntry(UIText.of("ponderer.ui.ai_generate.delete"),
                UIText.of("ponderer.ui.ai_generate.delete.tooltip"), this::deleteStructure));
            entries.add(new FullButtonListEntry("< " + UIText.of("ponderer.ui.ai_generate.prev.tooltip"),
                UIText.of("ponderer.ui.ai_generate.prev.tooltip"), this::prevStructure));
            entries.add(new FullButtonListEntry(UIText.of("ponderer.ui.ai_generate.next.tooltip") + " >",
                UIText.of("ponderer.ui.ai_generate.next.tooltip"), this::nextStructure));
        }

        PlainTextListEntry carrierEntry = new PlainTextListEntry(
            "ponderer.ui.ai_generate.carrier",
            null,
            "ponderer.ui.ai_generate.carrier.hint",
            cachedCarrier,
            value -> cachedCarrier = value);
        carrierEntry.field().setMaxLength(128);
        if (JeiCompat.isAvailable()) {
            carrierEntry.addTrailingButton(
                20,
                this::toggleJei,
                () -> "J",
                () -> jeiActive ? 0x55FF55 : 0xAAAAFF,
                UIText.of("ponderer.ui.jei_browse.tooltip"));
        }
        entries.add(carrierEntry);
        carrierField = carrierEntry.field();

        PlainTextListEntry promptEntry = new PlainTextListEntry(
            "ponderer.ui.ai_generate.prompt",
            null,
            "ponderer.ui.ai_generate.prompt.hint",
            cachedPrompt,
            value -> cachedPrompt = value);
        promptEntry.field().setMaxLength(2048);
        entries.add(promptEntry);

        entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.ai_generate.urls")));
        List<String> urlValues = referenceUrlManager.getUrlValues();
        for (int i = 0; i < urlValues.size(); i++) {
            final int index = i;
            PlainTextListEntry urlEntry = new PlainTextListEntry(
                i == 0 ? "ponderer.ui.ai_generate.urls" : "",
                null,
                "ponderer.ui.ai_generate.url.hint",
                urlValues.get(i),
                value -> referenceUrlManager.updateUrl(index, value));
            urlEntry.field().setMaxLength(512);
            urlEntry.addTrailingButton(20, () -> removeUrl(index), () -> "-", () -> 0xFF6666, null);
            entries.add(urlEntry);
        }
        entries.add(new FullButtonListEntry(
            UIText.of("ponderer.ui.ai_generate.add_url"),
            UIText.of("ponderer.ui.ai_generate.add_url.tooltip"),
            this::addUrl));

        entries.add(new ToggleListEntry(
            "ponderer.ui.ai_generate.build_tutorial",
            "ponderer.ui.ai_generate.build_tutorial.tooltip",
            () -> cachedBuildTutorial,
            this::toggleBuildTutorial));
        entries.add(new ToggleListEntry(
            "ponderer.ui.ai_generate.include_images",
            "ponderer.ui.ai_generate.include_images.tooltip",
            () -> cachedIncludeImages,
            this::toggleIncludeImages));
    }

    @Override
    protected boolean hasUnsavedChanges() {
        if (!initialSnapshotCaptured) {
            return false;
        }
        return baselineStructureIndex != cachedStructureIndex
            || !baselineStructurePaths.equals(cachedStructurePaths)
            || !Objects.equals(baselineCarrier, cachedCarrier)
            || !Objects.equals(baselinePrompt, cachedPrompt)
            || !baselineUrls.equals(referenceUrlManager.getUrlValues())
            || baselineBuildTutorial != cachedBuildTutorial
            || baselineIncludeImages != cachedIncludeImages;
    }

    @Override
    protected int getUnsavedChangeCount() {
        int dirty = 0;
        if (baselineStructureIndex != cachedStructureIndex || !baselineStructurePaths.equals(cachedStructurePaths)) {
            dirty++;
        }
        if (!Objects.equals(baselineCarrier, cachedCarrier)) {
            dirty++;
        }
        if (!Objects.equals(baselinePrompt, cachedPrompt)) {
            dirty++;
        }
        if (!baselineUrls.equals(referenceUrlManager.getUrlValues())) {
            dirty++;
        }
        if (baselineBuildTutorial != cachedBuildTutorial) {
            dirty++;
        }
        if (baselineIncludeImages != cachedIncludeImages) {
            dirty++;
        }
        return dirty;
    }

    @Override
    protected boolean saveEdits() {
        return doGenerate();
    }

    @Override
    protected void discardEdits() {
        cachedStructurePaths.clear();
        cachedStructurePaths.addAll(baselineStructurePaths);
        cachedStructureInfos.clear();
        cachedStructureInfos.addAll(baselineStructureInfos);
        cachedStructureIndex = baselineStructureIndex;
        cachedCarrier = baselineCarrier;
        cachedPrompt = baselinePrompt;
        referenceUrlManager.replaceWith(List.copyOf(baselineUrls), List.copyOf(baselineUrlAutoAdded));
        cachedBuildTutorial = baselineBuildTutorial;
        cachedIncludeImages = baselineIncludeImages;
        applyCachedStatus();
        rebuildEntries(currentListScroll());
    }

    @Override
    protected int getEntryHeight() {
        return 40;
    }

    @Override
    @Nullable
    public HintableTextFieldWidget getJeiTargetField() {
        return carrierField;
    }

    @Override
    public void deactivateJei() {
        jeiActive = false;
        JeiCompat.clearActiveEditor();
        if (list != null) {
            rebuildEntries(currentListScroll());
        }
    }

    @Override
    public void showJeiIncompatibleWarning(IdFieldMode mode) {
        setErrorMessage(switch (mode) {
            case BLOCK -> UIText.of("ponderer.ui.jei.error.not_block");
            case ENTITY -> UIText.of("ponderer.ui.jei.error.not_spawn_egg");
            case ITEM, INGREDIENT -> null;
        });
    }

    @Override
    public int getGuiLeft() {
        return width / 2 - currentListWidthValue() / 2 - 40;
    }

    @Override
    public int getGuiTop() {
        return 35;
    }

    @Override
    public int getGuiWidth() {
        return currentListWidthValue() + 80;
    }

    @Override
    public int getGuiHeight() {
        return height - 60;
    }

    @Override
    public void removed() {
        super.removed();
        if (jeiActive) {
            deactivateJei();
        }
    }

    private void toggleJei() {
        if (!JeiCompat.isAvailable()) {
            return;
        }
        if (jeiActive) {
            deactivateJei();
            return;
        }
        jeiActive = true;
        JeiCompat.setActiveScreen(this, IdFieldMode.ITEM);
        rebuildEntries(currentListScroll());
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

    private void captureBaseline() {
        baselineStructurePaths.clear();
        baselineStructurePaths.addAll(cachedStructurePaths);
        baselineStructureInfos.clear();
        baselineStructureInfos.addAll(cachedStructureInfos);
        baselineStructureIndex = cachedStructureIndex;
        baselineCarrier = cachedCarrier;
        baselinePrompt = cachedPrompt;
        baselineUrls.clear();
        baselineUrls.addAll(referenceUrlManager.getUrlValues());
        baselineUrlAutoAdded.clear();
        baselineUrlAutoAdded.addAll(referenceUrlManager.getUrlAutoAdded());
        baselineBuildTutorial = cachedBuildTutorial;
        baselineIncludeImages = cachedIncludeImages;
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
