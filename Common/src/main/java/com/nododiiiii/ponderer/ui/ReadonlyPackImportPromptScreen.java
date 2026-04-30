package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PonderPackInfo;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.ponder.foundation.PonderIndex;
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

        SceneStore.PackImportResult result = SceneStore.importPackFromResourcePack(info.sourcePath);
        if (!result.isSuccess()) {
            dialog.closeToSource();
            String key = result.uiMessageKey();
            notifyUser(key == null || key.isBlank()
                ? Component.literal(result.englishMessage())
                : Component.literal(UIText.of(key, result.uiMessageArgs())));
            return;
        }

        SceneStore.autoLoadPonderPacks();
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);

        DslScene importedScene = SceneRuntime.findByKey(sceneKey);
        notifyUser(Component.literal(UIText.of(result.uiMessageKey(), result.uiMessageArgs())));
        if (importedScene == null) {
            dialog.closeToSource();
            return;
        }

        SceneEditorScreen.markUiToEditorTransition(importedScene);
        Minecraft.getInstance().setScreen(new SceneEditorScreen(importedScene, sceneIndex));
    }

    @Nullable
    private static PonderPackInfo findSourcePack(String packId) {
        for (PonderPackInfo info : SceneStore.scanAvailableSourcePacks()) {
            if (packId.equals(info.name)) {
                return info;
            }
        }
        return null;
    }

    private void notifyUser(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }
}
