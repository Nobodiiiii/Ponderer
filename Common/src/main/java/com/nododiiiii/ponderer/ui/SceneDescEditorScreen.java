package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.ui.catnip.LocalizedTextListEntry;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SceneDescEditorScreen extends AbstractStatefulDeclarativeFormScreen {

    private final DslScene scene;
    private final int sceneIndex;
    private final boolean hasMultiScene;

    private LocalizedText originalPonderTitle;
    private LocalizedText workingPonderTitle;
    private String ponderTitleLang;

    @Nullable
    private LocalizedText originalSceneTitle;
    @Nullable
    private LocalizedText workingSceneTitle;
    private String sceneTitleLang;

    private String originalPonderId;
    private String draftPonderId;
    private String originalSceneId;
    private String draftSceneId;
    private boolean workingEditable;

    @Nullable
    private LocalizedTextListEntry ponderTitleEntry;
    @Nullable
    private LocalizedTextListEntry sceneTitleEntry;

    public SceneDescEditorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.scene_desc");
        this.scene = scene;
        this.sceneIndex = sceneIndex;
        this.hasMultiScene = scene.scenes != null
            && !scene.scenes.isEmpty()
            && sceneIndex >= 0
            && sceneIndex < scene.scenes.size();

        this.ponderTitleLang = getCurrentLang();
        this.sceneTitleLang = getCurrentLang();
        syncStateFromScene();
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.localizedText(
            FieldBindings.transientString(
                () -> localizedValue(workingPonderTitle, ponderTitleLang),
                value -> {
                    workingPonderTitle.setForLang(ponderTitleLang, value);
                    clearStatusMessages();
                }),
            "ponderer.ui.scene_desc.ponder_title",
            null,
            "ponderer.ui.scene_desc.hint.ponder_title",
            -1,
            () -> ponderTitleLang,
            this::togglePonderTitleLang,
            entry -> ponderTitleEntry = entry));

        if (hasMultiScene && workingSceneTitle != null) {
            entries.add(FieldSpecs.localizedText(
                FieldBindings.transientString(
                    () -> localizedValue(workingSceneTitle, sceneTitleLang),
                    value -> {
                        workingSceneTitle.setForLang(sceneTitleLang, value);
                        clearStatusMessages();
                    }),
                "ponderer.ui.scene_desc.scene_title",
                null,
                "ponderer.ui.scene_desc.hint.scene_title",
                -1,
                () -> sceneTitleLang,
                this::toggleSceneTitleLang,
                entry -> sceneTitleEntry = entry));
        } else {
            sceneTitleEntry = null;
        }

        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> draftPonderId, value -> {
                draftPonderId = value;
                clearStatusMessages();
            }),
            "ponderer.ui.scene_desc.ponder_id",
            "ponderer.ui.scene_desc.id_hint",
            "ponderer.ui.scene_desc.hint.ponder_id",
            -1));

        if (hasMultiScene) {
            entries.add(FieldSpecs.text(
                FieldBindings.transientString(() -> draftSceneId, value -> {
                    draftSceneId = value;
                    clearStatusMessages();
                }),
                "ponderer.ui.scene_desc.scene_id",
                "ponderer.ui.scene_desc.id_hint",
                "ponderer.ui.scene_desc.hint.scene_id",
                -1));
        }

        entries.add(FieldSpecs.toggle(
            "ponderer.ui.scene_desc.editable",
            "ponderer.ui.scene_desc.editable.tooltip",
            () -> workingEditable,
            this::toggleEditable));
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();

        String newPonderId = draftPonderId.trim();
        if (!validatePonderId(newPonderId) || !validateSceneId()) {
            return false;
        }

        DslScene candidate = SceneStore.copyScene(scene);
        if (candidate == null) {
            setErrorMessage(UIText.of("ponderer.ui.scene_desc.error.prepare_copy"));
            return false;
        }

        candidate.title = copyLocalizedText(workingPonderTitle);
        candidate.editable = workingEditable;
        if (!newPonderId.isEmpty()) {
            candidate.id = newPonderId;
        }

        if (hasMultiScene && candidate.scenes != null && workingSceneTitle != null) {
            candidate.scenes.get(sceneIndex).title = copyLocalizedText(workingSceneTitle);
            String newSceneId = draftSceneId.trim();
            if (!newSceneId.isEmpty()) {
                candidate.scenes.get(sceneIndex).id = newSceneId;
            }
        }

        SceneStore.LocalSaveResult saveResult = SceneStore.saveSceneToLocalDetailed(candidate);
        if (!saveResult.isSuccess()) {
            setErrorMessage(UIText.saveError(saveResult));
            return false;
        }

        applySavedScene(candidate);
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);

        syncStateFromScene();
        markStateSaved();
        rebuildEntries(currentListScroll());
        setInfoMessage(UIText.of("ponderer.ui.scene_desc.saved"));
        return true;
    }

    @Override
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("ponder_title_plain", String.valueOf(workingPonderTitle.isPlain()));
        writeLocalizedSnapshot(snapshot, "ponder_title", workingPonderTitle);
        snapshot.put("ponder_id", draftPonderId);
        snapshot.put("scene_title_present", String.valueOf(workingSceneTitle != null));
        if (workingSceneTitle != null) {
            snapshot.put("scene_title_plain", String.valueOf(workingSceneTitle.isPlain()));
            writeLocalizedSnapshot(snapshot, "scene_title", workingSceneTitle);
        }
        snapshot.put("scene_id", draftSceneId);
        snapshot.put("editable", String.valueOf(workingEditable));
        return snapshot;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        workingPonderTitle = readLocalizedSnapshot(snapshot, "ponder_title");
        draftPonderId = snapshot.getOrDefault("ponder_id", "");

        boolean hasSceneTitle = Boolean.parseBoolean(snapshot.getOrDefault("scene_title_present", "false"));
        workingSceneTitle = hasSceneTitle ? readLocalizedSnapshot(snapshot, "scene_title") : null;
        draftSceneId = snapshot.getOrDefault("scene_id", "");
        workingEditable = Boolean.parseBoolean(snapshot.getOrDefault("editable", "true"));
    }

    private boolean validatePonderId(String newPonderId) {
        if (newPonderId.isEmpty() || newPonderId.equals(scene.id)) {
            return true;
        }
        for (DslScene existingScene : SceneRuntime.getScenes()) {
            if (existingScene != scene && newPonderId.equals(existingScene.id)) {
                setErrorMessage(Component.translatable(
                    "ponderer.ui.scene_desc.error.ponder_id_exists",
                    newPonderId).getString());
                return false;
            }
        }
        return true;
    }

    private boolean validateSceneId() {
        if (!hasMultiScene) {
            return true;
        }

        String newSceneId = draftSceneId.trim();
        DslScene.SceneSegment currentScene = scene.scenes.get(sceneIndex);
        if (newSceneId.isEmpty() || newSceneId.equals(currentScene.id)) {
            return true;
        }

        for (int i = 0; i < scene.scenes.size(); i++) {
            if (i != sceneIndex && newSceneId.equals(scene.scenes.get(i).id)) {
                setErrorMessage(Component.translatable(
                    "ponderer.ui.scene_desc.error.scene_id_exists",
                    newSceneId).getString());
                return false;
            }
        }
        return true;
    }

    private void applySavedScene(DslScene candidate) {
        scene.title = candidate.title;
        scene.id = candidate.id;
        if (hasMultiScene && candidate.scenes != null && scene.scenes != null
            && sceneIndex >= 0 && sceneIndex < candidate.scenes.size() && sceneIndex < scene.scenes.size()) {
            scene.scenes.get(sceneIndex).title = candidate.scenes.get(sceneIndex).title;
            scene.scenes.get(sceneIndex).id = candidate.scenes.get(sceneIndex).id;
        }
        scene.editable = candidate.editable;
    }

    private void syncStateFromScene() {
        originalPonderTitle = copyLocalizedText(scene.title);
        workingPonderTitle = copyLocalizedText(scene.title);
        originalPonderId = scene.id != null ? scene.id : "";
        draftPonderId = originalPonderId;
        workingEditable = scene.isEditable(Config.DEFAULT_EDITABLE.get());

        if (hasMultiScene && scene.scenes != null && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            DslScene.SceneSegment currentScene = scene.scenes.get(sceneIndex);
            originalSceneTitle = copyLocalizedText(currentScene.title);
            workingSceneTitle = copyLocalizedText(currentScene.title);
            originalSceneId = currentScene.id != null ? currentScene.id : "";
            draftSceneId = originalSceneId;
        } else {
            originalSceneTitle = null;
            workingSceneTitle = null;
            originalSceneId = "";
            draftSceneId = "";
        }
    }

    private void toggleEditable() {
        if (!workingEditable) {
            workingEditable = true;
            clearStatusMessages();
            return;
        }

        if (Config.DEVELOPER_MODE.get()) {
            workingEditable = false;
            clearStatusMessages();
            return;
        }

        new ConfirmationScreen()
            .centered()
            .withText(Component.translatable("ponderer.ui.scene_desc.editable.confirm_title"))
            .addText(Component.translatable("ponderer.ui.scene_desc.editable.confirm"))
            .withAction(confirmed -> {
                if (confirmed) {
                    workingEditable = false;
                    clearStatusMessages();
                }
            })
            .open(this);
    }

    private void togglePonderTitleLang() {
        ponderTitleLang = nextLang(ponderTitleLang);
        if (ponderTitleEntry != null) {
            ponderTitleEntry.setValue(localizedValue(workingPonderTitle, ponderTitleLang));
        }
        clearStatusMessages();
    }

    private void toggleSceneTitleLang() {
        if (workingSceneTitle == null) {
            return;
        }
        sceneTitleLang = nextLang(sceneTitleLang);
        if (sceneTitleEntry != null) {
            sceneTitleEntry.setValue(localizedValue(workingSceneTitle, sceneTitleLang));
        }
        clearStatusMessages();
    }

    private static String nextLang(String current) {
        String minecraftLang = getCurrentLang();
        if ("en_us".equals(current) && !"en_us".equals(minecraftLang)) {
            return minecraftLang;
        }
        return "en_us";
    }

    private static String localizedValue(@Nullable LocalizedText text, String lang) {
        if (text == null) {
            return "";
        }
        String exact = text.getExact(lang);
        return exact != null ? exact : "";
    }

    private static String getCurrentLang() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Exception e) {
            return "en_us";
        }
    }

    private static LocalizedText copyLocalizedText(@Nullable LocalizedText text) {
        if (text == null) {
            return LocalizedText.of("");
        }
        return text.isPlain()
            ? LocalizedText.of(text.resolve())
            : LocalizedText.ofMap(new LinkedHashMap<>(text.getAllTranslations()));
    }

    private static void writeLocalizedSnapshot(Map<String, String> snapshot, String prefix, LocalizedText text) {
        snapshot.put(prefix + "_count", String.valueOf(text.getAllTranslations().size()));
        int index = 0;
        for (Map.Entry<String, String> entry : text.getAllTranslations().entrySet()) {
            snapshot.put(prefix + "_lang_" + index, entry.getKey());
            snapshot.put(prefix + "_value_" + index, entry.getValue());
            index++;
        }
    }

    private static LocalizedText readLocalizedSnapshot(Map<String, String> snapshot, String prefix) {
        int count = 0;
        try {
            count = Integer.parseInt(snapshot.getOrDefault(prefix + "_count", "0"));
        } catch (NumberFormatException ignored) {
        }

        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String lang = snapshot.get(prefix + "_lang_" + i);
            String value = snapshot.get(prefix + "_value_" + i);
            if (lang != null && value != null) {
                values.put(lang, value);
            }
        }
        return values.isEmpty() ? LocalizedText.of("") : LocalizedText.ofMap(values);
    }
}
