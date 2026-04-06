package com.nododiiiii.ponderer.blueprint;

import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.FieldBindings;
import com.nododiiiii.ponderer.ui.FieldSpecs;
import com.nododiiiii.ponderer.ui.UILayoutConstants;
import com.nododiiiii.ponderer.ui.UIText;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.client.Minecraft;

import java.util.List;

public class BlueprintPromptScreen extends AbstractDeclarativeFormScreen {

    private String blueprintName = "";
    private String baselineName = "";
    private boolean awaitingOverrideConfirm = false;

    public BlueprintPromptScreen() {
        super(null, "ponderer.ui.scope.editor", "ponderer.ui.blueprint.prompt.title", UILayoutConstants.EDITOR_LIST_W);
    }

    @Override
    protected void init() {
        super.init();
        if (discardChanges != null) {
            discardChanges.withCallback(this::discardBlueprint);
            discardChanges.active = true;
        }
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> blueprintName, value -> {
                blueprintName = value;
                awaitingOverrideConfirm = false;
                clearStatusMessages();
            }),
            "ponderer.ui.blueprint.prompt.name",
            null,
            "ponderer.ui.blueprint.prompt.name",
            -1));
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return !blueprintName.trim().equals(baselineName.trim()) || awaitingOverrideConfirm;
    }

    @Override
    protected int getUnsavedChangeCount() {
        return hasUnsavedChanges() ? 1 : 0;
    }

    @Override
    protected boolean isDiscardButtonActive() {
        return true;
    }

    @Override
    protected boolean saveEdits() {
        String name = blueprintName.trim();
        if (!awaitingOverrideConfirm && SceneStore.isBuiltinStructureName(name)) {
            awaitingOverrideConfirm = true;
            setErrorMessage(UIText.of("ponderer.ui.blueprint.prompt.override_warn"));
            return false;
        }

        BlueprintExport.SaveResult result = BlueprintHandler.INSTANCE.saveBlueprint(blueprintName);
        if (result.isSuccess()) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }

        awaitingOverrideConfirm = false;
        setErrorMessage(UIText.of(result.uiMessageKey(), result.uiMessageArgs()));
        return false;
    }

    @Override
    protected void discardEdits() {
        awaitingOverrideConfirm = false;
        blueprintName = baselineName;
        clearStatusMessages();
        rebuildEntries(currentListScroll());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void discardBlueprint() {
        BlueprintHandler.INSTANCE.discard();
        Minecraft.getInstance().setScreen(null);
    }
}
