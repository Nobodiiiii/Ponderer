package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class StructureListScreen extends AbstractReadonlyDeclarativeListScreen {

    private final Screen parent;
    private final Consumer<String> onSelect;
    private final List<String> allStructures = new ArrayList<>();

    public StructureListScreen(Screen parent, Consumer<String> onSelect) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.scene_desc.structure_list_title", UILayoutConstants.EDITOR_LIST_W);
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        reloadStructures();
        super.init();
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (allStructures.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.structure_list.empty")));
            return;
        }

        for (String structureId : allStructures) {
            entries.add(new FullButtonListEntry(structureId, null, () -> {
                onSelect.accept(structureId);
                Minecraft.getInstance().setScreen(parent);
            }));
        }
    }

    @Override
    protected int getEntryHeight() {
        return 40;
    }

    private void reloadStructures() {
        allStructures.clear();
        try {
            Registry<?> registry = null;
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            }
            if (registry == null || registry.keySet().isEmpty()) {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    registry = connection.registryAccess().registryOrThrow(Registries.STRUCTURE);
                }
            }
            if (registry != null) {
                for (var key : registry.keySet()) {
                    allStructures.add(key.toString());
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(allStructures);
    }
}
