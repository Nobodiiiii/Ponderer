package com.nododiiiii.ponderer.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.ai.AiSceneGenerator;
import com.nododiiiii.ponderer.ai.AiGenerationTask;
import com.nododiiiii.ponderer.ai.AiSceneAgentPipeline;
import com.nododiiiii.ponderer.ai.AiRunFiles;
import com.nododiiiii.ponderer.ai.StructureDescriber;
import com.nododiiiii.ponderer.ai.McmodApiClient;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
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
import com.nododiiiii.ponderer.ui.catnip.WorkspaceHeaderListEntry;
import com.nododiiiii.ponderer.util.SafePaths;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.ScreenOpener;
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
    private static boolean cachedContextInitialized = false;
    private static int cachedStructureIndex = 0;
    private static String cachedCarrier = "";
    private static String cachedPrompt = "";
    private static boolean cachedEditExisting = false;
    private static String cachedExistingSceneKey = "";
    private static final ReferenceUrlManager referenceUrlManager = new ReferenceUrlManager();
    private static boolean cachedBuildTutorial = false;
    private static boolean cachedIncludeImages = false;
    private static boolean cachedRewrite = false;
    private static String cachedSteering = "";
    @Nullable private static AiSceneGenerator.PendingReview cachedPendingReview;
    @Nullable
    private static String cachedStatusMessage = null;
    private static boolean cachedStatusIsError = false;
    private static boolean cachedGenerating = false;
    private static boolean cachedCancelling = false;
    @Nullable
    private static AiGenerationTask cachedGenerationTask;

    private static final int PREVIEW_HEIGHT = UILayoutConstants.LIST_ENTRY_H * 2;
    private static final int STRUCTURE_CONTROLS_WIDTH = 108;
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
        if (!cachedContextInitialized) {
            selectDefaultStructure();
            cachedContextInitialized = true;
        }
    }

    @Override
    protected void init() {
        super.init();
        useConfirmationActionIcons();
        discardChanges.withCallback(this::discardEdits);
        setActionButtonTooltip(discardChanges, "ponderer.ui.ai_generate.discard");
        setActionButtonTooltip(goBack, "ponderer.ui.back");
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        previewW = 0;
        previewH = 0;
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasPreviewBounds() && list != null && list.isMouseOver(mouseX, mouseY)
            && preview.onMouseDown(mouseX, mouseY)) {
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
        entries.add(new WorkspaceHeaderListEntry(
            () -> UIText.of("ponderer.ui.ai_generate.title"),
            () -> UIText.of(cachedEditExisting
                ? "ponderer.ui.ai_generate.edit_mode.edit"
                : "ponderer.ui.ai_generate.edit_mode.new") + " · "
                + UIText.of("ponderer.ui.ai_generate.structures_header") + ": " + cachedStructurePaths.size(),
            List.of()));
        entries.add(new ActionStripListEntry(List.of(
            ActionStripListEntry.button(
                () -> UIText.of("ponderer.ui.ai_runs.title"),
                () -> List.of(Component.translatable("ponderer.ui.ai_runs.title")),
                () -> Minecraft.getInstance().setScreen(new AiRunHistoryScreen(this, this::resumeGeneration)),
                () -> HEADER_TEXT_COLOR,
                () -> !cachedGenerating),
            ActionStripListEntry.button(
                () -> UIText.of("ponderer.ui.ai_review.open"),
                () -> List.of(Component.translatable("ponderer.ui.ai_review.open")),
                () -> {
                    if (cachedPendingReview != null && cachedPendingReview.files().canReview()) {
                        openReview(cachedPendingReview);
                    }
                },
                () -> HEADER_TEXT_COLOR,
                () -> !cachedGenerating && cachedPendingReview != null && cachedPendingReview.files().canReview()))));
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.choice(
            "ponderer.ui.ai_generate.edit_mode", null, 100,
            this::toggleEditMode,
            () -> UIText.of(cachedEditExisting
                ? "ponderer.ui.ai_generate.edit_mode.edit"
                : "ponderer.ui.ai_generate.edit_mode.new")));

        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> cachedCarrier, value -> cachedCarrier = value),
            "ponderer.ui.ai_generate.carrier",
            null,
            "ponderer.ui.ai_generate.carrier.hint",
            -1,
            entry -> {
                entry.field().setMaxLength(128);
                entry.field().setEditable(!cachedEditExisting);
                if (cachedEditExisting) {
                    entry.addIngredientSlot(IdFieldMode.ITEM, null);
                    BoxWidget selector = entry.addTrailingIcon(PondererGuiIcons.LIST,
                        this::openExistingScenePicker, UIText.of("ponderer.ui.ai_generate.existing_scene.tooltip"));
                    if (!cachedExistingSceneKey.isBlank()) {
                        selector.getToolTip().add(Component.translatable(
                            "ponderer.ui.ai_generate.existing_scene.selected", cachedExistingSceneKey));
                    }
                }
            },
            FieldDecorators.jei(IdFieldMode.ITEM)));

        if (cachedEditExisting && !cachedExistingSceneKey.isBlank()) entries.add(FieldSpecs.toggle(
            "ponderer.ui.ai_generate.rewrite", "ponderer.ui.ai_generate.rewrite.tooltip",
            () -> cachedRewrite, () -> { cachedRewrite = !cachedRewrite; rebuildListPreservingScroll(); }));

        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> cachedPrompt, value -> cachedPrompt = value),
            "ponderer.ui.ai_generate.prompt",
            null,
            "ponderer.ui.ai_generate.prompt.hint",
            -1,
            entry -> entry.field().setMaxLength(2048)));

        entries.add(screen -> {
            screen.appendBuiltEntry(new StructurePreviewRowEntry(0));
            screen.appendBuiltEntry(new StructurePreviewRowEntry(1));
        });

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
        entries.add(FieldSpecs.text(FieldBindings.transientString(() -> cachedSteering, value -> cachedSteering = value),
            "ponderer.ui.ai_generate.steering", null, "ponderer.ui.ai_generate.steering.hint", -1,
            entry -> entry.field().setMaxLength(2048)));
        entries.add(FieldSpecs.fullButton(() -> UIText.of("ponderer.ui.ai_generate.send_steering"), null, () -> {
            if (cachedGenerationTask != null && cachedGenerationTask.steer(cachedSteering)) {
                cachedSteering = ""; setCachedStatus(UIText.of("ponderer.ui.ai_generate.steering_queued"), false);
                rebuildListPreservingScroll();
            } else setCachedStatus(UIText.of("ponderer.ui.ai_generate.steering_unavailable"), true);
        }, () -> HEADER_TEXT_COLOR, () -> cachedGenerating && !cachedCancelling && !cachedSteering.isBlank()));
    }

    @Override
    protected boolean saveEdits() {
        if (cachedGenerating) {
            return cancelGeneration();
        }
        boolean started = doGenerate();
        applyCachedStatus();
        return started;
    }

    @Override
    protected boolean isSaveButtonActive() {
        return true;
    }

    @Override
    protected boolean isDiscardButtonActive() {
        return true;
    }

    @Override
    protected void attemptBackToParent() {
        ScreenOpener.open(parent);
    }

    @Override
    protected void discardEdits() {
        if (pendingStructureInfo != null) {
            pendingStructureInfo.cancel(true);
            pendingStructureInfo = null;
        }
        restoreSnapshot(Map.of());
        referenceUrlManager.clear();
        cachedPendingReview = null;
        setCachedStatus(null, false);
        selectDefaultStructure();
        loadedPreviewPath = null;
        preview.clear();
        loadPreviewForCurrent();
        if (search != null) {
            search.setValue("");
        }
        rebuildEntries();
        captureBaselineState();
        applyCachedStatus();
    }

    private void selectDefaultStructure() {
        if (!SceneStore.ensureBuiltinStructure("basic")) {
            return;
        }
        Path defaultStructure = resolveStructurePath("ponderer:basic");
        if (defaultStructure != null) {
            cachedStructurePaths.add(defaultStructure);
            cachedStructureInfos.add(describeStructureSafe(defaultStructure));
            cachedStructureIndex = 0;
        }
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
        snapshot.put("edit_existing", String.valueOf(cachedEditExisting));
        snapshot.put("existing_scene", cachedExistingSceneKey);
        snapshot.put("build_tutorial", String.valueOf(cachedBuildTutorial));
        snapshot.put("include_images", String.valueOf(cachedIncludeImages));
        snapshot.put("rewrite", String.valueOf(cachedRewrite));
        snapshot.put("steering", cachedSteering);
        referenceUrlManager.snapshot(snapshot);
        return snapshot;
    }

    private boolean hasStructures() {
        return !cachedStructurePaths.isEmpty();
    }

    private void toggleEditMode() {
        cachedEditExisting = !cachedEditExisting;
        cachedRewrite = false;
        setCachedStatus(null, false);
        rebuildListPreservingScroll();
    }

    private void openExistingScenePicker() {
        if (SceneRuntime.getScenes().isEmpty()) {
            setCachedStatus(UIText.of("ponderer.ui.function_page.no_scenes"), true);
            return;
        }
        Minecraft.getInstance().setScreen(new PonderItemGridScreen(this,
            sceneKey -> {
                Minecraft.getInstance().setScreen(this);
                DslScene scene = SceneRuntime.findByKey(sceneKey);
                if (scene == null || !canAdjustScene(scene)) {
                    return;
                }
                cachedEditExisting = true;
                cachedExistingSceneKey = sceneKey;
                cachedRewrite = false;
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
            setCachedStatus(UIText.of("ponderer.ui.readonly_scene.message"), true);
            return false;
        }
        PackStateStore.load();
        if (scene.pack != null && !scene.pack.isBlank() && !PackStateStore.isImported(scene.pack)) {
            setCachedStatus(UIText.of("ponderer.ui.readonly_import_prompt.message"), true);
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
    public List<IngredientTarget> getJeiIngredientTargets() {
        return cachedEditExisting ? List.of() : super.getJeiIngredientTargets();
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
        if (cachedEditExisting) {
            if (cachedExistingSceneKey.isBlank()) {
                setCachedStatus(UIText.of("ponderer.ui.ai_generate.error.no_existing_scene"), true);
                return false;
            }
            existingScene = SceneRuntime.findByKey(cachedExistingSceneKey);
            if (existingScene == null) {
                setCachedStatus(UIText.of("ponderer.cmd.scene_not_found", cachedExistingSceneKey), true);
                return false;
            }
            if (!canAdjustScene(existingScene)) {
                return false;
            }
        }
        if (cachedStructurePaths.isEmpty() && existingScene == null) {
            setCachedStatus(UIText.of("ponderer.ui.ai_generate.error.no_structure"), true);
            return false;
        }
        if (cachedCarrier.trim().isEmpty()) {
            setCachedStatus(UIText.of("ponderer.ui.ai_generate.error.no_carrier"), true);
            return false;
        }
        ResourceLocation carrierId = ResourceLocation.tryParse(cachedCarrier.trim());
        if (carrierId == null || !BuiltInRegistries.ITEM.containsKey(carrierId)
            || BuiltInRegistries.ITEM.get(carrierId) == net.minecraft.world.item.Items.AIR) {
            setCachedStatus(UIText.of("ponderer.ui.ai_generate.error.invalid_carrier"), true);
            return false;
        }
        if (cachedPrompt.trim().isEmpty()) {
            setCachedStatus(UIText.of("ponderer.ui.ai_generate.error.no_prompt"), true);
            return false;
        }

        List<String> urls = new ArrayList<>();
        for (String url : referenceUrlManager.getUrlValues()) {
            if (url != null && !url.isBlank()) {
                urls.add(url.trim());
            }
        }

        return beginGeneration(new AiSceneAgentPipeline.Request(new ArrayList<>(cachedStructurePaths), carrierId.toString(),
            cachedPrompt.trim(), urls, existingScene == null ? null : SCENE_GSON.toJson(existingScene), cachedBuildTutorial, cachedIncludeImages,
            null, null, cachedRewrite && existingScene != null ? AiSceneAgentPipeline.Request.EditMode.REWRITE : AiSceneAgentPipeline.Request.EditMode.SCOPED), null);
    }

    private void resumeGeneration(AiRunFiles files, String clarification) {
        if (cachedGenerating) return;
        try {
            AiSceneAgentPipeline.Request saved = files.recoveryRequest();
            if (files.canReview()) {
                String reviewed = files.read("checkpoint.json").get("draft").toString();
                String feedbackPrompt = "Revise the current draft using the preview feedback below. Preserve unrelated steps and metadata.\n" + clarification.trim();
                saved = new AiSceneAgentPipeline.Request(saved.structurePaths(), saved.carrierItemId(), feedbackPrompt,
                    saved.referenceUrls(), reviewed, false, saved.includeImages(), saved.resumeDraft(), null,
                    AiSceneAgentPipeline.Request.EditMode.SCOPED, saved.resumeMemory());
                clarification = "";
            }
            String prompt = saved.userPrompt() + (clarification.isBlank() ? "" : "\nUser clarification for recovery:\n" + clarification.trim());
            AiSceneAgentPipeline.Request request = new AiSceneAgentPipeline.Request(saved.structurePaths(), saved.carrierItemId(), prompt,
                saved.referenceUrls(), saved.existingJson(), saved.buildTutorial(), saved.includeImages(), saved.resumeDraft(), saved.resumeScope(), saved.editMode(), saved.resumeMemory());
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
                ClientFeedback.chat(Component.translatable("ponderer.ui.ai_generate.completed",
                    Path.of(filePath).getFileName().toString()));
                refreshCurrentScreen();
            }),
            error -> Minecraft.getInstance().execute(() -> {
                if (!isCurrentTask(taskRef.get())) {
                    return;
                }
                cachedGenerating = false;
                cachedCancelling = false;
                cachedGenerationTask = null;
                setCachedStatus(error, true);
                refreshCurrentScreen();
            }),
            () -> Minecraft.getInstance().execute(() -> {
                if (!isCurrentTask(taskRef.get())) {
                    return;
                }
                cachedGenerating = false;
                cachedCancelling = false;
                cachedGenerationTask = null;
                setCachedStatus(UIText.of("ponderer.ui.ai_generate.status.cancelled"), false);
                refreshCurrentScreen();
            }),
            statusMsg -> Minecraft.getInstance().execute(() -> {
                if (!isCurrentTask(taskRef.get()) || cachedCancelling) {
                    return;
                }
                setCachedStatus(statusMsg, false);
                refreshCurrentScreen();
            }), review -> {
                if (!isCurrentTask(taskRef.get())) return;
                cachedGenerating = false; cachedCancelling = false; cachedGenerationTask = null;
                cachedPendingReview = review;
                setCachedStatus(UIText.of("ponderer.ui.ai_review.ready"), false);
                DslScene scene = review.scene();
                String name = scene.title == null ? "" : scene.title.resolve();
                ClientFeedback.chat(Component.translatable("ponderer.ui.ai_generate.completed",
                    name.isBlank() ? scene.id : name));
                refreshCurrentScreen();
            });
        taskRef.set(task);
        cachedGenerationTask = task;
        return true;
    }

    private void openReview(AiSceneGenerator.PendingReview review) {
        Minecraft.getInstance().setScreen(new AiSceneReviewScreen(this, review, this::resumeGeneration, () -> {
            cachedPendingReview = null;
            setCachedStatus(UIText.of("ponderer.ui.ai_generate.status.success"), false);
        }));
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
        if (shouldShowStop) {
            PonderIconStencils.attachFail(saveChanges,
                PonderIconStencils.centered(PonderGuiTextures.ICON_CONFIG_DISCARD));
        } else {
            saveChanges.showingElement(PonderGuiTextures.ICON_CONFIRM.asStencil()
                .withElementRenderer(BoxWidget.gradientFactory.apply(saveChanges)));
        }
        setActionButtonTooltip(saveChanges, shouldShowStop
            ? "ponderer.ui.ai_generate.stop" : "ponderer.ui.ai_generate.generate");
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

    private static void refreshCurrentScreen() {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof AiGenerateScreen screen) {
                screen.applyCachedStatus();
                screen.refreshGenerationActionButton();
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
        cachedEditExisting = Boolean.parseBoolean(snapshot.getOrDefault("edit_existing",
            String.valueOf(!cachedExistingSceneKey.isBlank())));
        cachedBuildTutorial = Boolean.parseBoolean(snapshot.getOrDefault("build_tutorial", "false"));
        cachedIncludeImages = Boolean.parseBoolean(snapshot.getOrDefault("include_images", "false"));
        cachedRewrite = Boolean.parseBoolean(snapshot.getOrDefault("rewrite", "false"));
        cachedSteering = snapshot.getOrDefault("steering", "");
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

    private class StructurePreviewRowEntry extends ConfigScreenList.Entry {
        private final int row;
        private final BoxWidget leftButton;
        private final BoxWidget rightButton;

        private StructurePreviewRowEntry(int row) {
            this.row = row;
            if (row == 0) {
                leftButton = createNavButton(PonderGuiTextures.ICON_PONDER_LEFT,
                    AiGenerateScreen.this::selectPreviousStructure, "ponderer.ui.ai_generate.prev.tooltip");
                rightButton = createNavButton(PonderGuiTextures.ICON_PONDER_RIGHT,
                    AiGenerateScreen.this::selectNextStructure, "ponderer.ui.ai_generate.next.tooltip");
            } else {
                leftButton = new FormBoxWidget(0, 0, 0, NAV_BUTTON_SIZE)
                    .withCallback(AiGenerateScreen.this::openStructurePicker);
                leftButton.getToolTip().add(Component.translatable("ponderer.ui.ai_generate.add.tooltip"));
                rightButton = new FormBoxWidget(0, 0, 0, NAV_BUTTON_SIZE)
                    .withCallback(AiGenerateScreen.this::deleteStructure);
                rightButton.getToolTip().add(Component.translatable("ponderer.ui.ai_generate.delete.tooltip"));
            }
            listeners.add(leftButton);
            listeners.add(rightButton);
        }

        @Override
        public void tick() {
            super.tick();
            syncButtons();
            leftButton.tick();
            rightButton.tick();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            if (list == null) return;
            graphics.enableScissor(list.getX(), list.getY(), list.getRight(), list.getBottom());
            try {
                renderRow(graphics, x, y, width, height, mouseX, mouseY, partialTicks);
            } finally {
                graphics.disableScissor();
            }
        }

        private void renderRow(GuiGraphics graphics, int x, int y, int width, int height,
                               int mouseX, int mouseY, float partialTicks) {
            int controlsWidth = Math.min(STRUCTURE_CONTROLS_WIDTH, EntryTextSupport.contentWidth(width) / 2);
            int controlsRight = EntryTextSupport.contentRight(x, width);
            int controlsX = controlsRight - controlsWidth;
            renderPreview(graphics, x, y - row * getEntryHeight(), controlsX, partialTicks);

            var font = Minecraft.getInstance().font;
            int buttonY = row == 0 ? y + 12 : y + Math.max(0, (height - NAV_BUTTON_SIZE) / 2);
            int buttonWidth = row == 0 ? NAV_BUTTON_SIZE : controlsWidth / 2;
            layoutButton(leftButton, controlsX, buttonY, buttonWidth);
            layoutButton(rightButton, controlsRight - buttonWidth, buttonY, buttonWidth);
            syncButtons();
            leftButton.render(graphics, mouseX, mouseY, partialTicks);
            rightButton.render(graphics, mouseX, mouseY, partialTicks);

            if (row == 0) {
                String name = font.plainSubstrByWidth(currentStructureName(), controlsWidth);
                graphics.drawCenteredString(font, name, controlsX + controlsWidth / 2, y + 1,
                    hasStructures() ? HEADER_TEXT_COLOR : HEADER_MUTED_TEXT_COLOR);
                graphics.drawCenteredString(font, currentStructureCounter(), controlsX + controlsWidth / 2,
                    buttonY + (NAV_BUTTON_SIZE - font.lineHeight) / 2, HEADER_TITLE_COLOR);
            } else {
                EntryTextSupport.drawButtonLabel(graphics, leftButton, UIText.of("ponderer.ui.ai_generate.add"),
                    HEADER_TEXT_COLOR);
                EntryTextSupport.drawButtonLabel(graphics, rightButton, UIText.of("ponderer.ui.ai_generate.delete"),
                    rightButton.active ? HEADER_TEXT_COLOR : HEADER_MUTED_TEXT_COLOR);
            }
        }

        private void renderPreview(GuiGraphics graphics, int x, int y, int controlsX, float partialTicks) {
            // Either visible row can draw the shared preview, including when the first row has scrolled away.
            if (hasPreviewBounds() || list == null) return;
            previewX = EntryTextSupport.contentLeft(x);
            previewY = y + 2;
            int visibleTop = Math.max(previewY, list.getY());
            int visibleBottom = Math.min(previewY + PREVIEW_HEIGHT - 4, list.getBottom());
            if (visibleBottom <= visibleTop) return;
            previewW = Math.max(0, controlsX - EntryTextSupport.rightControlGap() - previewX);
            previewH = PREVIEW_HEIGHT - 4;
            if (!hasPreviewBounds()) return;
            preview.setShowDefaultPlaceholder(!hasStructures());
            preview.setBounds(previewX, previewY, previewW, previewH);

            graphics.enableScissor(previewX, visibleTop, previewX + previewW, visibleBottom);
            preview.render(graphics, partialTicks);
            graphics.disableScissor();
        }

        @Override
        public Component getNarration() {
            return CommonComponents.EMPTY;
        }

        private void layoutButton(BoxWidget button, int x, int y, int width) {
            button.setX(x);
            button.setY(y);
            button.setWidth(width);
            button.setHeight(NAV_BUTTON_SIZE);
        }

        private void syncButtons() {
            leftButton.active = row == 0 ? canTurnStructures() : true;
            rightButton.active = row == 0 ? canTurnStructures() : hasStructures();
            leftButton.updateGradientFromState();
            rightButton.updateGradientFromState();
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
