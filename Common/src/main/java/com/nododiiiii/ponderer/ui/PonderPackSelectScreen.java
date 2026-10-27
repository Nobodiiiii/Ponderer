package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.workspace.api.PonderPackInfo;
import com.nododiiiii.ponderer.ponder.SceneItemBindings;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.SelectionRowListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import com.nododiiiii.ponderer.ui.catnip.WorkspaceHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PonderPackSelectScreen extends AbstractDeclarativeListScreen {

    private final Screen parent;
    private final Consumer<List<String>> onSelect;
    private final Runnable onCancel;
    private final List<PonderPackInfo> packs = new ArrayList<>();
    private final Set<String> originalSelection;
    private final Set<String> selectedPacks;

    public PonderPackSelectScreen(Screen parent, List<String> selectedPacks,
                                  Consumer<List<String>> onSelect, Runnable onCancel) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.pack_select.title", UILayoutConstants.EDITOR_LIST_W);
        this.parent = parent;
        this.originalSelection = new LinkedHashSet<>(selectedPacks);
        this.selectedPacks = new LinkedHashSet<>(selectedPacks);
        this.onSelect = onSelect;
        this.onCancel = onCancel;
    }

    @Override
    protected void init() {
        reloadPacks();
        super.init();
        useConfirmationActionIcons();
        if (discardChanges != null) discardChanges.withCallback(this::cancelSelection);
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new WorkspaceHeaderListEntry(
            () -> UIText.of("ponderer.ui.pack_select.title"),
            () -> UIText.of("ponderer.ui.pack_select.available_count", packs.size()) + " · "
                + UIText.of("ponderer.ui.function_page.selected_packs", selectedPacks.size()),
            List.of()));
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (packs.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.pack_select.empty")));
            return;
        }

        for (PonderPackInfo info : packs) {
            ItemStack icon = packIcon(info);
            entries.add(new SelectionRowListEntry(
                () -> info.name,
                () -> packDetail(info),
                () -> packLabel(info) + " " + packTooltip(info),
                () -> selectedPacks.contains(info.name),
                () -> togglePack(info.name),
                () -> List.of(Component.literal(packLabel(info)), Component.literal(packTooltip(info))),
                () -> icon, List.of()));
        }
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.LIST_ENTRY_H;
    }

    private void togglePack(String pack) {
        if (!selectedPacks.remove(pack)) selectedPacks.add(pack);
        clearStatusMessages();
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return !selectedPacks.equals(originalSelection);
    }

    @Override
    protected int getUnsavedChangeCount() {
        Set<String> added = new LinkedHashSet<>(selectedPacks);
        added.removeAll(originalSelection);
        Set<String> removed = new LinkedHashSet<>(originalSelection);
        removed.removeAll(selectedPacks);
        return added.size() + removed.size();
    }

    @Override
    protected boolean saveEdits() {
        onSelect.accept(List.copyOf(selectedPacks));
        originalSelection.clear();
        originalSelection.addAll(selectedPacks);
        Minecraft.getInstance().setScreen(parent);
        return true;
    }

    @Override
    protected void discardEdits() {
        selectedPacks.clear();
        selectedPacks.addAll(originalSelection);
        clearStatusMessages();
    }

    @Override
    protected boolean isSaveButtonActive() {
        return true;
    }

    @Override
    protected boolean isDiscardButtonActive() {
        return true;
    }

    private void cancelSelection() {
        discardEdits();
        onCancel.run();
    }

    private void reloadPacks() {
        packs.clear();
        packs.addAll(SceneStore.scanAvailableSourcePacks());
    }

    @Override
    protected void attemptBackToParent() {
        cancelSelection();
    }

    private ItemStack packIcon(PonderPackInfo info) {
        var firstScene = SceneRuntime.getScenes().stream()
            .filter(scene -> info.name.equals(scene.pack))
            .findFirst().orElse(null);
        var itemId = SceneItemBindings.firstItem(firstScene);
        return itemId == null ? ItemStack.EMPTY
            : BuiltInRegistries.ITEM.getOptional(itemId).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private String packDetail(PonderPackInfo info) {
        List<String> parts = new ArrayList<>();
        if (info.version != null && !info.version.isBlank()) parts.add("v" + info.version);
        if (info.author != null && !info.author.isBlank()) parts.add(info.author);
        String description = packTooltip(info);
        if (!description.isBlank()) parts.add(description);
        return String.join(" | ", parts);
    }

    private String packLabel(PonderPackInfo info) {
        StringBuilder label = new StringBuilder(info.name == null ? "" : info.name);
        if (info.version != null && !info.version.isBlank()) {
            label.append(" v").append(info.version);
        }
        if (info.author != null && !info.author.isBlank()) {
            label.append(" | ").append(info.author);
        }
        return label.toString();
    }

    private String packTooltip(PonderPackInfo info) {
        if (info.description != null && !info.description.isBlank()) {
            return info.description;
        }
        if (info.sourcePath != null && info.sourcePath.getFileName() != null) {
            return info.sourcePath.getFileName().toString();
        }
        return "";
    }
}
