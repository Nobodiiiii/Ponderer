package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.CustomPonderTags;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/** A category draft; saving returns it to the tag selection screen. */
public class CustomTagEditorScreen extends AbstractInventoryPickerFormScreen {
    private final SceneTagsScreen tagScreen;
    @Nullable
    private final String originalId;
    private String draftName;
    private DslScene.CustomTag definition;
    private String language = Minecraft.getInstance().getLanguageManager().getSelected();

    public CustomTagEditorScreen(SceneTagsScreen parent, @Nullable String id, @Nullable DslScene.CustomTag initial) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.custom_tag", 380);
        tagScreen = parent;
        originalId = id;
        draftName = id == null ? "" : CustomPonderTags.editorName(id);
        definition = initial == null ? new DslScene.CustomTag()
            : CustomPonderTags.copy(Map.of("draft", initial)).get("draft");
        if (definition.title == null) definition.title = LocalizedText.of("");
        if (definition.description == null) definition.description = LocalizedText.of("");
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(FieldBindings.transientString(() -> draftName, value -> {
            draftName = value;
            clearStatusMessages();
        }), "ponderer.ui.custom_tag.id", "ponderer.ui.custom_tag.id.tooltip",
            "ponderer.ui.custom_tag.id.hint", -1));
        entries.add(FieldSpecs.localizedText(FieldBindings.transientString(
            () -> textForLanguage(definition.title), value -> {
                definition.title.setForLang(language, value);
                clearStatusMessages();
            }), "ponderer.ui.custom_tag.title", "ponderer.ui.custom_tag.scope", "ponderer.ui.custom_tag.title.hint", -1,
            () -> language, this::toggleLanguage));
        entries.add(FieldSpecs.localizedText(FieldBindings.transientString(
            () -> textForLanguage(definition.description), value -> {
                definition.description.setForLang(language, value);
                clearStatusMessages();
            }), "ponderer.ui.custom_tag.description", null, "ponderer.ui.custom_tag.description.hint", -1,
            () -> language, this::toggleLanguage));
        entries.add(FieldSpecs.text(FieldBindings.transientString(() -> definition.icon, value -> {
            definition.icon = value;
            clearStatusMessages();
        }), "ponderer.ui.custom_tag.icon", "ponderer.ui.custom_tag.icon.tooltip",
            "ponderer.ui.custom_tag.icon.hint", -1, FieldDecorators.jei(IdFieldMode.ITEM),
            FieldDecorators.heldItem(stack -> {
                definition.icon = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                clearStatusMessages();
                rebuildListPreservingScroll();
            })));
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();
        String name = draftName.trim();
        if (!CustomPonderTags.isName(name)) {
            setErrorMessage(UIText.of("ponderer.ui.custom_tag.error.id"));
            return false;
        }
        String id = originalId != null && name.equals(CustomPonderTags.editorName(originalId))
            ? originalId : CustomPonderTags.PREFIX + name;
        if (definition.title.getAllTranslations().values().stream().allMatch(value -> value == null || value.isBlank())) {
            setErrorMessage(UIText.of("ponderer.ui.custom_tag.error.title"));
            return false;
        }
        ResourceLocation icon = definition.icon == null ? null : ResourceLocation.tryParse(definition.icon.trim());
        if (icon == null || BuiltInRegistries.ITEM.getOptional(icon).filter(item -> item != Items.AIR).isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.custom_tag.error.icon"));
            return false;
        }
        definition.icon = icon.toString();
        String error = tagScreen.updateCustomTag(originalId, id,
            CustomPonderTags.copy(Map.of("draft", definition)).get("draft"));
        if (error != null) {
            setErrorMessage(error);
            return false;
        }
        markStateSaved();
        ScreenOpener.open(tagScreen);
        return true;
    }

    @Override
    protected Map<String, String> snapshotState() {
        return Map.of("name", draftName, "definition", CustomPonderTags.encode(Map.of("draft", definition)));
    }

    @Override
    protected void prepareSnapshotForBuild(Map<String, String> snapshot) {
        restoreSnapshot(snapshot);
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        draftName = snapshot.getOrDefault("name", "");
        definition = CustomPonderTags.decode(snapshot.get("definition")).get("draft");
    }

    private String textForLanguage(LocalizedText text) {
        String value = text.getExact(language);
        return value == null ? "" : value;
    }

    private void toggleLanguage() {
        String current = Minecraft.getInstance().getLanguageManager().getSelected();
        language = "en_us".equals(language) && !"en_us".equals(current) ? current : "en_us";
        rebuildListPreservingScroll();
    }
}
