package com.nododiiiii.ponderer.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.ai.AiSceneGenerator;
import com.nododiiiii.ponderer.ai.AiGenerationTask;
import com.nododiiiii.ponderer.ai.AiSceneAgentPipeline;
import com.nododiiiii.ponderer.ai.AiRunFiles;
import com.nododiiiii.ponderer.ai.StructureDescriber;
import com.nododiiiii.ponderer.ai.McmodApiClient;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.PackStateStore;
import com.nododiiiii.ponderer.ponder.SceneItemBindings;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ui.catnip.ActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.ui.catnip.EntryTextSupport;
import com.nododiiiii.ponderer.ui.catnip.FormBoxWidget;
import com.nododiiiii.ponderer.ui.catnip.PonderIconStencils;
import com.nododiiiii.ponderer.ui.catnip.PondererGuiIcons;
import com.nododiiiii.ponderer.util.SafePaths;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

public class AiGenerateScreen extends AbstractJeiAwareFormScreen {

    private static final Gson SCENE_GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();
    private static final List<Path> cachedStructurePaths = new ArrayList<>();
    private static final List<StructureDescriber.StructureInfo> cachedStructureInfos = new ArrayList<>();
    private static int cachedStructureIndex = 0;
    private static String cachedCarrier = "";
    private static String cachedPrompt = "";
    private static String cachedExistingSceneKey = "";
    private static final ReferenceUrlManager referenceUrlManager = new ReferenceUrlManager();
    private static boolean cachedBuildTutorial = false;
    private static boolean cachedIncludeImages = false;
    @Nullable
    private static String cachedStatusMessage = null;
    private static boolean cachedStatusIsError = false;
    private static boolean cachedGenerating = false;
    private static boolean cachedCancelling = false;
    @Nullable
    private static AiGenerationTask cachedGenerationTask;

    private static final int PREVIEW_HEIGHT = UILayoutConstants.LIST_ENTRY_H * 2;
    private static final int NAV_BUTTON_SIZE = 26;
    private static final int HEADER_TITLE_COLOR = 0xFFCCCC77;
    private static final int HEADER_TEXT_COLOR = 0xFFFFFFFF;
    private static final int HEADER_MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private final StructurePreviewWidget preview = new StructurePreviewWidget(0, 0, 0, 0);
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private boolean showingStopAction;

    @Nullable
    private Path loadedPreviewPath;
    @Nullable
    private CompletableFuture<StructureDescriber.StructureInfo> pendingStructureInfo;

    public AiGenerateScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.ai_generate.title",
            UILayoutConstants.EDITOR_LIST_W);
    }

    @Override
    protected void init() {
        super.init();
        useConfirmationActionIcons();
        applyCachedStatus();
        refreshGenerationActionButton(true);
        if (!cachedStructurePaths.isEmpty()) {
            loadPreviewForCurrent();
        } else {
            preview.clear();
            loadedPreviewPath = null;
        }
    }

    @Override
    public void resize(Minecraft client, int newWidth, int newHeight) {
        super.resize(client, newWidth, newHeight);
    }

    @Override
    public void removed() {
        super.removed();
        if (pendingStructureInfo != null) {
            pendingStructureInfo.cancel(true);
            pendingStructureInfo = null;
        }
        preview.dispose();
        loadedPreviewPath = null;
    }

    @Override
    public void tick() {
        super.tick();
        applyCachedStatus();
        refreshGenerationActionButton();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasPreviewBounds() && preview.onMouseDown(mouseX, mouseY)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (hasPreviewBounds() && preview.onMouseDrag(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean previewHandled = preview.onMouseUp();
        boolean superHandled = super.mouseReleased(mouseX, mouseY, button);
        return previewHandled || superHandled;
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new PreviewHeaderEntry());
        entries.add(new SpacerHeaderEntry());
        entries.add(new StructureNavigationHeaderEntry());
        entries.add(new ActionStripListEntry(List.of(
            ActionStripListEntry.button(
                () -> UIText.of("ponderer.ui.ai_generate.add"),
                () -> List.of(Component.translatable("ponderer.ui.ai_generate.add.tooltip")),
                this::openStructurePicker,
                () -> HEADER_TEXT_COLOR,
                () -> true),
            ActionStripListEntry.button(
                () -> UIText.of("ponderer.ui.ai_generate.delete"),
                () -> List.of(Component.translatable("ponderer.ui.ai_generate.delete.tooltip")),
                this::deleteStructure,
                () -> HEADER_TEXT_COLOR,
                this::hasStructures))));
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> cachedExistingSceneKey, value -> cachedExistingSceneKey = value),
            "ponderer.ui.ai_generate.existing_scene",
            "ponderer.ui.ai_generate.existing_scene.tooltip",
            "ponderer.ui.ai_generate.existing_scene.hint",
            -1,
            entry -> entry.field().setEditable(false),
            FieldDecorators.sceneSelector(this::openExistingScenePicker),
            FieldDecorators.iconAction(PondererGuiIcons.REMOVE,
                UIText.of("ponderer.ui.ai_generate.existing_scene.clear"), () -> {
                    cachedExistingSceneKey = "";
                    setCachedStatus(null, false);
                    rebuildListPreservingScroll();
                })));

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
        List<Boolean> urlAutoAdded = referenceUrlManager.getUrlAutoAdded();
        for (int i = 0; i < urlValues.size(); i++) {
            final int index = i;
            final boolean isAutoAdded = index < urlAutoAdded.size() && urlAutoAdded.get(index);
            String labelKey = isAutoAdded
                ? "ponderer.ui.ai_generate.url.mcmod"
                : "ponderer.ui.ai_generate.urls";
            entries.add(FieldSpecs.text(
                FieldBindings.transientString(
                    () -> referenceUrlManager.getUrlValues().get(index),
                    value -> referenceUrlManager.updateUrl(index, value)),
                labelKey,
                null,
                "ponderer.ui.ai_generate.url.hint",
                -1,
                entry -> {
                    entry.field().setMaxLength(512);
                    if (entry.field() instanceof com.nododiiiii.ponderer.ui.catnip.ClippedConfigTextField textField) {
                        textField.setConsumeRightClick(true);
                    }
                    if (isAutoAdded) {
                        entry.field().setEditable(false);
                        entry.field().setCanLoseFocus(true);
                    }
                },
                FieldDecorators.iconAction(PondererGuiIcons.REMOVE,
                    UIText.of("ponderer.ui.ai_generate.remove_url.tooltip"), () -> removeUrl(index))));
        }
        entries.add(FieldSpecs.fullButton(
            UIText.of("ponderer.ui.ai_generate.add_url"),
            UIText.of("ponderer.ui.ai_generate.add_url.tooltip"),
            this::addUrl));

        entries.add(FieldSpecs.fullButton(UIText.of("ponderer.ui.ai_runs.title"), null, () -> {
            if (!cachedGenerating) Minecraft.getInstance().setScreen(new AiRunHistoryScreen(this, this::resumeGeneration));
        }));
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
        if (cachedGenerating) {
            return cancelGeneration();
        }
        return doGenerate();
    }

    @Override
    protected boolean isSaveButtonActive() {
        return cachedGenerating ? !cachedCancelling : super.isSaveButtonActive();
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
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("structure_index", String.valueOf(cachedStructureIndex));
        snapshot.put("structure_count", String.valueOf(cachedStructurePaths.size()));
        for (int i = 0; i < cachedStructurePaths.size(); i++) {
            snapshot.put("structure_" + i, cachedStructurePaths.get(i).toString());
        }
        snapshot.put("carrier", cachedCarrier);
        snapshot.put("prompt", cachedPrompt);
        snapshot.put("existing_scene", cachedExistingSceneKey);
        snapshot.put("build_tutorial", String.valueOf(cachedBuildTutorial));
        snapshot.put("include_images", String.valueOf(cachedIncludeImages));
        referenceUrlManager.snapshot(snapshot);
        return snapshot;
    }

    private boolean hasStructures() {
        return !cachedStructurePaths.isEmpty();
    }

    private void openExistingScenePicker() {
        if (SceneRuntime.getScenes().isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.function_page.no_scenes"));
            return;
        }
        Minecraft.getInstance().setScreen(new PonderItemGridScreen(this,
            sceneKey -> {
                Minecraft.getInstance().setScreen(this);
                DslScene scene = SceneRuntime.findByKey(sceneKey);
                if (scene == null || !canAdjustScene(scene)) {
                    return;
                }
                cachedExistingSceneKey = sceneKey;
                ResourceLocation carrier = SceneItemBindings.firstItem(scene);
                if (carrier != null) {
                    cachedCarrier = carrier.toString();
                }
                referenceUrlManager.removeAutoUrlsForItem();
                cachedStructurePaths.clear();
                cachedStructureInfos.clear();
                cachedStructureIndex = 0;
                loadedPreviewPath = null;
                pendingStructureInfo = null;
                preview.clear();
                setCachedStatus(null, false);
                rebuildListPreservingScroll();
            },
            () -> Minecraft.getInstance().setScreen(this)));
    }

    private boolean canAdjustScene(DslScene scene) {
        if (!SceneEditorScreen.canModifyScene(scene)) {
            setErrorMessage(UIText.of("ponderer.ui.readonly_scene.message"));
            return false;
        }
        PackStateStore.load();
        if (scene.pack != null && !scene.pack.isBlank() && !PackStateStore.isImported(scene.pack)) {
            setErrorMessage(UIText.of("ponderer.ui.readonly_import_prompt.message"));
            return false;
        }
        return true;
    }

    private boolean canTurnStructures() {
        return cachedStructurePaths.size() > 1;
    }

    private boolean hasPreviewBounds() {
        return previewW > 0 && previewH > 0;
    }

    private void openStructurePicker() {
        Minecraft.getInstance().setScreen(new StructurePickerScreen(
            this::reopenFromStructurePicker,
            snapshotState(),
            "ai_generate_structure",
            currentStructureReference()));
    }

    private void reopenFromStructurePicker(Map<String, String> snapshot) {
        Minecraft.getInstance().setScreen(this);
        String selected = snapshot.get("ai_generate_structure");
        if (selected != null && !selected.isBlank()) {
            addStructureFromPicker(selected);
        }
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

        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        preview.setStatus("ponderer.ui.structure_picker.preview.loading");
        loadedPreviewPath = null;
        CompletableFuture<StructureDescriber.StructureInfo> request = CompletableFuture.supplyAsync(() -> {
            try {
                return StructureDescriber.describe(path);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Util.ioPool());
        pendingStructureInfo = request;
        request.whenCompleteAsync((info, error) -> {
            if (pendingStructureInfo != request || mc.screen != this || mc.level != level) return;
            pendingStructureInfo = null;
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                setCachedStatus("Failed to parse NBT: " + cause.getMessage(), true);
                preview.setStatus("ponderer.ui.structure_picker.preview.load_failed");
                return;
            }
            cachedStructurePaths.add(path);
            cachedStructureInfos.add(info);
            cachedStructureIndex = cachedStructurePaths.size() - 1;
            setCachedStatus(null, false);
            loadPreviewForCurrent();
            rebuildListPreservingScroll();
        }, mc);
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
        rebuildListPreservingScroll();
    }

    private void selectPreviousStructure() {
        if (!canTurnStructures()) {
            return;
        }
        int target = cachedStructureIndex - 1;
        if (target < 0) {
            target = cachedStructurePaths.size() - 1;
        }
        selectStructure(target);
    }

    private void selectNextStructure() {
        if (!canTurnStructures()) {
            return;
        }
        selectStructure((cachedStructureIndex + 1) % cachedStructurePaths.size());
    }

    private void selectStructure(int index) {
        if (index < 0 || index >= cachedStructurePaths.size()) {
            return;
        }
        if (index == cachedStructureIndex && cachedStructurePaths.get(index).equals(loadedPreviewPath)) {
            return;
        }
        cachedStructureIndex = index;
        loadPreviewForCurrent();
        rebuildListPreservingScroll();
    }

    private void loadPreviewForCurrent() {
        if (cachedStructurePaths.isEmpty()) {
            preview.clear();
            loadedPreviewPath = null;
            return;
        }
        Path file = cachedStructurePaths.get(cachedStructureIndex);
        preview.load(file, this);
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
        if (!itemId.equals(cachedCarrier)) {
            return;
        }
        referenceUrlManager.removeAutoUrlsForItem();
        if (url != null && !url.isBlank()) {
            referenceUrlManager.addUrl(url, itemId, true);
        }
        if (Minecraft.getInstance().screen == this) {
            rebuildListPreservingScroll();
        }
    }

    @Override
    public void onJeiItemDropped(HintableTextFieldWidget field, ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        CompletableFuture.supplyAsync(() -> McmodApiClient.getItemUrl(itemId))
            .exceptionally(error -> Optional.empty())
            .thenAcceptAsync(url -> updateAutoUrl(url.orElse(null), itemId), Minecraft.getInstance());
    }

    private boolean doGenerate() {
        if (cachedGenerating) {
            return false;
        }
        DslScene existingScene = null;
        if (!cachedExistingSceneKey.isBlank()) {
            existingScene = SceneRuntime.findByKey(cachedExistingSceneKey);
            if (existingScene == null) {
                setErrorMessage(UIText.of("ponderer.cmd.scene_not_found", cachedExistingSceneKey));
                return false;
            }
            if (!canAdjustScene(existingScene)) {
                return false;
            }
        }
        if (cachedStructurePaths.isEmpty() && existingScene == null) {
            setErrorMessage(UIText.of("ponderer.ui.ai_generate.error.no_structure"));
            return false;
        }
        if (cachedCarrier.trim().isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.ai_generate.error.no_carrier"));
            return false;
        }
        ResourceLocation carrierId = ResourceLocation.tryParse(cachedCarrier.trim());
        if (carrierId == null || !BuiltInRegistries.ITEM.containsKey(carrierId)
            || BuiltInRegistries.ITEM.get(carrierId) == net.minecraft.world.item.Items.AIR) {
            setErrorMessage(UIText.of("ponderer.ui.ai_generate.error.invalid_carrier"));
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

        return beginGeneration(new AiSceneAgentPipeline.Request(new ArrayList<>(cachedStructurePaths), carrierId.toString(),
            cachedPrompt.trim(), urls, existingScene == null ? null : SCENE_GSON.toJson(existingScene), cachedBuildTutorial, cachedIncludeImages), null);
    }

    private void resumeGeneration(AiRunFiles files, String clarification) {
        if (cachedGenerating) return;
        try {
            AiSceneAgentPipeline.Request saved = files.recoveryRequest();
            String prompt = saved.userPrompt() + (clarification.isBlank() ? "" : "\nUser clarification for recovery:\n" + clarification.trim());
            AiSceneAgentPipeline.Request request = new AiSceneAgentPipeline.Request(saved.structurePaths(), saved.carrierItemId(), prompt,
                saved.referenceUrls(), saved.existingJson(), saved.buildTutorial(), saved.includeImages(), saved.resumeDraft(), saved.resumeScope());
            Minecraft.getInstance().setScreen(this);
            beginGeneration(request, files);
        } catch (RuntimeException error) { cachedGenerating = false; cachedCancelling = false; setCachedStatus(error.getMessage(), true); }
    }

    private boolean beginGeneration(AiSceneAgentPipeline.Request request, @Nullable AiRunFiles recovery) {
        cachedGenerating = true;
        cachedCancelling = false;
        setCachedStatus(UIText.of("ponderer.ui.ai_generate.status.generating"), false);
        applyCachedStatus();
        refreshGenerationActionButton();

        AtomicReference<AiGenerationTask> taskRef = new AtomicReference<>();
        AiGenerationTask task = AiSceneGenerator.startRequest(request, recovery,
            filePath -> Minecraft.getInstance().execute(() -> {
                if (!isCurrentTask(taskRef.get())) {
                    return;
                }
                cachedGenerating = false;
                cachedCancelling = false;
                cachedGenerationTask = null;
                setCachedStatus(UIText.of("ponderer.ui.ai_generate.status.success") + " " + UIText.of("ponderer.ui.ai_runs.review_hint"), false);
                applyCachedStatus();
                refreshGenerationActionButton();
            }),
            error -> Minecraft.getInstance().execute(() -> {
                if (!isCurrentTask(taskRef.get())) {
                    return;
                }
                cachedGenerating = false;
                cachedCancelling = false;
                cachedGenerationTask = null;
                setCachedStatus(error, true);
                applyCachedStatus();
                refreshGenerationActionButton();
            }),
            () -> Minecraft.getInstance().execute(() -> {
                if (!isCurrentTask(taskRef.get())) {
                    return;
                }
                cachedGenerating = false;
                cachedCancelling = false;
                cachedGenerationTask = null;
                setCachedStatus(UIText.of("ponderer.ui.ai_generate.status.cancelled"), false);
                applyCachedStatus();
                refreshGenerationActionButton();
            }),
            statusMsg -> Minecraft.getInstance().execute(() -> {
                if (!isCurrentTask(taskRef.get()) || cachedCancelling) {
                    return;
                }
                setCachedStatus(statusMsg, false);
                applyCachedStatus();
            }));
        taskRef.set(task);
        cachedGenerationTask = task;
        return true;
    }

    private boolean cancelGeneration() {
        AiGenerationTask task = cachedGenerationTask;
        if (!cachedGenerating || cachedCancelling || task == null) {
            return false;
        }
        cachedCancelling = true;
        setCachedStatus(UIText.of("ponderer.ui.ai_generate.status.cancelling"), false);
        applyCachedStatus();
        refreshGenerationActionButton();
        task.cancel();
        return true;
    }

    private static boolean isCurrentTask(@Nullable AiGenerationTask task) {
        return task == null
            ? cachedGenerationTask == null && cachedGenerating
            : cachedGenerationTask == task;
    }

    private void refreshGenerationActionButton() {
        refreshGenerationActionButton(false);
    }

    private void refreshGenerationActionButton(boolean force) {
        if (saveChanges == null) {
            return;
        }
        boolean shouldShowStop = cachedGenerating;
        if (!force && showingStopAction == shouldShowStop) {
            return;
        }
        showingStopAction = shouldShowStop;
        PonderGuiTextures texture = shouldShowStop
            ? PonderGuiTextures.ICON_DISABLE
            : PonderGuiTextures.ICON_CONFIRM;
        saveChanges.showingElement(texture.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(saveChanges)));
        setActionButtonTooltip(saveChanges, shouldShowStop ? "ponderer.ui.cancel" : "ponderer.ui.confirm");
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
        cachedExistingSceneKey = snapshot.getOrDefault("existing_scene", "");
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

    private class PreviewHeaderEntry extends ConfigScreenList.Entry {
        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            previewX = EntryTextSupport.contentLeft(x);
            previewY = y + 2;
            previewW = EntryTextSupport.contentWidth(width);
            previewH = Math.max(0, PREVIEW_HEIGHT - 4);
            preview.setShowDefaultPlaceholder(!hasStructures());
            preview.setBounds(previewX, previewY, previewW, previewH);

            if (previewW > 0 && previewH > 0) {
                graphics.enableScissor(previewX, previewY, previewX + previewW, previewY + previewH);
                preview.render(graphics, partialTicks);
                graphics.disableScissor();
            }

            if (!hasStructures()) {
                var font = Minecraft.getInstance().font;
                graphics.drawCenteredString(font,
                    UIText.of("ponderer.ui.ai_generate.preview_title"),
                    x + width / 2,
                    y + 6,
                    HEADER_TITLE_COLOR);
            }
        }

        @Override
        public Component getNarration() {
            return CommonComponents.EMPTY;
        }
    }

    private class StructureNavigationHeaderEntry extends ConfigScreenList.Entry {
        private final BoxWidget previousButton = createNavButton(
            PonderGuiTextures.ICON_PONDER_LEFT,
            AiGenerateScreen.this::selectPreviousStructure,
            "ponderer.ui.ai_generate.prev.tooltip");
        private final BoxWidget nextButton = createNavButton(
            PonderGuiTextures.ICON_PONDER_RIGHT,
            AiGenerateScreen.this::selectNextStructure,
            "ponderer.ui.ai_generate.next.tooltip");

        private StructureNavigationHeaderEntry() {
            listeners.add(previousButton);
            listeners.add(nextButton);
        }

        @Override
        public void tick() {
            super.tick();
            syncButton(previousButton);
            syncButton(nextButton);
            previousButton.tick();
            nextButton.tick();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            int buttonY = y + Math.max(0, (height - NAV_BUTTON_SIZE) / 2);
            layoutButton(previousButton, EntryTextSupport.contentLeft(x), buttonY);
            layoutButton(nextButton, EntryTextSupport.contentRight(x, width) - NAV_BUTTON_SIZE, buttonY);

            previousButton.render(graphics, mouseX, mouseY, partialTicks);
            nextButton.render(graphics, mouseX, mouseY, partialTicks);

            var font = Minecraft.getInstance().font;
            int textWidth = Math.max(20, nextButton.getX()
                - previousButton.getX() - NAV_BUTTON_SIZE
                - 2 * EntryTextSupport.rightControlGap());
            String structureName = font.plainSubstrByWidth(currentStructureName(), textWidth);
            int nameColor = hasStructures() ? HEADER_TEXT_COLOR : HEADER_MUTED_TEXT_COLOR;
            graphics.drawCenteredString(font, structureName, x + width / 2, y + 8, nameColor);
            graphics.drawCenteredString(font, currentStructureCounter(), x + width / 2, y + 22, HEADER_TITLE_COLOR);
        }

        @Override
        public Component getNarration() {
            return CommonComponents.EMPTY;
        }

        private void layoutButton(BoxWidget button, int x, int y) {
            button.setX(x);
            button.setY(y);
            button.setWidth(NAV_BUTTON_SIZE);
            button.setHeight(NAV_BUTTON_SIZE);
            syncButton(button);
        }

        private void syncButton(BoxWidget button) {
            button.active = canTurnStructures();
        }
    }

    private static BoxWidget createNavButton(PonderGuiTextures texture, Runnable callback, String tooltipKey) {
        BoxWidget button = new FormBoxWidget(0, 0, NAV_BUTTON_SIZE, NAV_BUTTON_SIZE)
            .withPadding(2, 2)
            .withCallback(callback);
        button.showingElement(PonderIconStencils.centered(texture));
        button.getToolTip().add(Component.translatable(tooltipKey));
        return button;
    }
}
