package com.nododiiiii.ponderer.application.ui;

import com.google.gson.*;
import com.nododiiiii.ponderer.application.api.RuntimeAccess;
import com.nododiiiii.ponderer.application.ui.widgets.DeclarativeFormEntry;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.support.ui.UIText;
import net.minecraft.network.chat.Component;
import java.util.List;

/** A provider's codec owns parameter validation; opaque Java never enters this form. */
final class CustomStepScreen extends AbstractStepEditorScreen {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(com.nododiiiii.ponderer.scene.LocalizedText.class,
            new com.nododiiiii.ponderer.scene.LocalizedText.GsonAdapter()).create();
    private final String type;
    private final StepTextFieldHandle parameters = new StepTextFieldHandle("parameters");

    CustomStepScreen(String type, DslScene scene, int sceneIndex, SceneEditorScreen parent,
                     int stepIndex, DslScene.DslStep step) {
        super(Component.literal(type), scene, sceneIndex, parent, stepIndex, step);
        this.type = type;
        parameters.setValue("{}");
    }
    @Override protected String getStepType() { return type; }
    @Override protected String getHeaderTitle() { return RuntimeAccess.engineFor(scene).steps().editorLabel(type); }
    @Override protected void collectStepEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(parameters, "ponderer.ui.custom_step.parameters",
            "ponderer.ui.custom_step.parameters.tooltip", null, 240));
    }
    @Override protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        parameters.setValue(step.parameters == null ? "{}" : step.parameters.toString());
    }
    @Override protected DslScene.DslStep buildStep() {
        clearStatusMessages();
        try {
            JsonObject data = JsonParser.parseString(parameters.getValue()).getAsJsonObject();
            var registry = RuntimeAccess.engineFor(scene).steps();
            if (!registry.editableParameters(type)) throw new IllegalArgumentException(UIText.of("ponderer.ui.custom_step.opaque"));
            registry.validateParameters(type, data);
            DslScene.DslStep step = existingStep == null ? new DslScene.DslStep()
                : GSON.fromJson(GSON.toJson(existingStep), DslScene.DslStep.class);
            step.type = type;
            step.parameters = data;
            step.attachKeyFrame = null;
            return step;
        } catch (RuntimeException error) {
            setErrorMessage(UIText.of("ponderer.ui.custom_step.invalid", error.getMessage()));
            return null;
        }
    }
}
