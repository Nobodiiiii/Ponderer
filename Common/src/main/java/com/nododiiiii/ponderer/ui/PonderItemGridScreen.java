package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.NbtSceneFilter;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.TextStencilElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Uses the standard declarative list screen shell and replaces only the list content
 * with a custom scrollable grid panel, so the chrome/background/buttons match the
 * rest of the catnip-based screens.
 */
public class PonderItemGridScreen extends AbstractDeclarativeListScreen {

    public enum Mode {
        LIST, SINGLE_SELECT, MULTI_SELECT
    }

    private static final int INFO_ENTRY_H = 40;
    private static final int CONTROL_ENTRY_H = 32;
    private static final int CELL_SIZE = 20;
    private static final int GRID_PAD_X = 12;
    private static final int GRID_PAD_Y = 8;
    private static final int SECTION_HEADER_H = 16;
    private static final int SCROLL_SPEED = UILayoutConstants.SCROLL_SPEED;

    record ItemEntry(ItemStack stack, @Nullable String nbtFilter, List<String> sceneKeys) {
    }

    record PackSection(@Nullable String packPrefix, String displayName, List<ItemEntry> entries) {
    }

    enum GroupMode {
        BY_PACK, BY_ITEM
    }

    private final Mode mode;
    private final @Nullable Consumer<String> onSelectSingle;
    private final @Nullable Consumer<Set<String>> onSelectMulti;
    private final @Nullable Runnable onCancel;

    private final Set<String> selectedSceneKeys = new HashSet<>();
    private final Set<String> baselineSelectedSceneKeys = new HashSet<>();

    private List<PackSection> sections = List.of();
    private List<PackSection> visibleSections = List.of();
    private GroupMode groupMode = GroupMode.BY_PACK;
    private String searchFilter = "";
    private String initialSearchText = "";
    private double pendingPanelScroll = 0;
    private int totalSceneCount;
    private int totalItemCount;
    private int lastMouseX;
    private int lastMouseY;

    @Nullable
    private GridContentPanel gridPanel;
    @Nullable
    private TextButton selectAllButton;
    @Nullable
    private TextButton deselectAllButton;
    @Nullable
    private TextButton groupToggleButton;

    /** When non-null, PonderUI close will return to this screen (via mixin). */
    @Nullable
    public static PonderItemGridScreen returnScreen;

    public PonderItemGridScreen() {
        this(new FunctionScreen());
    }

    public PonderItemGridScreen(@Nullable Screen parent) {
        this(parent, Mode.LIST, null, null, null);
    }

    public PonderItemGridScreen(Consumer<String> onSelect, Runnable onCancel) {
        this(null, onSelect, onCancel);
    }

    public PonderItemGridScreen(@Nullable Screen parent, Consumer<String> onSelect, Runnable onCancel) {
        this(parent, Mode.SINGLE_SELECT, onSelect, null, onCancel);
    }

    public PonderItemGridScreen(Consumer<Set<String>> onSelectMulti, Runnable onCancel, boolean multi) {
        this(null, onSelectMulti, onCancel, multi);
    }

    public PonderItemGridScreen(@Nullable Screen parent, Consumer<Set<String>> onSelectMulti,
                                Runnable onCancel, boolean multi) {
        this(parent, Mode.MULTI_SELECT, null, onSelectMulti, onCancel);
    }

    private static String titleKeyFor(Mode mode) {
        return switch (mode) {
            case LIST -> "ponderer.ui.item_grid.select_scene";
            case SINGLE_SELECT -> "ponderer.ui.item_grid.select_scene";
            case MULTI_SELECT -> "ponderer.ui.item_grid.select_scenes";
        };
    }

    private PonderItemGridScreen(@Nullable Screen parent, Mode mode, @Nullable Consumer<String> onSelectSingle,
                                 @Nullable Consumer<Set<String>> onSelectMulti,
                                 @Nullable Runnable onCancel) {
        super(parent, "ponderer.ui.scope.editor", titleKeyFor(mode), UILayoutConstants.EDITOR_LIST_W);
        this.mode = mode;
        this.onSelectSingle = onSelectSingle;
        this.onSelectMulti = onSelectMulti;
        this.onCancel = onCancel;
        refreshVisibleSections();
    }

    @Override
    protected void init() {
        super.init();

        if (list != null) {
            removeWidget(list);
            list = null;
        }

        gridPanel = new GridContentPanel(
            panelLeft(),
            contentAreaTop(),
            currentListWidthValue(),
            contentAreaHeight());
        addRenderableWidget(gridPanel);

        initControlButtons();
        configureActionButtons();

        if (search != null) {
            search.setResponder(this::updateSearchFilter);
            if (!initialSearchText.isBlank()) {
                search.setValue(initialSearchText);
            }
            updateSearchFilter(search.getValue());
        } else {
            updateSearchFilter(searchFilter);
        }

        if (gridPanel != null) {
            relayoutGridPanel();
            gridPanel.setScroll(pendingPanelScroll);
            gridPanel.clampScroll();
        }
    }

    @Override
    public void resize(Minecraft client, int width, int height) {
        pendingPanelScroll = gridPanel != null ? gridPanel.scrollY() : 0;
        initialSearchText = search != null ? search.getValue() : initialSearchText;
        super.resize(client, width, height);
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return mode == Mode.MULTI_SELECT && !baselineSelectedSceneKeys.equals(selectedSceneKeys);
    }

    @Override
    protected int getUnsavedChangeCount() {
        return hasUnsavedChanges() ? 1 : 0;
    }

    @Override
    protected boolean saveEdits() {
        return confirmSelection();
    }

    @Override
    protected void discardEdits() {
        selectedSceneKeys.clear();
        selectedSceneKeys.addAll(baselineSelectedSceneKeys);
    }

    @Override
    protected boolean isSaveButtonActive() {
        return mode == Mode.MULTI_SELECT;
    }

    @Override
    protected boolean isDiscardButtonActive() {
        return mode == Mode.MULTI_SELECT;
    }

    @Override
    protected void attemptBackToParent() {
        if (mode != Mode.MULTI_SELECT || !hasUnsavedChanges()) {
            runCancelAction();
            return;
        }

        showLeavingPrompt(response -> {
            if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Cancel) {
                return;
            }
            if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Confirm) {
                if (!saveEdits()) {
                    return;
                }
            } else {
                discardEdits();
                runCancelAction();
                return;
            }
            runCancelAction();
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (gridPanel != null && gridPanel.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_W) {
            HitResult hit = hitTest(lastMouseX, lastMouseY);
            if (hit != null) {
                returnScreen = copyForReturn();
                openFilteredPonderUI(hit.entry);
                return true;
            }
        }

        return false;
    }

    private void refreshVisibleSections() {
        sections = collectSectionsForCurrentMode();
        visibleSections = searchFilter.isBlank() ? sections : filterSections(searchFilter);
        recomputeCounts(visibleSections);
    }

    private List<PackSection> collectSectionsForCurrentMode() {
        return groupMode == GroupMode.BY_PACK ? collectGroupedSections() : collectFlatSections();
    }

    private void recomputeCounts(List<PackSection> source) {
        int items = 0;
        Set<String> allKeys = new HashSet<>();
        for (PackSection section : source) {
            items += section.entries.size();
            for (ItemEntry entry : section.entries) {
                allKeys.addAll(entry.sceneKeys);
            }
        }
        totalItemCount = items;
        totalSceneCount = allKeys.size();
    }

    private List<PackSection> filterSections(String normalizedQuery) {
        List<PackSection> filtered = new ArrayList<>();
        for (PackSection section : sections) {
            boolean sectionMatches = section.displayName.toLowerCase(Locale.ROOT).contains(normalizedQuery);
            List<ItemEntry> matched = new ArrayList<>();
            for (ItemEntry entry : section.entries) {
                if (sectionMatches || matchesQuery(entry, normalizedQuery)) {
                    matched.add(entry);
                }
            }
            if (!matched.isEmpty()) {
                filtered.add(new PackSection(section.packPrefix, section.displayName, matched));
            }
        }
        return filtered;
    }

    private static boolean matchesQuery(ItemEntry entry, String normalizedQuery) {
        if (entry.stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(entry.stack.getItem());
        if (itemId != null && itemId.toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }

        if (entry.nbtFilter != null && entry.nbtFilter.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }

        for (String sceneKey : entry.sceneKeys) {
            if (sceneKey.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    private void updateSearchFilter(String query) {
        searchFilter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        initialSearchText = query == null ? "" : query;
        refreshVisibleSections();
        if (search != null) {
            search.setTextColor(visibleSections.isEmpty() && !searchFilter.isBlank()
                ? AbstractSimiWidget.COLOR_FAIL.getFirst().getRGB()
                : UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
        }
        if (gridPanel != null) {
            relayoutGridPanel();
            gridPanel.setScroll(0);
        }
    }

    private void toggleGroupMode() {
        groupMode = (groupMode == GroupMode.BY_PACK) ? GroupMode.BY_ITEM : GroupMode.BY_PACK;
        refreshVisibleSections();
        if (gridPanel != null) {
            relayoutGridPanel();
            gridPanel.setScroll(0);
        }
        layoutControlButtons();
    }

    private void handleSelectAll() {
        for (PackSection section : visibleSections) {
            for (ItemEntry entry : section.entries) {
                selectedSceneKeys.addAll(entry.sceneKeys);
            }
        }
    }

    private void handleDeselectAll() {
        selectedSceneKeys.clear();
    }

    private void initControlButtons() {
        groupToggleButton = createTextButton(this::toggleGroupMode);
        selectAllButton = createTextButton(this::handleSelectAll);
        deselectAllButton = createTextButton(this::handleDeselectAll);
        layoutControlButtons();
    }

    private void configureActionButtons() {
        if (saveChanges != null) {
            if (mode == Mode.MULTI_SELECT) {
                saveChanges.visible = true;
                saveChanges.active = true;
                saveChanges.withCallback(this::confirmSelection);
                saveChanges.getToolTip().clear();
                saveChanges.getToolTip().add(Component.translatable("ponderer.ui.confirm"));
            } else {
                saveChanges.visible = false;
                saveChanges.active = false;
            }
        }

        if (discardChanges != null) {
            if (mode == Mode.MULTI_SELECT) {
                discardChanges.visible = true;
                discardChanges.active = true;
                discardChanges.withCallback(this::cancelSelection);
                discardChanges.getToolTip().clear();
                discardChanges.getToolTip().add(Component.translatable("ponderer.ui.cancel"));
            } else {
                discardChanges.visible = false;
                discardChanges.active = false;
            }
        }
    }

    private TextButton createTextButton(Runnable callback) {
        BoxWidget box = new BoxWidget(0, 0, 20, 16).withCallback(callback);
        TextStencilElement text = new TextStencilElement(Minecraft.getInstance().font, Component.empty())
            .centered(true, true);
        text.withElementRenderer(BoxWidget.gradientFactory.apply(box));
        box.showingElement(text);
        addRenderableWidget(box);
        return new TextButton(box, text);
    }

    private void layoutControlButtons() {
        if (groupToggleButton == null || gridPanel == null) {
            return;
        }

        int contentX = gridPanel.entryContentLeft();
        int contentRight = gridPanel.entryContentRight();
        int buttonH = 8;

        String groupLabel = groupMode == GroupMode.BY_PACK
            ? UIText.of("ponderer.ui.item_grid.group_by_pack")
            : UIText.of("ponderer.ui.item_grid.group_by_item");
        String selectAllLabel = UIText.of("ponderer.ui.item_grid.select_all");
        String deselectAllLabel = UIText.of("ponderer.ui.item_grid.deselect_all");
        int buttonW = Math.max(12, Math.max(
            Math.max(font.width(groupLabel), font.width(selectAllLabel)),
            font.width(deselectAllLabel)) + 2);

        int infoRowY = gridPanel.infoRowY();
        boolean infoVisible = gridPanel.isRowVisible(infoRowY, INFO_ENTRY_H);
        layoutButton(groupToggleButton, groupLabel,
            contentRight - buttonW, infoRowY + 8, buttonW, buttonH, infoVisible);

        int rowY = gridPanel.controlsRowY();
        boolean multiVisible = mode == Mode.MULTI_SELECT && gridPanel.isRowVisible(rowY, gridPanel.controlsRowHeight());
        if (selectAllButton != null) {
            layoutButton(selectAllButton, selectAllLabel,
                contentX, rowY + 9, buttonW, buttonH, multiVisible);
        }
        if (deselectAllButton != null) {
            layoutButton(deselectAllButton, deselectAllLabel,
                contentX + buttonW + 10, rowY + 9, buttonW, buttonH, multiVisible);
        }
    }

    private static void layoutButton(TextButton textButton, String label, int x, int y, int w, int h, boolean visible) {
        textButton.box.setX(x);
        textButton.box.setY(y);
        textButton.box.setWidth(w);
        textButton.box.setHeight(h);
        textButton.box.visible = visible;
        textButton.box.active = visible;
        textButton.text.withText(Component.literal(label));
    }

    private int panelLeft() {
        return width / 2 - currentListWidthValue() / 2;
    }

    private void relayoutGridPanel() {
        if (gridPanel == null) {
            return;
        }

        int panelHeight = Math.min(contentAreaHeight(), gridPanel.contentHeight());
        gridPanel.setY(centeredContentTop(panelHeight));
        gridPanel.setHeight(panelHeight);
        gridPanel.clampScroll();
        layoutControlButtons();
    }

    private boolean confirmSelection() {
        if (mode != Mode.MULTI_SELECT || onSelectMulti == null) {
            return false;
        }
        baselineSelectedSceneKeys.clear();
        baselineSelectedSceneKeys.addAll(selectedSceneKeys);
        onSelectMulti.accept(new HashSet<>(selectedSceneKeys));
        return true;
    }

    private void cancelSelection() {
        discardEdits();
        runCancelAction();
    }

    private void runCancelAction() {
        if (onCancel != null) {
            onCancel.run();
        } else {
            ScreenOpener.open(parent);
        }
    }

    private int getSelectionState(ItemEntry entry) {
        if (mode != Mode.MULTI_SELECT) {
            return 0;
        }
        boolean any = false;
        boolean all = true;
        for (String key : entry.sceneKeys) {
            if (selectedSceneKeys.contains(key)) {
                any = true;
            } else {
                all = false;
            }
        }
        if (all) {
            return 2;
        }
        return any ? 1 : 0;
    }

    @Nullable
    private HitResult hitTest(double mouseX, double mouseY) {
        if (gridPanel == null) {
            return null;
        }
        return gridPanel.hitTest(mouseX, mouseY);
    }

    private void handleItemClick(ItemEntry entry) {
        switch (mode) {
            case LIST -> {
                returnScreen = copyForReturn();
                openFilteredPonderUI(entry);
            }
            case SINGLE_SELECT -> {
                if (entry.sceneKeys.size() == 1) {
                    if (onSelectSingle != null) {
                        onSelectSingle.accept(entry.sceneKeys.get(0));
                    }
                } else {
                    Minecraft.getInstance().setScreen(new SceneIdListScreen(
                        entry.sceneKeys, SceneIdListScreen.SelectMode.SINGLE,
                        onSelectSingle, null, null,
                        () -> Minecraft.getInstance().setScreen(this)));
                }
            }
            case MULTI_SELECT -> {
                if (entry.sceneKeys.size() == 1) {
                    String key = entry.sceneKeys.get(0);
                    if (selectedSceneKeys.contains(key)) {
                        selectedSceneKeys.remove(key);
                    } else {
                        selectedSceneKeys.add(key);
                    }
                } else {
                    Set<String> pre = new HashSet<>();
                    for (String key : entry.sceneKeys) {
                        if (selectedSceneKeys.contains(key)) {
                            pre.add(key);
                        }
                    }
                    PonderItemGridScreen self = this;
                    Minecraft.getInstance().setScreen(new SceneIdListScreen(
                        entry.sceneKeys, SceneIdListScreen.SelectMode.MULTI,
                        null,
                        returnedKeys -> {
                            entry.sceneKeys.forEach(selectedSceneKeys::remove);
                            selectedSceneKeys.addAll(returnedKeys);
                            Minecraft.getInstance().setScreen(self);
                        },
                        pre,
                        () -> Minecraft.getInstance().setScreen(self)));
                }
            }
        }
    }

    private PonderItemGridScreen copyForReturn() {
        PonderItemGridScreen copy = new PonderItemGridScreen(parent, mode, onSelectSingle, onSelectMulti, onCancel);
        copy.groupMode = this.groupMode;
        copy.initialSearchText = search != null ? search.getValue() : initialSearchText;
        copy.pendingPanelScroll = gridPanel != null ? gridPanel.scrollY() : 0;
        copy.selectedSceneKeys.addAll(this.selectedSceneKeys);
        copy.baselineSelectedSceneKeys.addAll(this.baselineSelectedSceneKeys);
        copy.refreshVisibleSections();
        return copy;
    }

    private static List<PackSection> collectGroupedSections() {
        Map<String, Map<String, List<String>>> packGroups = new LinkedHashMap<>();
        Map<String, Map<String, List<String>>> packItemFilters = new LinkedHashMap<>();

        for (DslScene scene : SceneRuntime.getScenes()) {
            if (scene.items == null || scene.id == null) {
                continue;
            }
            String packPrefix = scene.getPackPrefix();
            String packKey = packPrefix != null ? packPrefix : "";
            String sceneKey = scene.sceneKey();

            for (String itemId : scene.items) {
                String nf = scene.nbtFilter;
                String entryKey = normalizedEntryKey(itemId, nf);

                packGroups.computeIfAbsent(packKey, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(entryKey, ignored -> new ArrayList<>());
                List<String> keys = packGroups.get(packKey).get(entryKey);
                if (!keys.contains(sceneKey)) {
                    keys.add(sceneKey);
                }

                packItemFilters.computeIfAbsent(packKey, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(itemId, ignored -> new ArrayList<>());
                List<String> filters = packItemFilters.get(packKey).get(itemId);
                if (nf != null && !nf.isBlank()) {
                    if (!filters.contains(nf)) {
                        filters.add(nf);
                    }
                } else if (!filters.contains(null)) {
                    filters.add(0, null);
                }
            }
        }

        List<PackSection> result = new ArrayList<>();
        List<String> packKeys = new ArrayList<>(packGroups.keySet());
        packKeys.sort((a, b) -> {
            if (a.isEmpty() && !b.isEmpty()) {
                return -1;
            }
            if (!a.isEmpty() && b.isEmpty()) {
                return 1;
            }
            return a.compareToIgnoreCase(b);
        });

        for (String packKey : packKeys) {
            Map<String, List<String>> entryMap = packGroups.get(packKey);
            Map<String, List<String>> filterMap = packItemFilters.getOrDefault(packKey, Map.of());
            List<ItemEntry> entries = buildEntries(entryMap, filterMap);
            if (!entries.isEmpty()) {
                String prefix = packKey.isEmpty() ? null : packKey;
                String displayName = packKey.isEmpty()
                    ? UIText.of("ponderer.ui.item_grid.section_local")
                    : packKey;
                result.add(new PackSection(prefix, displayName, entries));
            }
        }

        return result;
    }

    private static List<PackSection> collectFlatSections() {
        Map<String, List<String>> entryMap = new LinkedHashMap<>();
        Map<String, List<String>> itemFilters = new LinkedHashMap<>();

        for (DslScene scene : SceneRuntime.getScenes()) {
            if (scene.items == null || scene.id == null) {
                continue;
            }
            String sceneKey = scene.sceneKey();

            for (String itemId : scene.items) {
                String nf = scene.nbtFilter;
                String key = normalizedEntryKey(itemId, nf);

                entryMap.computeIfAbsent(key, ignored -> new ArrayList<>());
                List<String> keys = entryMap.get(key);
                if (!keys.contains(sceneKey)) {
                    keys.add(sceneKey);
                }

                itemFilters.computeIfAbsent(itemId, ignored -> new ArrayList<>());
                List<String> filters = itemFilters.get(itemId);
                if (nf != null && !nf.isBlank()) {
                    if (!filters.contains(nf)) {
                        filters.add(nf);
                    }
                } else if (!filters.contains(null)) {
                    filters.add(0, null);
                }
            }
        }

        List<ItemEntry> entries = buildEntries(entryMap, itemFilters);
        if (entries.isEmpty()) {
            return List.of();
        }
        return List.of(new PackSection(null, UIText.of("ponderer.ui.item_grid.section_all"), entries));
    }

    private static List<ItemEntry> buildEntries(Map<String, List<String>> entryMap,
                                                Map<String, List<String>> filterMap) {
        List<ItemEntry> entries = new ArrayList<>();
        for (var itemEntry : filterMap.entrySet()) {
            String itemId = itemEntry.getKey();
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == Items.AIR) {
                continue;
            }

            Set<String> seenNormalizedKeys = new HashSet<>();
            for (String nf : itemEntry.getValue()) {
                ItemStack stack = new ItemStack(item);
                if (nf != null) {
                    try {
                        CompoundTag filterTag = TagParser.parseTag(nf);
                        CompoundTag fullTag = new CompoundTag();
                        fullTag.putString("id", rl.toString());
                        fullTag.putByte("Count", (byte) 1);
                        fullTag.put("tag", filterTag);
                        ItemStack parsed = ItemStack.of(fullTag);
                        if (!parsed.isEmpty()) {
                            stack = parsed;
                        }
                    } catch (Exception ignored) {
                    }
                }

                String key = normalizedEntryKey(itemId, nf);
                if (!seenNormalizedKeys.add(key)) {
                    continue;
                }
                List<String> sceneKeys = entryMap.getOrDefault(key, List.of());
                if (!sceneKeys.isEmpty()) {
                    entries.add(new ItemEntry(stack, nf, sceneKeys));
                }
            }
        }
        return entries;
    }

    private static String normalizedEntryKey(String itemId, @Nullable String nbtFilter) {
        String nf = nbtFilter != null ? nbtFilter : "";
        if ("minecraft:written_book".equals(itemId) && !nf.isBlank()) {
            String title = NbtSceneFilter.extractWrittenBookTitleFromFilterSnbt(nf);
            if (title != null && !title.isBlank()) {
                return itemId + "|title:" + title;
            }
        }
        return itemId + "|" + nf;
    }

    private static String formatNbtFilterForTooltip(ItemEntry entry) {
        if (entry.nbtFilter == null) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(entry.stack.getItem());
        if (itemId != null && "minecraft:written_book".equals(itemId.toString())) {
            String title = NbtSceneFilter.extractWrittenBookTitleFromFilterSnbt(entry.nbtFilter);
            if (title != null && !title.isBlank()) {
                return "title: " + title;
            }
        }
        return entry.nbtFilter;
    }

    private static void openFilteredPonderUI(ItemEntry entry) {
        PonderUI ui = PonderUI.of(entry.stack);
        var accessor = (com.nododiiiii.ponderer.mixin.PonderUIAccessor) (Object) ui;
        List<net.createmod.ponder.foundation.PonderScene> allScenes = accessor.ponderer$getScenes();

        Set<SceneRuntime.PonderSceneRef> refs = SceneRuntime.resolvePonderSceneRefs(entry.sceneKeys);
        if (!refs.isEmpty()) {
            Map<String, int[]> idCounters = new LinkedHashMap<>();
            List<net.createmod.ponder.foundation.PonderScene> filtered = new ArrayList<>();
            for (net.createmod.ponder.foundation.PonderScene ps : allScenes) {
                String psId = ps.getId().toString();
                int[] counter = idCounters.computeIfAbsent(psId, ignored -> new int[]{0});
                int occ = counter[0]++;
                if (refs.contains(new SceneRuntime.PonderSceneRef(psId, occ))) {
                    filtered.add(ps);
                }
            }
            if (!filtered.isEmpty() && filtered.size() < allScenes.size()) {
                allScenes.clear();
                allScenes.addAll(filtered);
            }
        }

        // PonderUI inherits Catnip's NavigatableSimiScreen transition, which
        // re-renders the previous screen underneath during entry animation.
        // For the item grid we want an immediate handoff to avoid old-screen overlap.
        ScreenOpener.open(ui);
    }

    private static int rowsFor(int size, int columns) {
        return (size + columns - 1) / columns;
    }

    private String screenTitleText() {
        return UIText.of(titleKey);
    }

    private record TextButton(BoxWidget box, TextStencilElement text) {
    }

    private record HitResult(ItemEntry entry) {
    }

    private final class GridContentPanel extends AbstractSimiWidget {

        private double scrollY = 0;
        private boolean draggingScrollbar = false;
        private double scrollbarGrabOffset = 0;

        private GridContentPanel(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;

            clampScroll();
            renderPanelFrame(graphics);

            graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
            try {
                int drawY = infoRowY();
                renderInfoEntry(graphics, drawY);
                drawY += INFO_ENTRY_H;
                renderControlsEntry(graphics, drawY);
                drawY += controlsRowHeight();
                renderGridEntry(graphics, drawY, mouseX, mouseY);
            } finally {
                graphics.disableScissor();
            }

            renderScrollbar(graphics, mouseX, mouseY);
            renderTooltip(graphics, mouseX, mouseY);
            layoutControlButtons();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !isMouseOver(mouseX, mouseY)) {
                return false;
            }

            if (isOverScrollbar(mouseX, mouseY)) {
                if (contentHeight() <= getHeight()) {
                    return true;
                }
                if (isOverScrollbarThumb(mouseX, mouseY)) {
                    draggingScrollbar = true;
                    scrollbarGrabOffset = mouseY - scrollbarThumbY();
                } else {
                    jumpScrollbar(mouseY);
                }
                return true;
            }

            HitResult hit = hitTest(mouseX, mouseY);
            if (hit != null) {
                handleItemClick(hit.entry);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!draggingScrollbar || button != 0) {
                return false;
            }
            dragScrollbar(mouseY - scrollbarGrabOffset + scrollbarThumbHeight() / 2.0);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && draggingScrollbar) {
                draggingScrollbar = false;
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            setScroll(scrollY - delta * SCROLL_SPEED);
            return true;
        }

        private double scrollY() {
            return scrollY;
        }

        private void setScroll(double value) {
            scrollY = Mth.clamp(value, 0, Math.max(0, contentHeight() - getHeight()));
        }

        private void clampScroll() {
            setScroll(scrollY);
        }

        private int infoRowY() {
            return getY() - Mth.floor(scrollY);
        }

        private int controlsRowY() {
            return infoRowY() + INFO_ENTRY_H;
        }

        private int controlsRowHeight() {
            return mode == Mode.MULTI_SELECT ? CONTROL_ENTRY_H : 0;
        }

        private int gridRowY() {
            return controlsRowY() + controlsRowHeight();
        }

        private boolean isRowVisible(int rowY, int rowHeight) {
            return rowY + rowHeight > getY() && rowY < getY() + getHeight();
        }

        private int contentHeight() {
            return INFO_ENTRY_H + controlsRowHeight() + gridEntryHeight();
        }

        private int gridEntryHeight() {
            if (visibleSections.isEmpty()) {
                return 88;
            }
            int columns = columns();
            int height = GRID_PAD_Y * 2;
            for (PackSection section : visibleSections) {
                height += SECTION_HEADER_H;
                height += rowsFor(section.entries.size(), columns) * CELL_SIZE;
            }
            return Math.max(88, height);
        }

        private int entryLeft() {
            return getX() + 4;
        }

        private int entryWidth() {
            return getWidth() - 8;
        }

        private int entryContentLeft() {
            return getX() + 12;
        }

        private int entryContentRight() {
            return getX() + getWidth() - 12;
        }

        private int scrollbarX() {
            return getX() + getWidth() - UILayoutConstants.SCROLLBAR_W - 4;
        }

        private int scrollbarTop() {
            return getY() + 4;
        }

        private int scrollbarHeight() {
            return getHeight() - 8;
        }

        private int scrollbarThumbHeight() {
            if (contentHeight() <= getHeight()) {
                return scrollbarHeight();
            }
            return Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB,
                scrollbarHeight() * getHeight() / contentHeight());
        }

        private int scrollbarThumbY() {
            if (contentHeight() <= getHeight()) {
                return scrollbarTop();
            }
            int trackRange = scrollbarHeight() - scrollbarThumbHeight();
            int contentRange = contentHeight() - getHeight();
            return scrollbarTop() + Mth.floor(scrollY / contentRange * trackRange);
        }

        private boolean isOverScrollbar(double mouseX, double mouseY) {
            return mouseX >= scrollbarX() && mouseX < scrollbarX() + UILayoutConstants.SCROLLBAR_W
                && mouseY >= scrollbarTop() && mouseY < scrollbarTop() + scrollbarHeight();
        }

        private boolean isOverScrollbarThumb(double mouseX, double mouseY) {
            int thumbY = scrollbarThumbY();
            int thumbH = scrollbarThumbHeight();
            return mouseX >= scrollbarX() && mouseX < scrollbarX() + UILayoutConstants.SCROLLBAR_W
                && mouseY >= thumbY && mouseY < thumbY + thumbH;
        }

        private void jumpScrollbar(double mouseY) {
            dragScrollbar(mouseY);
        }

        private void dragScrollbar(double thumbCenterY) {
            if (contentHeight() <= getHeight()) {
                setScroll(0);
                return;
            }
            int thumbH = scrollbarThumbHeight();
            int trackRange = scrollbarHeight() - thumbH;
            double clampedCenter = Mth.clamp(thumbCenterY,
                scrollbarTop() + thumbH / 2.0,
                scrollbarTop() + scrollbarHeight() - thumbH / 2.0);
            double trackOffset = clampedCenter - scrollbarTop() - thumbH / 2.0;
            double ratio = trackRange <= 0 ? 0 : trackOffset / trackRange;
            setScroll(ratio * (contentHeight() - getHeight()));
        }

        private int columns() {
            int availableWidth = Math.max(CELL_SIZE, gridRightLimit() - gridLeft());
            return Math.max(1, availableWidth / CELL_SIZE);
        }

        private int gridInnerWidth() {
            return columns() * CELL_SIZE;
        }

        private int gridLeft() {
            return entryContentLeft();
        }

        private int gridRightLimit() {
            return scrollbarX() - 6;
        }

        private int gridRight() {
            return gridLeft() + gridInnerWidth();
        }

        private void renderPanelFrame(GuiGraphics graphics) {
            Color c = new Color(0x60_000000);
            UIRenderHelper.angledGradient(graphics, 90, getX() + getWidth() / 2, getY(), getWidth(), 5, c, Color.TRANSPARENT_BLACK);
            UIRenderHelper.angledGradient(graphics, -90, getX() + getWidth() / 2, getY() + getHeight(), getWidth(), 5, c, Color.TRANSPARENT_BLACK);
            UIRenderHelper.angledGradient(graphics, 0, getX(), getY() + getHeight() / 2, getHeight(), 5, c, Color.TRANSPARENT_BLACK);
            UIRenderHelper.angledGradient(graphics, 180, getX() + getWidth(), getY() + getHeight() / 2, getHeight(), 5, c, Color.TRANSPARENT_BLACK);
        }

        private void renderEntryShell(GuiGraphics graphics, int y, int height, boolean accent) {
            int entryX = entryLeft();
            int entryW = entryWidth();
            graphics.fill(entryX, y + 4, entryX + entryW, y + height - 4,
                accent ? 0x0C_F3D46B : 0x08_FFFFFF);
        }

        private void renderInfoEntry(GuiGraphics graphics, int y) {
            if (!isRowVisible(y, INFO_ENTRY_H)) {
                return;
            }
            renderEntryShell(graphics, y, INFO_ENTRY_H, true);

            String subtitle = mode == Mode.MULTI_SELECT
                ? UIText.of("ponderer.ui.item_grid.selected_count", selectedSceneKeys.size(), totalSceneCount)
                : UIText.of("ponderer.ui.item_grid.total_scenes", totalItemCount, totalSceneCount);

            graphics.drawString(font, screenTitleText(), entryContentLeft(), y + 8,
                UIRenderHelper.COLOR_TEXT_STRONG_ACCENT.getFirst().getRGB());
            graphics.drawString(font, subtitle, entryContentLeft(), y + 22,
                UIRenderHelper.COLOR_TEXT.getSecond().getRGB());
        }

        private void renderGridEntry(GuiGraphics graphics, int y, int mouseX, int mouseY) {
            int height = gridEntryHeight();
            renderEntryShell(graphics, y, height, false);
            graphics.fill(gridLeft() - 2, y + 4, gridRightLimit() + 2, y + height - 4, 0x08_000000);

            if (visibleSections.isEmpty()) {
                if (isRowVisible(y, height)) {
                    graphics.drawCenteredString(font, Component.translatable("ponderer.ui.item_grid.empty"),
                        getX() + getWidth() / 2, y + height / 2 - 4,
                        UIRenderHelper.COLOR_TEXT_DARKER.getFirst().getRGB());
                }
                return;
            }

            int contentY = y + GRID_PAD_Y;
            int columns = columns();
            for (PackSection section : visibleSections) {
                int headerY = contentY;
                if (headerY + SECTION_HEADER_H > getY() && headerY < getY() + getHeight()) {
                    graphics.drawString(font, section.displayName, gridLeft() + 2, headerY + 3,
                        UIRenderHelper.COLOR_TEXT_STRONG_ACCENT.getFirst().getRGB());
                    graphics.fill(gridLeft(), headerY + SECTION_HEADER_H - 1, gridRight(),
                        headerY + SECTION_HEADER_H, 0x20_FFFFFF);
                }
                contentY += SECTION_HEADER_H;

                for (int index = 0; index < section.entries.size(); index++) {
                    int col = index % columns;
                    int row = index / columns;
                    int itemX = gridLeft() + col * CELL_SIZE;
                    int itemY = contentY + row * CELL_SIZE;
                    if (itemY + CELL_SIZE <= getY() || itemY >= getY() + getHeight()) {
                        continue;
                    }

                    ItemEntry entry = section.entries.get(index);
                    boolean hovered = mouseX >= itemX && mouseX < itemX + CELL_SIZE
                        && mouseY >= itemY && mouseY < itemY + CELL_SIZE;
                    renderItemCell(graphics, itemX, itemY, entry, hovered);
                }
                contentY += rowsFor(section.entries.size(), columns) * CELL_SIZE;
            }
        }

        private void renderControlsEntry(GuiGraphics graphics, int y) {
            int rowHeight = controlsRowHeight();
            if (rowHeight == 0 || !isRowVisible(y, rowHeight)) {
                return;
            }
            renderEntryShell(graphics, y, rowHeight, false);
        }

        private void renderItemCell(GuiGraphics graphics, int x, int y, ItemEntry entry, boolean hovered) {
            int selectionState = getSelectionState(entry);
            Couple<Color> border = null;
            Color background = null;
            int iconX = x + 2;
            int iconY = y + 2;
            int iconSize = 16;

            if (selectionState == 2) {
                border = AbstractSimiWidget.COLOR_SUCCESS;
                background = new Color(0x24_88F788, true);
            } else if (selectionState == 1) {
                border = Couple.create(new Color(0xdd_ffcf75, true), new Color(0x90_ff9a30, true));
                background = new Color(0x20_ffb347, true);
            } else if (hovered) {
                border = AbstractSimiWidget.COLOR_HOVER;
                background = new Color(0x14_ffffff, true);
            }

            if (border != null) {
                new BoxElement()
                    .withBackground(background)
                    .gradientBorder(border.getFirst(), border.getSecond())
                    .at(iconX + 1, iconY + 1, 0)
                    .withBounds(iconSize - 2, iconSize - 2)
                    .render(graphics);
            }

            graphics.renderItem(entry.stack, iconX, iconY);
            if (entry.nbtFilter != null) {
                graphics.fill(x + CELL_SIZE - 5, y + 1, x + CELL_SIZE - 1, y + 5, 0xFF_FFAA00);
            }
            if (entry.sceneKeys.size() > 1) {
                graphics.fill(x + 1, y + 1, x + 5, y + 5, 0xFF_55AAFF);
            }
        }

        @Nullable
        private HitResult hitTest(double mouseX, double mouseY) {
            if (!isMouseOver(mouseX, mouseY)) {
                return null;
            }

            int columns = columns();
            int contentY = gridRowY() + GRID_PAD_Y;
            for (PackSection section : visibleSections) {
                contentY += SECTION_HEADER_H;
                for (int index = 0; index < section.entries.size(); index++) {
                    int col = index % columns;
                    int row = index / columns;
                    int itemX = gridLeft() + col * CELL_SIZE;
                    int itemY = contentY + row * CELL_SIZE;
                    if (mouseX >= itemX && mouseX < itemX + CELL_SIZE
                        && mouseY >= itemY && mouseY < itemY + CELL_SIZE
                        && itemY >= getY() && itemY + CELL_SIZE <= getY() + getHeight()) {
                        return new HitResult(section.entries.get(index));
                    }
                }
                contentY += rowsFor(section.entries.size(), columns) * CELL_SIZE;
            }
            return null;
        }

        private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
            if (contentHeight() <= getHeight()) {
                return;
            }
            int trackX = scrollbarX();
            int trackY = scrollbarTop();
            int trackH = scrollbarHeight();
            int thumbY = scrollbarThumbY();
            int thumbH = scrollbarThumbHeight();
            boolean hovered = isOverScrollbarThumb(mouseX, mouseY) || draggingScrollbar;
            graphics.fill(trackX, trackY, trackX + UILayoutConstants.SCROLLBAR_W, trackY + trackH,
                UILayoutConstants.COLOR_SCROLLBAR_BG);
            graphics.fill(trackX, thumbY, trackX + UILayoutConstants.SCROLLBAR_W, thumbY + thumbH,
                hovered ? UILayoutConstants.COLOR_SCROLLBAR_HOVER : UILayoutConstants.COLOR_SCROLLBAR_FG);
        }

        private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
            HitResult hit = hitTest(mouseX, mouseY);
            if (hit == null) {
                return;
            }

            ItemEntry entry = hit.entry;
            List<Component> tooltip = new ArrayList<>(
                entry.stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.NORMAL));
            tooltip.add(Component.literal(""));

            if (entry.sceneKeys.size() == 1) {
                String key = entry.sceneKeys.get(0);
                String prefix = mode == Mode.MULTI_SELECT && selectedSceneKeys.contains(key) ? "\u2713 " : "";
                tooltip.add(Component.literal(prefix)
                    .append(Component.translatable("ponderer.ui.item_grid.scene_label", key))
                    .withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.translatable("ponderer.ui.item_grid.scenes_count", entry.sceneKeys.size())
                    .withStyle(ChatFormatting.AQUA));
                for (int i = 0; i < Math.min(entry.sceneKeys.size(), 8); i++) {
                    String key = entry.sceneKeys.get(i);
                    String prefix = mode == Mode.MULTI_SELECT && selectedSceneKeys.contains(key) ? "\u2713 " : "  ";
                    tooltip.add(Component.literal(prefix + key).withStyle(ChatFormatting.DARK_AQUA));
                }
                if (entry.sceneKeys.size() > 8) {
                    tooltip.add(Component.literal("  ...").withStyle(ChatFormatting.GRAY));
                }
            }

            if (entry.nbtFilter != null) {
                tooltip.add(Component.translatable("ponderer.ui.item_list.nbt_filter")
                    .withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.literal(formatNbtFilterForTooltip(entry))
                    .withStyle(ChatFormatting.GRAY));
            }

            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    static class SceneIdListScreen extends AbstractDeclarativeListScreen {
        public enum SelectMode {
            SINGLE, MULTI
        }

        private final List<String> sceneKeys;
        private final SelectMode selectMode;
        private final @Nullable Consumer<String> onSelectSingle;
        private final @Nullable Consumer<Set<String>> onSelectMulti;
        private final Runnable onCancel;
        private final Set<String> selectedItems = new HashSet<>();
        private final Set<String> baselineSelectedItems = new HashSet<>();

        SceneIdListScreen(List<String> sceneKeys, Consumer<String> onSelect, Runnable onCancel) {
            this(sceneKeys, SelectMode.SINGLE, onSelect, null, null, onCancel);
        }

        SceneIdListScreen(List<String> sceneKeys, SelectMode selectMode,
                          @Nullable Consumer<String> onSelectSingle,
                          @Nullable Consumer<Set<String>> onSelectMulti,
                          @Nullable Set<String> preSelected,
                          Runnable onCancel) {
            super(null, "ponderer.ui.scope.editor", "ponderer.ui.item_grid.select_scene_id", UILayoutConstants.EDITOR_LIST_W);
            this.sceneKeys = sceneKeys;
            this.selectMode = selectMode;
            this.onSelectSingle = onSelectSingle;
            this.onSelectMulti = onSelectMulti;
            this.onCancel = onCancel;
            if (preSelected != null) {
                this.selectedItems.addAll(preSelected);
                this.baselineSelectedItems.addAll(preSelected);
            }
        }

        @Override
        protected void init() {
            super.init();
            if (selectMode == SelectMode.SINGLE) {
                if (saveChanges != null) {
                    saveChanges.visible = false;
                    saveChanges.active = false;
                }
                if (discardChanges != null) {
                    discardChanges.visible = false;
                    discardChanges.active = false;
                }
            }
        }

        @Override
        protected void collectEntries(List<ConfigScreenList.Entry> entries) {
            if (selectMode == SelectMode.MULTI) {
                entries.add(new SectionHeaderListEntry(
                    UIText.of("ponderer.ui.item_grid.sub_selected", selectedItems.size(), sceneKeys.size())));
            }

            for (String sceneKey : sceneKeys) {
                entries.add(new FullButtonListEntry(
                    () -> selectMode == SelectMode.MULTI && selectedItems.contains(sceneKey)
                        ? "[x] " + sceneKey
                        : sceneKey,
                    null,
                    () -> handleSceneClick(sceneKey),
                    () -> selectMode == SelectMode.MULTI && selectedItems.contains(sceneKey) ? 0x80FFFF : 0xFFFFFF,
                    () -> true));
            }
        }

        @Override
        protected boolean hasUnsavedChanges() {
            return selectMode == SelectMode.MULTI && !baselineSelectedItems.equals(selectedItems);
        }

        @Override
        protected int getUnsavedChangeCount() {
            return hasUnsavedChanges() ? 1 : 0;
        }

        @Override
        protected boolean saveEdits() {
            if (selectMode != SelectMode.MULTI || onSelectMulti == null) {
                return false;
            }
            onSelectMulti.accept(new HashSet<>(selectedItems));
            return true;
        }

        @Override
        protected void discardEdits() {
            selectedItems.clear();
            selectedItems.addAll(baselineSelectedItems);
            rebuildEntries(currentListScroll());
        }

        @Override
        protected int getEntryHeight() {
            return UILayoutConstants.COMPACT_LIST_ENTRY_H;
        }

        @Override
        protected void attemptBackToParent() {
            if (!hasUnsavedChanges()) {
                onCancel.run();
                return;
            }
            showLeavingPrompt(response -> {
                if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Cancel) {
                    return;
                }
                if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Confirm) {
                    if (!saveEdits()) {
                        return;
                    }
                } else {
                    discardEdits();
                }
                onCancel.run();
            });
        }

        private void handleSceneClick(String sceneKey) {
            if (selectMode == SelectMode.SINGLE) {
                if (onSelectSingle != null) {
                    onSelectSingle.accept(sceneKey);
                }
                return;
            }

            if (selectedItems.contains(sceneKey)) {
                selectedItems.remove(sceneKey);
            } else {
                selectedItems.add(sceneKey);
            }
            rebuildEntries(currentListScroll());
        }
    }
}
