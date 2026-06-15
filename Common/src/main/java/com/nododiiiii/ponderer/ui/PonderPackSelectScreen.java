package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.PonderPackInfo;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PonderPackSelectScreen extends AbstractReadonlyDeclarativeListScreen {

    private final Screen parent;
    private final Consumer<String> onSelect;
    private final Runnable onCancel;
    private final List<PonderPackInfo> packs = new ArrayList<>();

    public PonderPackSelectScreen(Screen parent, Consumer<String> onSelect, Runnable onCancel) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.pack_select.title", UILayoutConstants.EDITOR_LIST_W);
        this.parent = parent;
        this.onSelect = onSelect;
        this.onCancel = onCancel;
    }

    @Override
    protected void init() {
        reloadPacks();
        super.init();
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (packs.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.pack_select.empty")));
            return;
        }

        entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.pack_select.available")));
        for (PonderPackInfo info : packs) {
            entries.add(new FullButtonListEntry(
                packLabel(info),
                packTooltip(info),
                () -> {
                    onSelect.accept(info.name);
                    Minecraft.getInstance().setScreen(parent);
                }));
        }
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }

    private void reloadPacks() {
        packs.clear();
        packs.addAll(SceneStore.scanAvailableSourcePacks());
    }

    @Override
    protected void attemptBackToParent() {
        onCancel.run();
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
