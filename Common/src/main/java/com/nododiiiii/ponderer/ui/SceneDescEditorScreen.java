package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Editor screen for ponder scene description: titles and IDs.
 * Trigger settings are handled by {@link TriggerEditorScreen}.
 */
public class SceneDescEditorScreen extends AbstractStepEditorScreen {

    // Ponder title
    private FieldWithLang ponderTitleRow;
    private String ponderTitleLang;
    private LocalizedText workingPonderTitle;

    // Scene title (only when scene.scenes[] mode)
    private boolean hasMultiScene;
    private FieldWithLang sceneTitleRow;
    private String sceneTitleLang;
    private LocalizedText workingSceneTitle;

    // IDs
    private HintableTextFieldWidget ponderIdField;
    private HintableTextFieldWidget sceneIdField;

    public SceneDescEditorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.scene_desc"), scene, sceneIndex, parent);

        this.ponderTitleLang = getCurrentLang();
        this.workingPonderTitle = scene.title != null ? scene.title : LocalizedText.of("");

        this.hasMultiScene = scene.scenes != null && !scene.scenes.isEmpty()
                && sceneIndex >= 0 && sceneIndex < scene.scenes.size();
        if (hasMultiScene) {
            this.sceneTitleLang = getCurrentLang();
            DslScene.SceneSegment sc = scene.scenes.get(sceneIndex);
            this.workingSceneTitle = sc.title != null ? sc.title : LocalizedText.of("");
        }
    }

    @Override
    protected boolean showsKeyFrame() { return false; }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.scene_desc");
    }

    @Override
    protected int getFormRowCount() {
        int rows = 1; // ponder title
        if (hasMultiScene) rows += 1; // scene title
        rows += 1; // ponder ID
        if (hasMultiScene) rows += 1; // scene ID
        return rows;
    }

    @Override
    protected void init() {
        super.init();
        confirmButton.withCallback(this::doConfirm);
    }

    @Override
    protected void buildForm() {
        beginForm();

        // ---- Ponder title with lang toggle ----
        ponderTitleRow = addFormTextFieldWithLang(
                "ponderer.ui.scene_desc.ponder_title", null,
                UIText.of("ponderer.ui.scene_desc.hint.ponder_title"),
                104,
                () -> ponderTitleLang,
                this::togglePonderTitleLang);
        String val = workingPonderTitle.getExact(ponderTitleLang);
        ponderTitleRow.field().setValue(val != null ? val : workingPonderTitle.resolve());

        // ---- Scene title with lang toggle ----
        if (hasMultiScene) {
            sceneTitleRow = addFormTextFieldWithLang(
                    "ponderer.ui.scene_desc.scene_title", null,
                    UIText.of("ponderer.ui.scene_desc.hint.scene_title"),
                    104,
                    () -> sceneTitleLang,
                    this::toggleSceneTitleLang);
            String scVal = workingSceneTitle.getExact(sceneTitleLang);
            sceneTitleRow.field().setValue(scVal != null ? scVal : workingSceneTitle.resolve());
        }

        // ---- Ponder ID (warning-colored label) ----
        addFormLabel("ponderer.ui.scene_desc.ponder_id", "ponderer.ui.scene_desc.id_hint", 0xFFFF00);
        ponderIdField = createTextField(fieldX(), formY(), 141, 18,
                UIText.of("ponderer.ui.scene_desc.hint.ponder_id"));
        ponderIdField.setValue(scene.id != null ? scene.id : "");
        nextFormRow();

        // ---- Scene segment ID (warning-colored label) ----
        if (hasMultiScene) {
            addFormLabel("ponderer.ui.scene_desc.scene_id", null, 0xFFFF00);
            sceneIdField = createTextField(fieldX(), formY(), 141, 18,
                    UIText.of("ponderer.ui.scene_desc.hint.scene_id"));
            DslScene.SceneSegment sc = scene.scenes.get(sceneIndex);
            sceneIdField.setValue(sc.id != null ? sc.id : "");
            nextFormRow();
        }
    }

    @Override
    protected String getStepType() {
        return "scene_desc";
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        return null;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("ponderTitle") && ponderTitleRow != null)
            ponderTitleRow.field().setValue(snapshot.get("ponderTitle"));
        if (snapshot.containsKey("ponderTitleLang"))
            ponderTitleLang = snapshot.get("ponderTitleLang");
        if (hasMultiScene && sceneTitleRow != null && snapshot.containsKey("sceneTitle"))
            sceneTitleRow.field().setValue(snapshot.get("sceneTitle"));
        if (snapshot.containsKey("sceneTitleLang"))
            sceneTitleLang = snapshot.get("sceneTitleLang");
        if (snapshot.containsKey("ponderId") && ponderIdField != null)
            ponderIdField.setValue(snapshot.get("ponderId"));
        if (hasMultiScene && sceneIdField != null && snapshot.containsKey("sceneId"))
            sceneIdField.setValue(snapshot.get("sceneId"));
    }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        if (ponderTitleRow != null) m.put("ponderTitle", ponderTitleRow.field().getValue());
        m.put("ponderTitleLang", ponderTitleLang);
        if (hasMultiScene && sceneTitleRow != null) {
            m.put("sceneTitle", sceneTitleRow.field().getValue());
            m.put("sceneTitleLang", sceneTitleLang);
        }
        if (ponderIdField != null) m.put("ponderId", ponderIdField.getValue());
        if (hasMultiScene && sceneIdField != null) m.put("sceneId", sceneIdField.getValue());
        return m;
    }

    // ---- Confirm / Save ----

    private void doConfirm() {
        errorMessage = null;

        String newPonderId = ponderIdField.getValue().trim();
        if (!newPonderId.isEmpty() && !newPonderId.equals(scene.id)) {
            for (DslScene s : SceneRuntime.getScenes()) {
                if (s != scene && newPonderId.equals(s.id)) {
                    errorMessage = Component.translatable("ponderer.ui.scene_desc.error.ponder_id_exists",
                            newPonderId).getString();
                    return;
                }
            }
        }

        if (hasMultiScene && sceneIdField != null) {
            String newSceneId = sceneIdField.getValue().trim();
            DslScene.SceneSegment currentSeg = scene.scenes.get(sceneIndex);
            if (!newSceneId.isEmpty() && !newSceneId.equals(currentSeg.id)) {
                for (int i = 0; i < scene.scenes.size(); i++) {
                    if (i != sceneIndex && newSceneId.equals(scene.scenes.get(i).id)) {
                        errorMessage = Component.translatable("ponderer.ui.scene_desc.error.scene_id_exists",
                                newSceneId).getString();
                        return;
                    }
                }
            }
        }

        // ---- Save all fields ----

        String pTitle = ponderTitleRow.field().getValue();
        if (!pTitle.isEmpty()) {
            workingPonderTitle.setForLang(ponderTitleLang, pTitle);
        }
        scene.title = workingPonderTitle;

        if (hasMultiScene && sceneTitleRow != null) {
            String scTitle = sceneTitleRow.field().getValue();
            if (!scTitle.isEmpty()) {
                workingSceneTitle.setForLang(sceneTitleLang, scTitle);
            }
            scene.scenes.get(sceneIndex).title = workingSceneTitle;
        }

        if (!newPonderId.isEmpty()) {
            scene.id = newPonderId;
        }

        if (hasMultiScene && sceneIdField != null) {
            String newSceneId = sceneIdField.getValue().trim();
            if (!newSceneId.isEmpty()) {
                scene.scenes.get(sceneIndex).id = newSceneId;
            }
        }

        SceneStore.saveSceneToLocal(scene);
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);
        returnToParent();
    }

    // ---- Language toggle logic ----

    private void togglePonderTitleLang() {
        if (ponderTitleRow == null) return;
        String currentText = ponderTitleRow.field().getValue();
        if (!currentText.isEmpty()) {
            workingPonderTitle.setForLang(ponderTitleLang, currentText);
        }
        ponderTitleLang = nextLang(ponderTitleLang);
        String val = workingPonderTitle.getExact(ponderTitleLang);
        ponderTitleRow.field().setValue(val != null ? val : "");
    }

    private void toggleSceneTitleLang() {
        if (sceneTitleRow == null) return;
        String currentText = sceneTitleRow.field().getValue();
        if (!currentText.isEmpty()) {
            workingSceneTitle.setForLang(sceneTitleLang, currentText);
        }
        sceneTitleLang = nextLang(sceneTitleLang);
        String val = workingSceneTitle.getExact(sceneTitleLang);
        sceneTitleRow.field().setValue(val != null ? val : "");
    }

    private String nextLang(String current) {
        String mcLang = getCurrentLang();
        if (current.equals("en_us") && !"en_us".equals(mcLang)) {
            return mcLang;
        }
        return "en_us";
    }

    private static String getCurrentLang() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Exception e) {
            return "en_us";
        }
    }
}
