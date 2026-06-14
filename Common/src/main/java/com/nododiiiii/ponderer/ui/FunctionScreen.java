package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.resourcify.ResourcifyCompat;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.ButtonPairListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FunctionScreen extends AbstractReadonlyDeclarativeListScreen {

    private record ButtonDef(String labelKey, Runnable action, String tooltipKey) {
    }

    private record Section(String titleKey, List<ButtonDef> buttons) {
    }

    private final List<Section> sections = new ArrayList<>();

    public FunctionScreen() {
        super(null, "ponderer.ui.scope.editor", "ponderer.ui.function_page.title", UILayoutConstants.EDITOR_LIST_W);

        sections.add(new Section("ponderer.ui.function_page.scene_management", List.of(
            new ButtonDef("ponderer.ui.function_page.new_scene",
                () -> Minecraft.getInstance().setScreen(buildNewScenePage()),
                "ponderer.ui.function_page.new_scene.tooltip"),
            new ButtonDef("ponderer.ui.function_page.ai_generate",
                () -> Minecraft.getInstance().setScreen(new AiGenerateScreen()),
                "ponderer.ui.function_page.ai_generate.tooltip"),
            new ButtonDef("ponderer.ui.function_page.copy_scene",
                () -> Minecraft.getInstance().setScreen(buildCopyPage()),
                "ponderer.ui.function_page.copy_scene.tooltip"),
            new ButtonDef("ponderer.ui.function_page.delete_scene",
                () -> Minecraft.getInstance().setScreen(buildDeletePage()),
                "ponderer.ui.function_page.delete_scene.tooltip"),
            new ButtonDef("ponderer.ui.function_page.scene_list",
                PondererClientCommands::openItemList,
                "ponderer.ui.function_page.scene_list.tooltip"),
            new ButtonDef("ponderer.ui.function_page.reload",
                () -> runOnPageStatus(PondererClientCommands::reloadLocal),
                "ponderer.ui.function_page.reload.tooltip")
        )));

        sections.add(new Section("ponderer.ui.function_page.sync", List.of(
            new ButtonDef("ponderer.ui.function_page.push",
                () -> Minecraft.getInstance().setScreen(buildPushPage()),
                "ponderer.ui.function_page.push.tooltip"),
            new ButtonDef("ponderer.ui.function_page.pull",
                () -> Minecraft.getInstance().setScreen(buildPullPage()),
                "ponderer.ui.function_page.pull.tooltip")
        )));

        sections.add(new Section("ponderer.ui.function_page.import_export", List.of(
            new ButtonDef("ponderer.ui.function_page.export",
                () -> Minecraft.getInstance().setScreen(new ExportPackScreen()),
                "ponderer.ui.function_page.export.tooltip"),
            new ButtonDef("ponderer.ui.function_page.export_java",
                () -> Minecraft.getInstance().setScreen(new JavaModuleExportScreen()),
                "ponderer.ui.function_page.export_java.tooltip"),
            new ButtonDef("ponderer.ui.function_page.import",
                () -> Minecraft.getInstance().setScreen(new ImportPackScreen()),
                "ponderer.ui.function_page.import.tooltip"),
            new ButtonDef("ponderer.ui.function_page.download",
                () -> Minecraft.getInstance().setScreen(buildDownloadPage()),
                "ponderer.ui.function_page.download.tooltip"),
            new ButtonDef("ponderer.ui.function_page.browse_modrinth",
                () -> ResourcifyCompat.openBrowseScreen("[Ponderer]"),
                "ponderer.ui.function_page.browse_modrinth.tooltip")
        )));

        sections.add(new Section("ponderer.ui.function_page.conversion", List.of(
            new ButtonDef("ponderer.ui.function_page.to_ponderjs",
                () -> runOnPageStatus(PondererClientCommands::convertAllToPonderJs),
                "ponderer.ui.function_page.to_ponderjs.tooltip"),
            new ButtonDef("ponderer.ui.function_page.from_ponderjs",
                () -> runOnPageStatus(PondererClientCommands::convertAllFromPonderJs),
                "ponderer.ui.function_page.from_ponderjs.tooltip")
        )));

        sections.add(new Section("ponderer.ui.function_page.settings", List.of(
            new ButtonDef("ponderer.ui.function_page.permissions",
                () -> Minecraft.getInstance().setScreen(new PermissionManagementScreen(this)),
                "ponderer.ui.function_page.permissions.tooltip"),
            new ButtonDef("ponderer.ui.function_page.blueprint_item",
                () -> Minecraft.getInstance().setScreen(new BlueprintItemConfigScreen(this)),
                "ponderer.ui.function_page.blueprint_item.tooltip"),
            new ButtonDef("ponderer.ui.function_page.projector",
                () -> Minecraft.getInstance().setScreen(new ProjectorFeatureConfigScreen(this)),
                "ponderer.ui.function_page.projector.tooltip"),
            new ButtonDef("ponderer.ui.function_page.keybindings",
                () -> Minecraft.getInstance().setScreen(new PondererKeyBindingsScreen(this)),
                "ponderer.ui.function_page.keybindings.tooltip"),
            new ButtonDef("ponderer.ui.function_page.mod_config",
                () -> Minecraft.getInstance().setScreen(new PondererConfigScreen(this)),
                "ponderer.ui.function_page.mod_config.tooltip"),
            new ButtonDef("ponderer.ui.function_page.ai_config",
                () -> Minecraft.getInstance().setScreen(new AiConfigScreen(this)),
                "ponderer.ui.function_page.ai_config.tooltip")
        )));
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        for (Section section : sections) {
            entries.add(compactSectionHeader(UIText.of(section.titleKey)));
            for (int i = 0; i < section.buttons.size(); i += 2) {
                ButtonDef left = section.buttons.get(i);
                ButtonDef right = i + 1 < section.buttons.size() ? section.buttons.get(i + 1) : null;
                entries.add(new ButtonPairListEntry(
                    UIText.of(left.labelKey),
                    UIText.of(left.tooltipKey),
                    left.action,
                    right == null ? null : UIText.of(right.labelKey),
                    right == null ? null : UIText.of(right.tooltipKey),
                    right == null ? null : right.action));
            }
        }
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }

    /**
     * Run a direct-action command (reload / convert) and report its result on this page's
     * status line instead of closing back to the game and printing to chat.
     */
    private void runOnPageStatus(Runnable command) {
        PondererClientCommands.runReportingTo(this::setInfoMessage, this::setErrorMessage, command);
    }

    private static SectionHeaderListEntry compactSectionHeader(String title) {
        return new SectionHeaderListEntry(title) {
            @Override
            public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTicks) {
                var font = Minecraft.getInstance().font;
                int color = annotations.containsKey("highlight") ? 0xFFF3D46B : 0xFFCCCC77;
                int titleY = y + 8;
                int lineY = y + height - 3;
                graphics.drawString(font, title, x + 4, titleY, color);
                graphics.fill(x + 4, lineY, x + width - 4, lineY + 1, 0x40FFFFFF);
            }
        };
    }

    private static CommandParamScreen buildPushPage() {
        return CommandParamScreen.builder("ponderer.ui.function_page.push.title")
            .choiceField("mode", "ponderer.ui.function_page.param.mode",
                List.of("ponderer.ui.function_page.mode.check", "ponderer.ui.function_page.mode.force"),
                List.of("check", "force"))
            .sceneIdField("scene_id", "ponderer.ui.function_page.param.scene_id",
                "ponderer.ui.function_page.param.scene_id.hint", false, true)
            .onExecute(values -> {
                String mode = values.get("mode");
                String sceneId = values.get("scene_id");
                if (sceneId != null && !sceneId.isEmpty()) {
                    for (String part : sceneId.split(",")) {
                        String trimmed = part.trim();
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        PondererClientCommands.pushByKey(trimmed, mode);
                    }
                    return;
                }
                PondererClientCommands.pushAll(mode);
            })
            .build();
    }

    private static CommandParamScreen buildPullPage() {
        return CommandParamScreen.builder("ponderer.ui.function_page.pull.title")
            .choiceField("mode", "ponderer.ui.function_page.param.mode",
                List.of("ponderer.ui.function_page.mode.check",
                    "ponderer.ui.function_page.mode.force",
                    "ponderer.ui.function_page.mode.keep_local"),
                List.of("check", "force", "keep_local"))
            .onExecute(values -> PondererClientCommands.pull(values.get("mode")))
            .build();
    }

    private static CommandParamScreen buildNewScenePage() {
        CommandParamScreen screen = CommandParamScreen.builder("ponderer.ui.function_page.new_scene.title")
            .toggleField("use_held", "ponderer.ui.function_page.new.use_held", true)
            .toggleField("use_held_nbt", "ponderer.ui.function_page.new.use_held_nbt", false)
            .itemField("item_id", "ponderer.ui.function_page.param.item_id",
                "ponderer.ui.function_page.param.item_id.hint", false)
            .textField("nbt", "ponderer.ui.function_page.param.nbt",
                "ponderer.ui.function_page.param.nbt.hint", false)
            .onExecute(values -> {
                boolean useHeld = "true".equals(values.get("use_held"));
                boolean useHeldNbt = "true".equals(values.get("use_held_nbt"));

                ResourceLocation itemId;
                CompoundTag nbt = null;

                if (useHeld) {
                    var player = Minecraft.getInstance().player;
                    if (player == null) {
                        return;
                    }
                    ItemStack held = player.getMainHandItem();
                    if (held.isEmpty()) {
                        PondererClientCommands.newSceneFromHand(null);
                        return;
                    }
                    itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
                    if (useHeldNbt && held.getTag() != null) {
                        nbt = held.getTag();
                    }
                } else {
                    itemId = ResourceLocation.tryParse(values.get("item_id"));
                    if (itemId == null) {
                        return;
                    }
                }

                if (!useHeldNbt) {
                    String nbtStr = values.get("nbt");
                    if (nbtStr != null && !nbtStr.isEmpty()) {
                        try {
                            nbt = TagParser.parseTag(nbtStr);
                        } catch (Exception ignored) {
                        }
                    }
                }

                PondererClientCommands.newSceneForItem(itemId, nbt);
            })
            .build();

        screen.addToggleDependency("use_held_nbt", "use_held");
        screen.addFieldDisablesToggle("item_id", "use_held");
        screen.addFieldDisablesToggle("nbt", "use_held_nbt");

        screen.addToggleAutoFill("use_held", "item_id", () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return "";
            }
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                return "";
            }
            return BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        });

        screen.addToggleAutoFill("use_held_nbt", "nbt", () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return "";
            }
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty() || held.getTag() == null) {
                return "";
            }
            return held.getTag().toString();
        });

        var player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                screen.setDefaultValue("item_id", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
            }
        }

        return screen;
    }

    private static CommandParamScreen buildCopyPage() {
        return CommandParamScreen.builder("ponderer.ui.function_page.copy_scene.title")
            .sceneIdField("scene_id", "ponderer.ui.function_page.param.scene_id",
                null, true)
            .itemField("target_item", "ponderer.ui.function_page.param.target_item",
                "ponderer.ui.function_page.param.target_item.hint", true)
            .onExecute(values -> {
                String sceneKey = values.get("scene_id");
                ResourceLocation targetItem = ResourceLocation.tryParse(values.get("target_item"));
                if (sceneKey != null && !sceneKey.isEmpty() && targetItem != null) {
                    PondererClientCommands.copySceneByKey(sceneKey, targetItem);
                }
            })
            .build();
    }

    private static CommandParamScreen buildDeletePage() {
        CommandParamScreen screen = CommandParamScreen.builder("ponderer.ui.function_page.delete_scene.title")
            .choiceField("mode", "ponderer.ui.function_page.param.mode",
                List.of("ponderer.ui.function_page.delete.by_scene", "ponderer.ui.function_page.delete.by_item"),
                List.of("by_scene", "by_item"))
            .sceneIdField("scene_id", "ponderer.ui.function_page.param.scene_id",
                null, false, true)
            .itemField("item_id", "ponderer.ui.function_page.param.item_id",
                "ponderer.ui.function_page.param.item_id.hint", false)
            .onExecute(values -> {
                String mode = values.get("mode");
                if ("by_scene".equals(mode)) {
                    String sceneId = values.get("scene_id");
                    if (sceneId != null && !sceneId.isEmpty()) {
                        for (String part : sceneId.split(",")) {
                            String trimmed = part.trim();
                            if (trimmed.isEmpty()) {
                                continue;
                            }
                            PondererClientCommands.deleteSceneByKey(trimmed);
                        }
                    }
                    return;
                }

                String itemId = values.get("item_id");
                if (itemId != null && !itemId.isEmpty()) {
                    ResourceLocation rl = ResourceLocation.tryParse(itemId);
                    if (rl != null) {
                        PondererClientCommands.deleteScenesForItem(rl);
                    }
                }
            })
            .build();
        screen.showFieldWhenValue("scene_id", "mode", "by_scene");
        screen.showFieldWhenValue("item_id", "mode", "by_item");
        return screen;
    }

    private static CommandParamScreen buildDownloadPage() {
        return CommandParamScreen.builder("ponderer.ui.function_page.download.title")
            .textField("structure_id", "ponderer.ui.function_page.param.structure_id",
                "ponderer.ui.function_page.param.structure_id.hint", true)
            .onExecute(values -> {
                ResourceLocation rl = ResourceLocation.tryParse(values.get("structure_id"));
                if (rl != null) {
                    PondererClientCommands.requestStructureDownload(rl);
                }
            })
            .build();
    }

}
