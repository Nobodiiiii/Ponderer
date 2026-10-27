package com.nododiiiii.ponderer.application.ui;

import com.nododiiiii.ponderer.support.ui.UIText;
import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.workspace.api.PonderPackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ReadonlyPackImportPromptScreen {

    private final Screen source;
    private final String packId;
    private final String sceneKey;
    private final int sceneIndex;

    public ReadonlyPackImportPromptScreen(@Nonnull Screen source, String packId, String sceneKey, int sceneIndex) {
        this.source = source;
        this.packId = packId;
        this.sceneKey = sceneKey;
        this.sceneIndex = sceneIndex;
    }

    public void open() {
        new PondererDialogScreen(
            source,
            List.of(Component.translatable("ponderer.ui.readonly_import_prompt.title")),
            List.of(Component.translatable("ponderer.ui.readonly_import_prompt.message")),
            List.of(
                PondererDialogScreen.button(
                    Component.translatable("ponderer.ui.readonly_import_prompt.import", "[" + packId + "]"),
                    this::importAndEdit),
                PondererDialogScreen.closeButton(Component.translatable("ponderer.ui.cancel"))))
            .open();
    }

    private void importAndEdit(PondererDialogScreen dialog) {
        PonderPackInfo info = findSourcePack(packId);
        if (info == null || info.sourcePath == null) {
            dialog.closeToSource();
            notifyUser(Component.literal(UIText.of(
                "ponderer.ui.import.failed",
                UIText.of("ponderer.ui.readonly_import_prompt.missing_source", packId))));
            return;
        }

        com.nododiiiii.ponderer.workspace.api.WorkspaceResults.PackImportResult result = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().packs().importPackFromResourcePack(info.sourcePath);
        if (!result.isSuccess()) {
            dialog.closeToSource();
            String key = result.uiMessageKey();
            notifyUser(key == null || key.isBlank()
                ? Component.literal(result.englishMessage())
                : Component.literal(UIText.of(key, result.uiMessageArgs())));
            return;
        }

        com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().packs().autoLoadPonderPacks();
        try {
            com.nododiiiii.ponderer.application.SceneRefreshCoordinator.refresh();
        } catch (RuntimeException failure) {
            com.nododiiiii.ponderer.application.SceneRefreshCoordinator.reportFailure(failure);
            dialog.closeToSource();
            notifyUser(Component.translatable("ponderer.refresh.failed", failure.getMessage()));
            return;
        }

        DslScene importedScene = com.nododiiiii.ponderer.application.api.RuntimeAccess.engine().catalog().findByKey(sceneKey);
        if (importedScene == null) {
            dialog.closeToSource();
            return;
        }

        SceneEditorScreen.markUiToEditorTransition(importedScene);
        SceneEditorScreen editor = new SceneEditorScreen(importedScene, sceneIndex);
        editor.acceptFeedback(Component.translatable(result.uiMessageKey(), result.uiMessageArgs()), FeedbackLevel.SUCCESS);
        Minecraft.getInstance().setScreen(editor);
    }

    @Nullable
    private static PonderPackInfo findSourcePack(String packId) {
        for (PonderPackInfo info : com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().packs().scanAvailableSourcePacks()) {
            if (packId.equals(info.name)) {
                return info;
            }
        }
        return null;
    }

    private void notifyUser(Component message) {
        new PondererDialogScreen(source, List.of(Component.translatable("ponderer.ui.readonly_import_prompt.title")),
            List.of(message), List.of(PondererDialogScreen.closeButton(Component.translatable("ponderer.ui.confirm")))).open();
    }
}
