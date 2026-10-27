package com.nododiiiii.ponderer.blueprint;

import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.editor.screen.FieldBindings;
import com.nododiiiii.ponderer.editor.screen.FieldSpecs;
import com.nododiiiii.ponderer.editor.screen.UILayoutConstants;
import com.nododiiiii.ponderer.editor.screen.UIText;
import com.nododiiiii.ponderer.editor.screen.widgets.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.editor.screen.widgets.DeclarativeFormEntry;
import net.minecraft.client.Minecraft;

import java.util.List;

public class BlueprintPromptScreen extends AbstractDeclarativeFormScreen {

    private String blueprintName = "";
    private String baselineName = "";
    private boolean awaitingOverrideConfirm = false;
    private boolean saved;

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
        if (saved) {
            entries.add(screen -> screen.createSectionHeaderEntry(blueprintName));
            return;
        }
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
        return !saved && (!blueprintName.trim().equals(baselineName.trim()) || awaitingOverrideConfirm);
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
    protected boolean isSaveButtonActive() { return !saved; }

    @Override
    protected boolean saveEdits() {
        if (saved) return true;
        String name = blueprintName.trim();
        if (!awaitingOverrideConfirm && SceneStore.isBuiltinStructureName(name)) {
            awaitingOverrideConfirm = true;
            setErrorMessage(UIText.of("ponderer.ui.blueprint.prompt.override_warn"));
            return false;
        }

        BlueprintExport.SaveResult result = BlueprintHandler.INSTANCE.saveBlueprint(blueprintName);
        if (result.isSuccess()) {
            saved = true;
            awaitingOverrideConfirm = false;
            baselineName = blueprintName;
            rebuildListPreservingScroll();
            String file = result.exportResult().file().getFileName().toString();
            setInfoMessage(UIText.of("item.ponderer.blueprint.saved", "ponderer:" + file.replaceFirst("\\.nbt$", "")));
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
        rebuildListPreservingScroll();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void discardBlueprint() {
        BlueprintHandler.INSTANCE.discard();
        Minecraft.getInstance().setScreen(null);
    }
}
