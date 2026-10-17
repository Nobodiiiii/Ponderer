package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.ActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.PonderTagListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import com.nododiiiii.ponderer.ponder.CustomPonderTags;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Edits the description form's draft; the parent owns persistence and index reload. */
public class SceneTagsScreen extends AbstractDeclarativeListScreen {
    private final Set<String> originalTags;
    private final Set<String> selectedTags;
    private final DslScene owner;
    private String originalDefinitions;
    private Map<String, DslScene.CustomTag> customTags;
    private final BiConsumer<List<String>, Map<String, DslScene.CustomTag>> onApply;
    private final Map<String, PonderTag> availableTags = new LinkedHashMap<>();

    public SceneTagsScreen(Screen parent, DslScene owner, List<String> tags,
                           Map<String, DslScene.CustomTag> definitions,
                           BiConsumer<List<String>, Map<String, DslScene.CustomTag>> onApply) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.scene_tags", 400);
        this.originalTags = new LinkedHashSet<>(tags);
        this.selectedTags = new LinkedHashSet<>(tags);
        this.owner = owner;
        this.customTags = CustomPonderTags.copy(definitions);
        this.originalDefinitions = CustomPonderTags.encode(customTags);
        this.onApply = onApply;
        refreshAvailableTags();
    }

    private void refreshAvailableTags() {
        Map<String, PonderTag> tags = new LinkedHashMap<>();
        PonderIndex.getTagAccess().getListedTags().stream()
            .filter(tag -> !CustomPonderTags.ownedBy(owner).containsKey(tag.getId().toString()))
            .forEach(tag -> tags.put(tag.getId().toString(), tag));
        CustomPonderTags.collect(SceneRuntime.getScenes().stream().filter(this::isOtherScene).toList())
            .forEach((id, definition) -> tags.put(id, CustomPonderTags.preview(id, definition)));
        customTags.forEach((id, definition) -> {
            if (CustomPonderTags.isCustomId(id) && definition != null) {
                tags.put(id, CustomPonderTags.preview(id, definition));
            }
        });
        availableTags.clear();
        tags.values().stream()
            .sorted(Comparator.comparing((PonderTag tag) -> tag.getTitle().toLowerCase(Locale.ROOT))
                .thenComparing(tag -> tag.getId().toString()))
            .forEach(tag -> availableTags.put(tag.getId().toString(), tag));
        // Keep unlisted and unavailable saved IDs visible, so they can be retained or removed.
        for (String id : selectedTags) {
            if (!availableTags.containsKey(id)) {
                availableTags.put(id, CustomPonderTags.isCustomId(id) ? null : resolveTag(id));
            }
        }
    }

    private boolean isOtherScene(DslScene scene) {
        return scene != owner && !java.util.Objects.equals(scene.sceneKey(), owner.sceneKey());
    }

    @Nullable
    private DslScene findReference(String id) {
        for (DslScene scene : SceneRuntime.getScenes()) {
            if (isOtherScene(scene) && scene.tags != null && scene.tags.contains(id)) {
                return scene;
            }
        }
        return null;
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new SectionHeaderListEntry(
            () -> UIText.of("ponderer.ui.scene_tags.selected_count", selectedTags.size())));
        entries.add(new ActionStripListEntry(List.of(ActionStripListEntry.button(
            UIText.of("ponderer.ui.custom_tag.create"),
            UIText.of("ponderer.ui.custom_tag.scope"),
            () -> ScreenOpener.open(new CustomTagEditorScreen(this, null, null))))));
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (availableTags.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.scene_tags.empty")));
            return;
        }
        for (Map.Entry<String, PonderTag> entry : availableTags.entrySet()) {
            String id = entry.getKey();
            boolean editable = customTags.containsKey(id) && CustomPonderTags.isCustomId(id);
            entries.add(new PonderTagListEntry(id, entry.getValue(),
                () -> selectedTags.contains(id), () -> toggleTag(id),
                editable ? () -> ScreenOpener.open(new CustomTagEditorScreen(this, id, customTags.get(id))) : null,
                editable ? () -> deleteCustomTag(id) : null));
        }
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return !selectedTags.equals(originalTags)
            || !originalDefinitions.equals(CustomPonderTags.encode(customTags));
    }

    @Override
    protected int getUnsavedChangeCount() {
        Set<String> changed = new LinkedHashSet<>(selectedTags);
        changed.removeAll(originalTags);
        Set<String> removed = new LinkedHashSet<>(originalTags);
        removed.removeAll(selectedTags);
        return changed.size() + removed.size()
            + (originalDefinitions.equals(CustomPonderTags.encode(customTags)) ? 0 : 1);
    }

    @Override
    protected boolean saveEdits() {
        onApply.accept(new ArrayList<>(selectedTags), CustomPonderTags.copy(customTags));
        originalTags.clear();
        originalTags.addAll(selectedTags);
        originalDefinitions = CustomPonderTags.encode(customTags);
        ScreenOpener.open(parent);
        return true;
    }

    @Override
    protected void discardEdits() {
        selectedTags.clear();
        selectedTags.addAll(originalTags);
        customTags = CustomPonderTags.decode(originalDefinitions);
        clearStatusMessages();
        refreshAvailableTags();
        rebuildListPreservingScroll();
    }

    private void toggleTag(String id) {
        if (!selectedTags.remove(id)) {
            selectedTags.add(id);
        }
        clearStatusMessages();
    }

    @Nullable
    String updateCustomTag(@Nullable String oldId, String id, DslScene.CustomTag definition) {
        if (!id.equals(oldId) && (customTags.containsKey(id) || availableTags.get(id) != null)) {
            return UIText.of("ponderer.ui.custom_tag.error.exists", id);
        }
        if (!id.equals(oldId)) {
            String name = CustomPonderTags.editorName(id);
            boolean nameExists = java.util.stream.Stream.concat(customTags.keySet().stream(), availableTags.keySet().stream())
                .anyMatch(existing -> !java.util.Objects.equals(existing, oldId)
                    && CustomPonderTags.isCustomId(existing) && CustomPonderTags.editorName(existing).equals(name));
            if (nameExists) {
                return UIText.of("ponderer.ui.custom_tag.error.exists", id);
            }
        }
        for (DslScene scene : SceneRuntime.getScenes()) {
            if (isOtherScene(scene) && CustomPonderTags.ownedBy(scene).containsKey(id)) {
                return UIText.of("ponderer.ui.custom_tag.error.exists", id);
            }
        }
        if (oldId != null && !oldId.equals(id)) {
            DslScene reference = findReference(oldId);
            if (reference != null) {
                return UIText.of("ponderer.ui.custom_tag.error.in_use", reference.sceneKey());
            }
            customTags.remove(oldId);
        }
        customTags.put(id, definition);
        if (oldId == null || (!oldId.equals(id) && selectedTags.remove(oldId))) {
            selectedTags.add(id);
        }
        clearStatusMessages();
        refreshAvailableTags();
        return null;
    }

    private void deleteCustomTag(String id) {
        DslScene reference = findReference(id);
        if (reference != null) {
            setErrorMessage(UIText.of("ponderer.ui.custom_tag.error.in_use", reference.sceneKey()));
            return;
        }
        new ConfirmationScreen().centered()
            .withText(Component.translatable("ponderer.ui.custom_tag.delete"))
            .addText(Component.translatable("ponderer.ui.custom_tag.delete.confirm", id))
            .withAction(confirmed -> {
                if (confirmed) {
                    customTags.remove(id);
                    selectedTags.remove(id);
                    clearStatusMessages();
                    refreshAvailableTags();
                    rebuildListPreservingScroll();
                }
            }).open(this);
    }

    public static String summarize(List<String> tags, Map<String, DslScene.CustomTag> definitions) {
        if (tags.isEmpty()) {
            return UIText.of("ponderer.ui.scene_tags.none");
        }
        List<String> titles = new ArrayList<>();
        for (String id : tags) {
            DslScene.CustomTag definition = definitions.get(id);
            PonderTag tag = definition != null && CustomPonderTags.isCustomId(id)
                ? CustomPonderTags.preview(id, definition) : resolveTag(id);
            titles.add(tag == null ? id : tag.getTitle() + " (" + id + ")");
        }
        return String.join("\n", titles);
    }

    @Nullable
    private static PonderTag resolveTag(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        PonderTag tag = PonderIndex.getTagAccess().getRegisteredTag(location);
        return location.equals(tag.getId()) ? tag : null;
    }
}
