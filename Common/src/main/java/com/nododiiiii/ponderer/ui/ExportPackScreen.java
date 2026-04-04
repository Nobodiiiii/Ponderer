package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ExportPackScreen extends com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen {

    private String draftName = "";
    private String draftVersion = "1.0.0";
    private String draftAuthor = "";
    private Set<String> selectedSceneIds = new HashSet<>();

    private String baselineName = draftName;
    private String baselineVersion = draftVersion;
    private String baselineAuthor = draftAuthor;
    private Set<String> baselineSceneIds = new HashSet<>();

    public ExportPackScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.function_page.export.title", 360);
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(textEntry(
            "ponderer.ui.export.name",
            null,
            "ponderer.ui.export.name",
            draftName,
            value -> {
                draftName = value;
                clearStatusMessages();
            }));
        entries.add(textEntry(
            "ponderer.ui.export.version",
            null,
            "ponderer.ui.export.version",
            draftVersion,
            value -> {
                draftVersion = value;
                clearStatusMessages();
            }));
        entries.add(textEntry(
            "ponderer.ui.export.author",
            null,
            "ponderer.ui.export.author",
            draftAuthor,
            value -> {
                draftAuthor = value;
                clearStatusMessages();
            }));
        entries.add(new FullButtonListEntry(
            currentSceneSelectionLabel(),
            UIText.of("ponderer.ui.export.all_scenes"),
            this::openSceneSelector));
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return getUnsavedChangeCount() > 0;
    }

    @Override
    protected int getUnsavedChangeCount() {
        int dirty = 0;
        if (!draftName.equals(baselineName)) {
            dirty++;
        }
        if (!draftVersion.equals(baselineVersion)) {
            dirty++;
        }
        if (!draftAuthor.equals(baselineAuthor)) {
            dirty++;
        }
        if (!new TreeSet<>(selectedSceneIds).equals(new TreeSet<>(baselineSceneIds))) {
            dirty++;
        }
        return dirty;
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();

        String name = draftName.trim();
        String version = draftVersion.trim();
        String author = draftAuthor.trim();

        if (version.isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.export.version_empty"));
            return false;
        }

        SceneStore.PackExportResult result = selectedSceneIds.isEmpty()
            ? SceneStore.packScenesAndStructuresDetailed(name, version, author)
            : SceneStore.packSelectedScenesAndStructuresDetailed(name, version, author, selectedSceneIds);

        if (!result.isSuccess()) {
            String key = result.uiMessageKey();
            setErrorMessage(key == null || key.isBlank()
                ? UIText.of("ponderer.ui.export.failed")
                : UIText.of(key, result.uiMessageArgs()));
            return false;
        }

        baselineName = draftName;
        baselineVersion = draftVersion;
        baselineAuthor = draftAuthor;
        baselineSceneIds = new HashSet<>(selectedSceneIds);
        setInfoMessage(UIText.of("ponderer.ui.export.success", name));
        return true;
    }

    @Override
    protected void discardEdits() {
        clearStatusMessages();
        draftName = baselineName;
        draftVersion = baselineVersion;
        draftAuthor = baselineAuthor;
        selectedSceneIds = new HashSet<>(baselineSceneIds);
        rebuildEntries(currentListScroll());
    }

    @Override
    protected int getEntryHeight() {
        return 40;
    }

    private void openSceneSelector() {
        Minecraft.getInstance().setScreen(new PonderItemGridScreen(
            selectedIds -> {
                selectedSceneIds = new HashSet<>(selectedIds);
                Minecraft.getInstance().setScreen(this);
            },
            () -> Minecraft.getInstance().setScreen(this),
            true));
    }

    private String currentSceneSelectionLabel() {
        return selectedSceneIds.isEmpty()
            ? UIText.of("ponderer.ui.export.all_scenes")
            : UIText.of("ponderer.ui.export.selected_scenes", selectedSceneIds.size());
    }
}
