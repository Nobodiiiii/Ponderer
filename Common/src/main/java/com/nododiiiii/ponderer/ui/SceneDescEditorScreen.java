package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.ui.catnip.FormEntries;
import com.nododiiiii.ponderer.ui.catnip.LocalizedTextListEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public class SceneDescEditorScreen extends AbstractDeclarativeFormScreen {

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
        entries.add(FormEntries.localizedText(
            "ponderer.ui.scene_desc.ponder_title",
            null,
            "ponderer.ui.scene_desc.hint.ponder_title",
            localizedValue(workingPonderTitle, ponderTitleLang),
            () -> ponderTitleLang,
            this::togglePonderTitleLang,
            value -> {
                workingPonderTitle.setForLang(ponderTitleLang, value);
                clearStatusMessages();
            },
            entry -> ponderTitleEntry = entry));

        if (hasMultiScene && workingSceneTitle != null) {
            entries.add(FormEntries.localizedText(
                "ponderer.ui.scene_desc.scene_title",
                null,
                "ponderer.ui.scene_desc.hint.scene_title",
                localizedValue(workingSceneTitle, sceneTitleLang),
                () -> sceneTitleLang,
                this::toggleSceneTitleLang,
                value -> {
                    workingSceneTitle.setForLang(sceneTitleLang, value);
                    clearStatusMessages();
                },
                entry -> sceneTitleEntry = entry));
        } else {
            sceneTitleEntry = null;
        }

        entries.add(FormEntries.text(
            "ponderer.ui.scene_desc.ponder_id",
            "ponderer.ui.scene_desc.id_hint",
            "ponderer.ui.scene_desc.hint.ponder_id",
            draftPonderId,
            value -> {
                draftPonderId = value;
                clearStatusMessages();
            }));

        if (hasMultiScene) {
            entries.add(FormEntries.text(
                "ponderer.ui.scene_desc.scene_id",
                "ponderer.ui.scene_desc.id_hint",
                "ponderer.ui.scene_desc.hint.scene_id",
                draftSceneId,
                value -> {
                    draftSceneId = value;
                    clearStatusMessages();
                }));
        }
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return getUnsavedChangeCount() > 0;
    }

    @Override
    protected int getUnsavedChangeCount() {
        int dirtyFields = 0;
        if (!sameLocalizedText(workingPonderTitle, originalPonderTitle)) {
            dirtyFields++;
        }
        if (!Objects.equals(draftPonderId, originalPonderId)) {
            dirtyFields++;
        }
        if (hasMultiScene) {
            if (!sameLocalizedText(workingSceneTitle, originalSceneTitle)) {
                dirtyFields++;
            }
            if (!Objects.equals(draftSceneId, originalSceneId)) {
                dirtyFields++;
            }
        }
        return dirtyFields;
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
        rebuildEntries(currentListScroll());
        setInfoMessage(UIText.of("ponderer.ui.scene_desc.saved"));
        return true;
    }

    @Override
    protected void discardEdits() {
        clearStatusMessages();
        workingPonderTitle = copyLocalizedText(originalPonderTitle);
        draftPonderId = originalPonderId;

        if (hasMultiScene) {
            workingSceneTitle = copyLocalizedText(originalSceneTitle);
            draftSceneId = originalSceneId;
        }

        rebuildEntries(currentListScroll());
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
    }

    private void syncStateFromScene() {
        originalPonderTitle = copyLocalizedText(scene.title);
        workingPonderTitle = copyLocalizedText(scene.title);
        originalPonderId = scene.id != null ? scene.id : "";
        draftPonderId = originalPonderId;

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

    private static boolean sameLocalizedText(@Nullable LocalizedText left, @Nullable LocalizedText right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.isPlain() != right.isPlain()) {
            return false;
        }
        return left.getAllTranslations().equals(right.getAllTranslations());
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
}
