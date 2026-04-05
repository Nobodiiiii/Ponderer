package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ExportPackScreen extends AbstractStatefulDeclarativeFormScreen {

    private String draftName = "";
    private String draftVersion = "1.0.0";
    private String draftAuthor = "";
    private Set<String> selectedSceneIds = new HashSet<>();

    public ExportPackScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.function_page.export.title", UILayoutConstants.EDITOR_LIST_W);
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> draftName, value -> {
                draftName = value;
                clearStatusMessages();
            }),
            "ponderer.ui.export.name",
            null,
            "ponderer.ui.export.name",
            -1));
        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> draftVersion, value -> {
                draftVersion = value;
                clearStatusMessages();
            }),
            "ponderer.ui.export.version",
            null,
            "ponderer.ui.export.version",
            -1));
        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> draftAuthor, value -> {
                draftAuthor = value;
                clearStatusMessages();
            }),
            "ponderer.ui.export.author",
            null,
            "ponderer.ui.export.author",
            -1));
        entries.add(FieldSpecs.fullButton(
            currentSceneSelectionLabel(),
            UIText.of("ponderer.ui.export.all_scenes"),
            this::openSceneSelector));
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

        markStateSaved();
        setInfoMessage(UIText.of("ponderer.ui.export.success", name));
        return true;
    }

    @Override
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("name", draftName);
        snapshot.put("version", draftVersion);
        snapshot.put("author", draftAuthor);
        TreeSet<String> ordered = new TreeSet<>(selectedSceneIds);
        snapshot.put("scene_count", String.valueOf(ordered.size()));
        int index = 0;
        for (String sceneId : ordered) {
            snapshot.put("scene_" + index, sceneId);
            index++;
        }
        return snapshot;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        draftName = snapshot.getOrDefault("name", "");
        draftVersion = snapshot.getOrDefault("version", "1.0.0");
        draftAuthor = snapshot.getOrDefault("author", "");

        selectedSceneIds = new HashSet<>();
        int count = 0;
        try {
            count = Integer.parseInt(snapshot.getOrDefault("scene_count", "0"));
        } catch (NumberFormatException ignored) {
        }
        for (int i = 0; i < count; i++) {
            String sceneId = snapshot.get("scene_" + i);
            if (sceneId != null && !sceneId.isBlank()) {
                selectedSceneIds.add(sceneId);
            }
        }
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
