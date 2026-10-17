package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.PonderTagListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.client.gui.screens.Screen;
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
import java.util.function.Consumer;

/** Edits the description form's draft; the parent owns persistence and index reload. */
public class SceneTagsScreen extends AbstractDeclarativeListScreen {
    private final Set<String> originalTags;
    private final Set<String> selectedTags;
    private final Consumer<List<String>> onApply;
    private final Map<String, PonderTag> availableTags = new LinkedHashMap<>();

    public SceneTagsScreen(Screen parent, List<String> tags, Consumer<List<String>> onApply) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.scene_tags", 400);
        this.originalTags = new LinkedHashSet<>(tags);
        this.selectedTags = new LinkedHashSet<>(tags);
        this.onApply = onApply;

        PonderIndex.getTagAccess().getListedTags().stream()
            .sorted(Comparator.comparing((PonderTag tag) -> tag.getTitle().toLowerCase(Locale.ROOT))
                .thenComparing(tag -> tag.getId().toString()))
            .forEach(tag -> availableTags.put(tag.getId().toString(), tag));
        // Keep unlisted and unavailable saved IDs visible, so they can be retained or removed.
        for (String id : originalTags) {
            availableTags.putIfAbsent(id, resolveTag(id));
        }
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new SectionHeaderListEntry(
            () -> UIText.of("ponderer.ui.scene_tags.selected_count", selectedTags.size())));
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (availableTags.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.scene_tags.empty")));
            return;
        }
        for (Map.Entry<String, PonderTag> entry : availableTags.entrySet()) {
            String id = entry.getKey();
            entries.add(new PonderTagListEntry(id, entry.getValue(),
                () -> selectedTags.contains(id), () -> toggleTag(id)));
        }
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return !selectedTags.equals(originalTags);
    }

    @Override
    protected int getUnsavedChangeCount() {
        Set<String> changed = new LinkedHashSet<>(selectedTags);
        changed.removeAll(originalTags);
        Set<String> removed = new LinkedHashSet<>(originalTags);
        removed.removeAll(selectedTags);
        return changed.size() + removed.size();
    }

    @Override
    protected boolean saveEdits() {
        onApply.accept(new ArrayList<>(selectedTags));
        originalTags.clear();
        originalTags.addAll(selectedTags);
        ScreenOpener.open(parent);
        return true;
    }

    @Override
    protected void discardEdits() {
        selectedTags.clear();
        selectedTags.addAll(originalTags);
        clearStatusMessages();
    }

    private void toggleTag(String id) {
        if (!selectedTags.remove(id)) {
            selectedTags.add(id);
        }
        clearStatusMessages();
    }

    public static String summarize(List<String> tags) {
        if (tags.isEmpty()) {
            return UIText.of("ponderer.ui.scene_tags.none");
        }
        List<String> titles = new ArrayList<>();
        for (String id : tags) {
            PonderTag tag = resolveTag(id);
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
