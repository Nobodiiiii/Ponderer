package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FunctionScreen extends NavigatableSimiScreen {

    private static final int WINDOW_W = 220;
    private static final int BTN_W = 95;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 4;
    private static final int SECTION_GAP = 6;
    private static final int MARGIN_LEFT = 14;
    private static final int COLS = 2;

    private record ButtonDef(String labelKey, Runnable action, String tooltipKey) {}

    private record Section(String titleKey, List<ButtonDef> buttons) {}

    private final List<Section> sections = new ArrayList<>();
    private final List<ClickableButton> clickableButtons = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;

    public FunctionScreen() {
        // -- Scene Management --
        sections.add(new Section("ponderer.ui.function_page.scene_management", List.of(
            new ButtonDef("ponderer.ui.function_page.new_scene", () -> {
                Minecraft.getInstance().setScreen(buildNewScenePage());
            }, "ponderer.ui.function_page.new_scene.tooltip"),
            new ButtonDef("ponderer.ui.function_page.ai_generate", () -> {
                Minecraft.getInstance().setScreen(new AiGenerateScreen());
            }, "ponderer.ui.function_page.ai_generate.tooltip"),
            new ButtonDef("ponderer.ui.function_page.copy_scene", () -> {
                Minecraft.getInstance().setScreen(buildCopyPage());
            }, "ponderer.ui.function_page.copy_scene.tooltip"),
            new ButtonDef("ponderer.ui.function_page.delete_scene", () -> {
                Minecraft.getInstance().setScreen(buildDeletePage());
            }, "ponderer.ui.function_page.delete_scene.tooltip"),
            new ButtonDef("ponderer.ui.function_page.scene_list", () -> {
                PondererClientCommands.openItemList();
            }, "ponderer.ui.function_page.scene_list.tooltip"),
            new ButtonDef("ponderer.ui.function_page.reload", () -> {
                Minecraft.getInstance().setScreen(null);
                PondererClientCommands.reloadLocal();
            }, "ponderer.ui.function_page.reload.tooltip")
        )));

        // -- Sync --
        sections.add(new Section("ponderer.ui.function_page.sync", List.of(
            new ButtonDef("ponderer.ui.function_page.push", () -> {
                Minecraft.getInstance().setScreen(buildPushPage());
            }, "ponderer.ui.function_page.push.tooltip"),
            new ButtonDef("ponderer.ui.function_page.pull", () -> {
                Minecraft.getInstance().setScreen(buildPullPage());
            }, "ponderer.ui.function_page.pull.tooltip")
        )));

        // -- Import / Export --
        sections.add(new Section("ponderer.ui.function_page.import_export", List.of(
            new ButtonDef("ponderer.ui.function_page.export", () -> {
                Minecraft.getInstance().setScreen(new ExportPackScreen());
            }, "ponderer.ui.function_page.export.tooltip"),
            new ButtonDef("ponderer.ui.function_page.import", () -> {
                Minecraft.getInstance().setScreen(new ImportPackScreen());
            }, "ponderer.ui.function_page.import.tooltip"),
            new ButtonDef("ponderer.ui.function_page.download", () -> {
                Minecraft.getInstance().setScreen(buildDownloadPage());
            }, "ponderer.ui.function_page.download.tooltip")
        )));

        // -- Conversion --
        sections.add(new Section("ponderer.ui.function_page.conversion", List.of(
            new ButtonDef("ponderer.ui.function_page.to_ponderjs", () -> {
                Minecraft.getInstance().setScreen(null);
                PondererClientCommands.convertAllToPonderJs();
            }, "ponderer.ui.function_page.to_ponderjs.tooltip"),
            new ButtonDef("ponderer.ui.function_page.from_ponderjs", () -> {
                Minecraft.getInstance().setScreen(null);
                PondererClientCommands.convertAllFromPonderJs();
            }, "ponderer.ui.function_page.from_ponderjs.tooltip")
        )));

        // -- Settings --
        sections.add(new Section("ponderer.ui.function_page.settings", List.of(
            new ButtonDef("ponderer.ui.function_page.permissions", () -> {
                Minecraft.getInstance().setScreen(buildPermissionsPage());
            }, "ponderer.ui.function_page.permissions.tooltip"),
            new ButtonDef("ponderer.ui.function_page.blueprint_item", () -> {
                Minecraft.getInstance().setScreen(buildBlueprintItemPage());
            }, "ponderer.ui.function_page.blueprint_item.tooltip"),
            new ButtonDef("ponderer.ui.function_page.keybindings", () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new KeyBindsScreen(null, mc.options));
            }, "ponderer.ui.function_page.keybindings.tooltip"),
            new ButtonDef("ponderer.ui.function_page.ai_config", () -> {
                Minecraft.getInstance().setScreen(new AiConfigScreen());
            }, "ponderer.ui.function_page.ai_config.tooltip")
        )));
    }

    // -- Sub-page builders --

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
                        if (trimmed.isEmpty()) continue;
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
                    if (player == null) return;
                    ItemStack held = player.getMainHandItem();
                    if (held.isEmpty()) {
                        PondererClientCommands.newSceneFromHand(null);
                        return;
                    }
                    itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
                    if (useHeldNbt) {
                        nbt = PondererClientCommands.extractStackNbtFilter(held);
                    }
                } else {
                    itemId = ResourceLocation.tryParse(values.get("item_id"));
                    if (itemId == null) return;
                }

                if (!useHeldNbt) {
                    String nbtStr = values.get("nbt");
                    if (nbtStr != null && !nbtStr.isEmpty()) {
                        try { nbt = TagParser.parseTag(nbtStr); } catch (Exception ignored) {}
                    }
                }

                PondererClientCommands.newSceneForItem(itemId, nbt);
            })
            .build();

        // use_held_nbt depends on use_held
        screen.addToggleDependency("use_held_nbt", "use_held");
        // Typing in item_id disables use_held
        screen.addFieldDisablesToggle("item_id", "use_held");
        // Typing in nbt disables use_held_nbt
        screen.addFieldDisablesToggle("nbt", "use_held_nbt");

        // Auto-fill item_id when use_held is turned ON
        screen.addToggleAutoFill("use_held", "item_id", () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return "";
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) return "";
            return BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        });

        // Auto-fill nbt when use_held_nbt is turned ON
        screen.addToggleAutoFill("use_held_nbt", "nbt", () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return "";
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) return "";
            CompoundTag tag = PondererClientCommands.extractStackNbtFilter(held);
            if (tag == null) return "";
            if (tag.isEmpty()) return "";
            return tag.toString();
        });

        // Pre-fill item_id with held item
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                screen.setDefaultValue("item_id",
                    BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
            }
        }

        return screen;
    }

    private static CommandParamScreen buildCopyPage() {
        return CommandParamScreen.builder("ponderer.ui.function_page.copy_scene.title")
            .sceneIdField("scene_id", "ponderer.ui.function_page.param.scene_id",
                "ponderer.ui.function_page.param.scene_id.hint", true)
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
        return CommandParamScreen.builder("ponderer.ui.function_page.delete_scene.title")
            .choiceField("mode", "ponderer.ui.function_page.param.mode",
                List.of("ponderer.ui.function_page.delete.by_scene", "ponderer.ui.function_page.delete.by_item"),
                List.of("by_scene", "by_item"))
            .sceneIdField("scene_id", "ponderer.ui.function_page.param.scene_id",
                "ponderer.ui.function_page.param.scene_id.hint", false, true)
            .itemField("item_id", "ponderer.ui.function_page.param.item_id",
                "ponderer.ui.function_page.param.item_id.hint", false)
            .onExecute(values -> {
                String mode = values.get("mode");
                if ("by_scene".equals(mode)) {
                    String sceneId = values.get("scene_id");
                    if (sceneId != null && !sceneId.isEmpty()) {
                        for (String part : sceneId.split(",")) {
                            String trimmed = part.trim();
                            if (trimmed.isEmpty()) continue;
                            PondererClientCommands.deleteSceneByKey(trimmed);
                        }
                    }
                } else {
                    String itemId = values.get("item_id");
                    if (itemId != null && !itemId.isEmpty()) {
                        ResourceLocation rl = ResourceLocation.tryParse(itemId);
                        if (rl != null) PondererClientCommands.deleteScenesForItem(rl);
                    }
                }
            })
            .build();
    }

    private static CommandParamScreen buildDownloadPage() {
        return CommandParamScreen.builder("ponderer.ui.function_page.download.title")
            .textField("structure_id", "ponderer.ui.function_page.param.structure_id",
                "ponderer.ui.function_page.param.structure_id.hint", true)
            .onExecute(values -> {
                ResourceLocation rl = ResourceLocation.tryParse(values.get("structure_id"));
                if (rl != null) PondererClientCommands.requestStructureDownload(rl);
            })
            .build();
    }

    private static CommandParamScreen buildPermissionsPage() {
        return CommandParamScreen.builder("ponderer.ui.function_page.permissions.title")
            .choiceField("action", "ponderer.ui.function_page.permissions.action",
                List.of("ponderer.ui.function_page.permissions.add",
                    "ponderer.ui.function_page.permissions.remove"),
                List.of("add", "remove"))
            .textField("player", "ponderer.ui.function_page.permissions.player",
                "ponderer.ui.function_page.permissions.player.hint", true)
            .onExecute(values -> {
                String action = values.get("action");
                String playerName = values.get("player").trim();
                var server = Minecraft.getInstance().getSingleplayerServer();
                if (server == null) {
                    var p = Minecraft.getInstance().player;
                    if (p != null) p.displayClientMessage(
                        Component.translatable("ponderer.ui.function_page.permissions.server_only"), false);
                    return;
                }
                Path allowlistPath = server.getWorldPath(LevelResource.ROOT)
                    .resolve("ponderer").resolve("upload_allowlist.txt");
                manageAllowlist(allowlistPath, action, playerName);
            })
            .build();
    }

    private static void manageAllowlist(Path path, String action, String playerName) {
        try {
            // Ensure parent directory exists
            Files.createDirectories(path.getParent());

            List<String> lines;
            if (Files.exists(path)) {
                lines = new ArrayList<>(Files.readAllLines(path));
            } else {
                lines = new ArrayList<>();
                lines.add("# Ponderer upload allowlist");
                lines.add("# Add player names or UUIDs, one per line");
            }

            String entry = playerName.toLowerCase();
            var player = Minecraft.getInstance().player;

            if ("add".equals(action)) {
                boolean exists = lines.stream()
                    .anyMatch(l -> l.trim().toLowerCase().equals(entry));
                if (!exists) {
                    lines.add(playerName);
                    Files.write(path, lines);
                    if (player != null) player.displayClientMessage(
                        Component.translatable("ponderer.ui.function_page.permissions.added", playerName), false);
                } else {
                    if (player != null) player.displayClientMessage(
                        Component.translatable("ponderer.ui.function_page.permissions.exists", playerName), false);
                }
            } else {
                boolean removed = lines.removeIf(l -> {
                    String trimmed = l.trim();
                    return !trimmed.startsWith("#") && trimmed.toLowerCase().equals(entry);
                });
                if (removed) {
                    Files.write(path, lines);
                    if (player != null) player.displayClientMessage(
                        Component.translatable("ponderer.ui.function_page.permissions.removed", playerName), false);
                } else {
                    if (player != null) player.displayClientMessage(
                        Component.translatable("ponderer.ui.function_page.permissions.not_found", playerName), false);
                }
            }
        } catch (IOException e) {
            var player = Minecraft.getInstance().player;
            if (player != null) player.displayClientMessage(
                Component.translatable("ponderer.ui.function_page.permissions.error"), false);
        }
    }

    private static CommandParamScreen buildBlueprintItemPage() {
        String currentValue = Config.BLUEPRINT_CARRIER_ITEM.get();
        boolean isBuiltin = "ponderer:blueprint".equals(currentValue);

        CommandParamScreen screen = CommandParamScreen.builder("ponderer.ui.function_page.blueprint_item.title")
            .itemField("carrier_item", "ponderer.ui.function_page.blueprint_item.carrier",
                "ponderer.ui.function_page.blueprint_item.carrier.hint", true)
            .toggleField("use_builtin", "ponderer.ui.function_page.blueprint_item.use_builtin", isBuiltin)
            .onExecute(values -> {
                String itemId = values.get("carrier_item");
                Config.BLUEPRINT_CARRIER_ITEM.set(itemId);
                var player = Minecraft.getInstance().player;
                if (player != null) player.displayClientMessage(
                    Component.translatable("ponderer.ui.function_page.blueprint_item.set", itemId), false);
            })
            .build();

        // Pre-fill with current config value
        screen.setDefaultValue("carrier_item", currentValue);
        // Editing carrier_item disables use_builtin
        screen.addFieldDisablesToggle("carrier_item", "use_builtin");
        // When use_builtin is ON, auto-fill carrier_item with ponderer:blueprint
        screen.addToggleAutoFill("use_builtin", "carrier_item", () -> "ponderer:blueprint");

        return screen;
    }

    // -- Layout --

    private int getWindowHeight() {
        int h = 30;
        for (Section section : sections) {
            h += 14;
            int btnCount = section.buttons.size();
            int rows = (btnCount + COLS - 1) / COLS;
            h += rows * (BTN_H + BTN_GAP);
            h += SECTION_GAP;
        }
        return h + 4;
    }

    @Override
    protected void init() {
        super.init();
        clickableButtons.clear();

        int contentH = getWindowHeight();
        int displayH = Math.min(contentH, height - UILayoutConstants.SCREEN_MARGIN * 2);
        maxScroll = Math.max(0, contentH - displayH);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int gLeft = (width - WINDOW_W) / 2;
        int gTop = (height - displayH) / 2;
        int y = gTop + 28;

        for (Section section : sections) {
            y += 14;
            int col = 0;
            for (ButtonDef def : section.buttons) {
                int bx = gLeft + MARGIN_LEFT + col * (BTN_W + BTN_GAP);
                clickableButtons.add(new ClickableButton(bx, y, BTN_W, BTN_H, def.labelKey, def.action, def.tooltipKey));
                col++;
                if (col >= COLS) {
                    col = 0;
                    y += BTN_H + BTN_GAP;
                }
            }
            if (col != 0) {
                y += BTN_H + BTN_GAP;
            }
            y += SECTION_GAP;
        }
    }

    @Override
    protected void initBackTrackIcon(BoxWidget backTrack) {
        backTrack.showing(PonderGuiTextures.ICON_PONDER_CLOSE);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);

        int contentH = getWindowHeight();
        int displayH = Math.min(contentH, height - UILayoutConstants.SCREEN_MARGIN * 2);
        int gLeft = (width - WINDOW_W) / 2;
        int gTop = (height - displayH) / 2;

        new BoxElement()
            .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
            .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true), new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
            .at(gLeft, gTop, 0)
            .withBounds(WINDOW_W, displayH)
            .render(graphics);

        var font = Minecraft.getInstance().font;

        graphics.drawCenteredString(font,
            Component.translatable("ponderer.ui.function_page.title"),
            gLeft + WINDOW_W / 2, gTop + 8, 0xFFFFFF);
        graphics.fill(gLeft + 5, gTop + 20, gLeft + WINDOW_W - 5, gTop + 21, UILayoutConstants.COLOR_SEPARATOR);

        int scrollAreaTop = gTop + 22;
        int scrollAreaBottom = gTop + displayH;
        if (maxScroll > 0) {
            graphics.enableScissor(gLeft, scrollAreaTop, gLeft + WINDOW_W, scrollAreaBottom);
            graphics.pose().pushPose();
            graphics.pose().translate(0, -scrollOffset, 0);
        }
        int adjustedMouseY = mouseY + scrollOffset;
        boolean inViewport = maxScroll <= 0 || (mouseY >= scrollAreaTop && mouseY < scrollAreaBottom);

        int y = gTop + 28;
        for (Section section : sections) {
            graphics.drawString(font,
                Component.translatable(section.titleKey),
                gLeft + MARGIN_LEFT, y + 2, 0xCCCC00);
            y += 14;

            int col = 0;
            for (ButtonDef def : section.buttons) {
                int bx = gLeft + MARGIN_LEFT + col * (BTN_W + BTN_GAP);
                boolean hovered = inViewport && mouseX >= bx && mouseX < bx + BTN_W
                    && adjustedMouseY >= y && adjustedMouseY < y + BTN_H;

                int bgColor = hovered ? 0x80_4466aa : 0x60_333366;
                int borderColor = hovered ? 0xCC_6688cc : 0x60_555588;
                graphics.fill(bx, y, bx + BTN_W, y + BTN_H, bgColor);
                graphics.fill(bx, y, bx + BTN_W, y + 1, borderColor);
                graphics.fill(bx, y + BTN_H - 1, bx + BTN_W, y + BTN_H, borderColor);
                graphics.fill(bx, y, bx + 1, y + BTN_H, borderColor);
                graphics.fill(bx + BTN_W - 1, y, bx + BTN_W, y + BTN_H, borderColor);

                String label = UIText.of(def.labelKey);
                int textWidth = font.width(label);
                int textX = bx + (BTN_W - textWidth) / 2;
                int textY = y + (BTN_H - font.lineHeight) / 2 + 1;
                graphics.drawString(font, label, textX, textY, hovered ? 0xFFFFFF : UILayoutConstants.COLOR_LABEL);

                col++;
                if (col >= COLS) {
                    col = 0;
                    y += BTN_H + BTN_GAP;
                }
            }
            if (col != 0) {
                y += BTN_H + BTN_GAP;
            }
            y += SECTION_GAP;
        }

        if (maxScroll > 0) {
            graphics.pose().popPose();
            graphics.disableScissor();
            renderScrollbar(graphics, gLeft, scrollAreaTop, scrollAreaBottom);
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int adjMouseY = mouseY + scrollOffset;
        for (ClickableButton btn : clickableButtons) {
            if (btn.tooltipKey != null && mouseX >= btn.x && mouseX < btn.x + btn.w
                && adjMouseY >= btn.y && adjMouseY < btn.y + btn.h) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 600);
                graphics.renderComponentTooltip(font,
                    List.of(Component.translatable(btn.tooltipKey)),
                    mouseX, mouseY);
                graphics.pose().popPose();
                break;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * UILayoutConstants.SCROLL_SPEED));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double adjY = mouseY + scrollOffset;
            for (ClickableButton btn : clickableButtons) {
                if (mouseX >= btn.x && mouseX < btn.x + btn.w
                    && adjY >= btn.y && adjY < btn.y + btn.h) {
                    btn.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isEquivalentTo(NavigatableSimiScreen other) {
        return other instanceof FunctionScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void renderScrollbar(GuiGraphics graphics, int gLeft, int scrollAreaTop, int scrollAreaBottom) {
        int barX = gLeft + WINDOW_W - UILayoutConstants.SCROLLBAR_W - 2;
        int trackH = scrollAreaBottom - scrollAreaTop;
        graphics.fill(barX, scrollAreaTop, barX + UILayoutConstants.SCROLLBAR_W, scrollAreaBottom, UILayoutConstants.COLOR_SCROLLBAR_BG);
        int contentH = getWindowHeight() - 28;
        if (contentH <= 0) return;
        int thumbH = Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB, trackH * trackH / contentH);
        int thumbY = scrollAreaTop + (int) ((float) scrollOffset / maxScroll * (trackH - thumbH));
        graphics.fill(barX, thumbY, barX + UILayoutConstants.SCROLLBAR_W, thumbY + thumbH, UILayoutConstants.COLOR_SCROLLBAR_FG);
    }

    private record ClickableButton(int x, int y, int w, int h, String labelKey, Runnable action, String tooltipKey) {}
}
