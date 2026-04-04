package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.PonderPackInfo;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class ImportPackScreen extends AbstractReadonlyDeclarativeListScreen {

    private final List<PonderPackInfo> availablePacks = new ArrayList<>();

    public ImportPackScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.function_page.import.title", 360);
    }

    @Override
    protected void init() {
        scanResourcePacks();
        super.init();
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (availablePacks.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.import.none")));
            return;
        }

        entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.import.available")));
        for (PonderPackInfo pack : availablePacks) {
            String label = pack.name + " v" + pack.version + (pack.author.isBlank() ? "" : " | " + pack.author);
            entries.add(new FullButtonListEntry(
                label,
                pack.sourcePath.toString(),
                () -> loadPack(pack)));
        }
    }

    @Override
    protected int getEntryHeight() {
        return 34;
    }

    private void scanResourcePacks() {
        availablePacks.clear();
        Path resourcepacksDir = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");
        if (!Files.exists(resourcepacksDir)) {
            return;
        }

        try (Stream<Path> paths = Files.list(resourcepacksDir)) {
            paths.filter(path -> path.toString().toLowerCase().endsWith(".zip"))
                .map(PonderPackInfo::fromZip)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(info -> info.name.toLowerCase()))
                .forEach(availablePacks::add);
        } catch (IOException ignored) {
        }
    }

    private void loadPack(PonderPackInfo pack) {
        try {
            SceneStore.PackUpdateInfo result = SceneStore.loadPonderPackFromResourcePack(pack.sourcePath, true);
            int count = result != null ? result.totalFiles : 0;
            SceneStore.reloadFromDisk();
            Minecraft.getInstance().execute(PonderIndex::reload);
            notifyUser(UIText.of("ponderer.ui.import.success", count, pack.name));
            Minecraft.getInstance().setScreen(new FunctionScreen());
        } catch (Exception e) {
            notifyUser(UIText.of("ponderer.ui.import.failed", e.getMessage()));
        }
    }

    private void notifyUser(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
        }
    }
}
