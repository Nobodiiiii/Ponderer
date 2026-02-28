package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Unified item grid screen with smooth scrolling and pack-based grouping.
 * <p>
 * Three modes:
 * <ul>
 * <li>LIST – browse all items with ponder scenes; click opens PonderUI</li>
 * <li>SINGLE_SELECT – pick one scene key via callback</li>
 * <li>MULTI_SELECT – pick multiple scene keys with confirm/cancel</li>
 * </ul>
 * All modes support pressing [W] to preview ponder for the hovered item.
 */
public class PonderItemGridScreen extends AbstractSimiScreen {

    public enum Mode {
        LIST, SINGLE_SELECT, MULTI_SELECT
    }

    // -- Layout constants --
    private static final int WINDOW_W = 256;
    private static final int COLS = 11;
    private static final int CELL_SIZE = 20;
    private static final int GRID_LEFT = 14;
    private static final int GRID_TOP = 42;
    private static final int VISIBLE_ROWS = 6;
    private static final int VISIBLE_H = VISIBLE_ROWS * CELL_SIZE;
    private static final int SECTION_HEADER_H = 16;
    private static final int SCROLL_SPEED = 20;

    // -- Data model --
    record ItemEntry(ItemStack stack, @Nullable String nbtFilter, List<String> sceneKeys) {
    }

    record PackSection(@Nullable String packPrefix, String displayName, List<ItemEntry> entries) {
    }

    enum GroupMode {
        BY_PACK, BY_ITEM
    }

    // -- State --
    private final Mode mode;
    private List<PackSection> sections;
    private final @Nullable Consumer<String> onSelectSingle;
    private final @Nullable Consumer<Set<String>> onSelectMulti;
    private final @Nullable Runnable onCancel;
    private final Set<String> selectedSceneKeys = new HashSet<>();
    private double scrollY = 0;
    private double maxScrollY = 0;
    private int totalSceneCount;
    private int totalItemCount;
    private int lastMouseX, lastMouseY;
    private GroupMode groupMode = GroupMode.BY_PACK;

    /** When non-null, PonderUI close will return to this screen (via Mixin). */
    @Nullable
    public static PonderItemGridScreen returnScreen;

    // -- Constructors --

    /** LIST mode – browse items, click opens PonderUI */
    public PonderItemGridScreen() {
        this(Mode.LIST, null, null, null);
    }

    /** SINGLE_SELECT mode – pick one scene key */
    public PonderItemGridScreen(Consumer<String> onSelect, Runnable onCancel) {
        this(Mode.SINGLE_SELECT, onSelect, null, onCancel);
    }

    /** MULTI_SELECT mode – pick multiple scene keys */
    public PonderItemGridScreen(Consumer<Set<String>> onSelectMulti, Runnable onCancel, boolean multi) {
        this(Mode.MULTI_SELECT, null, onSelectMulti, onCancel);
    }

    private PonderItemGridScreen(Mode mode, @Nullable Consumer<String> onSelectSingle,
            @Nullable Consumer<Set<String>> onSelectMulti,
            @Nullable Runnable onCancel) {
        super(Component.translatable(switch (mode) {
            case LIST -> "ponderer.ui.item_grid.select_scene";
            case SINGLE_SELECT -> "ponderer.ui.item_grid.select_scene";
            case MULTI_SELECT -> "ponderer.ui.item_grid.select_scenes";
        }));
        this.mode = mode;
        this.onSelectSingle = onSelectSingle;
        this.onSelectMulti = onSelectMulti;
        this.onCancel = onCancel;
        this.sections = collectGroupedSections();
        recomputeCounts();
    }

    private void recomputeCounts() {
        int items = 0;
        Set<String> allKeys = new HashSet<>();
        for (PackSection sec : sections) {
            items += sec.entries.size();
            for (ItemEntry entry : sec.entries) {
                allKeys.addAll(entry.sceneKeys);
            }
        }
        this.totalItemCount = items;
        this.totalSceneCount = allKeys.size();
    }

    /** Collect entries grouped by pack prefix. Local first, then sorted by pack name. */
    private static List<PackSection> collectGroupedSections() {
        // Group scenes by pack prefix
        Map<String, Map<String, List<String>>> packGroups = new LinkedHashMap<>();
        // packPrefix -> (itemKey -> list of sceneKeys)
        Map<String, Map<String, List<String>>> packItemFilters = new LinkedHashMap<>();

        for (DslScene scene : SceneRuntime.getScenes()) {
            if (scene.items == null || scene.id == null) continue;
            String packPrefix = DslScene.extractPackPrefix(scene.sourceFile);
            String packKey = packPrefix != null ? packPrefix : "";
            String sceneKey = scene.sceneKey();

            for (String itemId : scene.items) {
                String nf = scene.nbtFilter;
                String entryKey = itemId + "|" + (nf != null ? nf : "");

                packGroups.computeIfAbsent(packKey, k -> new LinkedHashMap<>())
                        .computeIfAbsent(entryKey, k -> new ArrayList<>());
                List<String> keys = packGroups.get(packKey).get(entryKey);
                if (!keys.contains(sceneKey)) keys.add(sceneKey);

                packItemFilters.computeIfAbsent(packKey, k -> new LinkedHashMap<>())
                        .computeIfAbsent(itemId, k -> new ArrayList<>());
                List<String> filters = packItemFilters.get(packKey).get(itemId);
                if (nf != null && !nf.isBlank()) {
                    if (!filters.contains(nf)) filters.add(nf);
                } else {
                    if (!filters.contains(null)) filters.add(0, null);
                }
            }
        }

        // Build sections: local first, then packs sorted alphabetically
        List<PackSection> result = new ArrayList<>();
        List<String> packKeys = new ArrayList<>(packGroups.keySet());
        packKeys.sort((a, b) -> {
            if (a.isEmpty() && !b.isEmpty()) return -1;
            if (!a.isEmpty() && b.isEmpty()) return 1;
            return a.compareToIgnoreCase(b);
        });

        for (String packKey : packKeys) {
            Map<String, List<String>> entryMap = packGroups.get(packKey);
            Map<String, List<String>> filterMap = packItemFilters.getOrDefault(packKey, Map.of());
            List<ItemEntry> entries = new ArrayList<>();

            for (var itemEntry : filterMap.entrySet()) {
                String itemId = itemEntry.getKey();
                ResourceLocation rl = ResourceLocation.tryParse(itemId);
                if (rl == null) continue;
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item == null || item == Items.AIR) continue;

                for (String nf : itemEntry.getValue()) {
                    ItemStack stack = new ItemStack(item);
                    if (nf != null) {
                        try {
                            CompoundTag filterTag = TagParser.parseTag(nf);
                            CompoundTag fullTag = new CompoundTag();
                            fullTag.putString("id", rl.toString());
                            fullTag.putByte("Count", (byte) 1);
                            fullTag.put("tag", filterTag);
                            ItemStack parsed = ItemStack.of(fullTag);
                            if (!parsed.isEmpty()) stack = parsed;
                        } catch (Exception ignored) {
                        }
                    }
                    String key = itemId + "|" + (nf != null ? nf : "");
                    List<String> sceneKeys = entryMap.getOrDefault(key, List.of());
                    if (!sceneKeys.isEmpty()) {
                        entries.add(new ItemEntry(stack, nf, sceneKeys));
                    }
                }
            }

            if (!entries.isEmpty()) {
                String prefix = packKey.isEmpty() ? null : packKey;
                String displayName = packKey.isEmpty()
                        ? UIText.of("ponderer.ui.item_grid.section_local")
                        : packKey;
                result.add(new PackSection(prefix, displayName, entries));
            }
        }
        return result;
    }

    /** Collect all entries into a single flat list (grouped by item, no pack separation). */
    private static List<PackSection> collectFlatSections() {
        Map<String, List<String>> entryMap = new LinkedHashMap<>();
        Map<String, List<String>> itemFilters = new LinkedHashMap<>();

        for (DslScene scene : SceneRuntime.getScenes()) {
            if (scene.items == null || scene.id == null) continue;
            String sceneKey = scene.sceneKey();

            for (String itemId : scene.items) {
                String nf = scene.nbtFilter;
                String key = itemId + "|" + (nf != null ? nf : "");

                entryMap.computeIfAbsent(key, k -> new ArrayList<>());
                List<String> keys = entryMap.get(key);
                if (!keys.contains(sceneKey)) keys.add(sceneKey);

                itemFilters.computeIfAbsent(itemId, k -> new ArrayList<>());
                List<String> filters = itemFilters.get(itemId);
                if (nf != null && !nf.isBlank()) {
                    if (!filters.contains(nf)) filters.add(nf);
                } else {
                    if (!filters.contains(null)) filters.add(0, null);
                }
            }
        }

        List<ItemEntry> entries = new ArrayList<>();
        for (var itemEntry : itemFilters.entrySet()) {
            String itemId = itemEntry.getKey();
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) continue;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == Items.AIR) continue;

            for (String nf : itemEntry.getValue()) {
                ItemStack stack = new ItemStack(item);
                if (nf != null) {
                    try {
                        CompoundTag filterTag = TagParser.parseTag(nf);
                        CompoundTag fullTag = new CompoundTag();
                        fullTag.putString("id", rl.toString());
                        fullTag.putByte("Count", (byte) 1);
                        fullTag.put("tag", filterTag);
                        ItemStack parsed = ItemStack.of(fullTag);
                        if (!parsed.isEmpty()) stack = parsed;
                    } catch (Exception ignored) {
                    }
                }
                String key = itemId + "|" + (nf != null ? nf : "");
                List<String> sceneKeys = entryMap.getOrDefault(key, List.of());
                if (!sceneKeys.isEmpty()) {
                    entries.add(new ItemEntry(stack, nf, sceneKeys));
                }
            }
        }

        if (entries.isEmpty()) return List.of();
        return List.of(new PackSection(null,
                UIText.of("ponderer.ui.item_grid.section_all"), entries));
    }

    private void toggleGroupMode() {
        groupMode = (groupMode == GroupMode.BY_PACK) ? GroupMode.BY_ITEM : GroupMode.BY_PACK;
        sections = (groupMode == GroupMode.BY_PACK) ? collectGroupedSections() : collectFlatSections();
        recomputeCounts();
        scrollY = 0;
        maxScrollY = Math.max(0, computeTotalContentHeight() - VISIBLE_H);
    }

    /** For ExportPackScreen to read current selection state. */
    public Set<String> getSelectedSceneIds() {
        return selectedSceneKeys;
    }

    private int getWindowHeight() {
        return GRID_TOP + VISIBLE_H + (mode == Mode.MULTI_SELECT ? 70 : 40);
    }

    private double computeTotalContentHeight() {
        double h = 0;
        for (PackSection section : sections) {
            h += SECTION_HEADER_H;
            int rows = (section.entries.size() + COLS - 1) / COLS;
            h += rows * CELL_SIZE;
        }
        return h;
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, getWindowHeight());
        super.init();
        maxScrollY = Math.max(0, computeTotalContentHeight() - VISIBLE_H);
        scrollY = Math.min(scrollY, maxScrollY);
    }

    /** 0=none, 1=partial, 2=full */
    private int getSelectionState(ItemEntry entry) {
        if (mode != Mode.MULTI_SELECT) return 0;
        boolean any = false, all = true;
        for (String key : entry.sceneKeys) {
            if (selectedSceneKeys.contains(key)) any = true;
            else all = false;
        }
        if (all) return 2;
        if (any) return 1;
        return 0;
    }

    // -- Rendering --

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        int wH = getWindowHeight();

        // Background
        new BoxElement()
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(WINDOW_W, wH)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        // Title
        graphics.drawCenteredString(font, this.title, guiLeft + WINDOW_W / 2, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WINDOW_W - 5, guiTop + 21, 0x60_FFFFFF);

        // Subtitle
        String subtitle;
        if (mode == Mode.MULTI_SELECT) {
            subtitle = UIText.of("ponderer.ui.item_grid.selected_count",
                    selectedSceneKeys.size(), totalSceneCount);
        } else {
            subtitle = UIText.of("ponderer.ui.item_grid.total_scenes",
                    totalItemCount, totalSceneCount);
        }
        graphics.drawString(font, subtitle, guiLeft + 10, guiTop + 25, 0x999999);

        // [W] hint
        String wHint = UIText.of("ponderer.ui.item_grid.press_w_hint");
        int wHintW = font.width(wHint);
        graphics.drawString(font, wHint, guiLeft + WINDOW_W - 10 - wHintW, guiTop + 25, 0x555555);

        // Group mode toggle button
        String groupLabel = groupMode == GroupMode.BY_PACK
                ? UIText.of("ponderer.ui.item_grid.group_by_pack")
                : UIText.of("ponderer.ui.item_grid.group_by_item");
        int groupBtnW = font.width(groupLabel) + 8;
        int groupBtnX = guiLeft + WINDOW_W - 10 - wHintW - groupBtnW - 6;
        int groupBtnY = guiTop + 22;
        int groupBtnH = 12;
        boolean groupHovered = mouseX >= groupBtnX && mouseX < groupBtnX + groupBtnW
                && mouseY >= groupBtnY && mouseY < groupBtnY + groupBtnH;
        int gbg = groupHovered ? 0x80_4466aa : 0x60_333366;
        int gbdr = groupHovered ? 0xCC_6688cc : 0x60_555588;
        graphics.fill(groupBtnX, groupBtnY, groupBtnX + groupBtnW, groupBtnY + groupBtnH, gbg);
        graphics.fill(groupBtnX, groupBtnY, groupBtnX + groupBtnW, groupBtnY + 1, gbdr);
        graphics.fill(groupBtnX, groupBtnY + groupBtnH - 1, groupBtnX + groupBtnW, groupBtnY + groupBtnH, gbdr);
        graphics.fill(groupBtnX, groupBtnY, groupBtnX + 1, groupBtnY + groupBtnH, gbdr);
        graphics.fill(groupBtnX + groupBtnW - 1, groupBtnY, groupBtnX + groupBtnW, groupBtnY + groupBtnH, gbdr);
        graphics.drawString(font, groupLabel, groupBtnX + 4, groupBtnY + 2, groupHovered ? 0xFFFFFF : 0xAAAAAA);

        // Empty state
        if (sections.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("ponderer.ui.item_grid.empty"),
                    guiLeft + WINDOW_W / 2, guiTop + GRID_TOP + 30, 0x999999);
            return;
        }

        // -- Scrollable grid with scissor clipping --
        int clipLeft = guiLeft + GRID_LEFT - 2;
        int clipTop = guiTop + GRID_TOP;
        int clipRight = guiLeft + GRID_LEFT + COLS * CELL_SIZE + 2;
        int clipBottom = clipTop + VISIBLE_H;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

        double currentY = 0;
        for (PackSection section : sections) {
            // Section header
            int headerScreenY = (int) (clipTop + currentY - scrollY);
            if (headerScreenY + SECTION_HEADER_H > clipTop && headerScreenY < clipBottom) {
                graphics.drawString(font, section.displayName,
                        guiLeft + GRID_LEFT + 2, headerScreenY + 3, 0xCCCC00);
                graphics.fill(guiLeft + GRID_LEFT, headerScreenY + SECTION_HEADER_H - 1,
                        guiLeft + GRID_LEFT + COLS * CELL_SIZE,
                        headerScreenY + SECTION_HEADER_H, 0x40_FFFFFF);
            }
            currentY += SECTION_HEADER_H;

            // Items
            int rows = (section.entries.size() + COLS - 1) / COLS;
            for (int i = 0; i < section.entries.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
                int iy = (int) (clipTop + currentY + row * CELL_SIZE - scrollY);

                if (iy + CELL_SIZE <= clipTop || iy >= clipBottom) continue;

                ItemEntry entry = section.entries.get(i);
                boolean hovered = mouseX >= ix && mouseX < ix + CELL_SIZE
                        && mouseY >= iy && mouseY < iy + CELL_SIZE;
                int selState = getSelectionState(entry);

                // Selection highlight
                if (selState == 2) {
                    graphics.fill(ix, iy, ix + CELL_SIZE, iy + CELL_SIZE, 0x60_4080FF);
                } else if (selState == 1) {
                    graphics.fill(ix, iy, ix + CELL_SIZE, iy + CELL_SIZE, 0x30_FFAA00);
                }

                // Hover highlight
                if (hovered) {
                    graphics.fill(ix, iy, ix + CELL_SIZE, iy + CELL_SIZE, 0x40_FFFFFF);
                }

                graphics.renderItem(entry.stack, ix + 2, iy + 2);

                // NBT indicator
                if (entry.nbtFilter != null) {
                    graphics.fill(ix + CELL_SIZE - 5, iy + 1, ix + CELL_SIZE - 1, iy + 5, 0xFF_FFAA00);
                }
                // Multi-scene indicator
                if (entry.sceneKeys.size() > 1) {
                    graphics.fill(ix + 1, iy + 1, ix + 5, iy + 5, 0xFF_55AAFF);
                }
            }
            currentY += rows * CELL_SIZE;
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScrollY > 0) {
            int barX = guiLeft + GRID_LEFT + COLS * CELL_SIZE + 2;
            int barH = VISIBLE_H;
            double ratio = scrollY / maxScrollY;
            int thumbH = Math.max(10, (int) (barH * (double) barH / (maxScrollY + barH)));
            int thumbY = clipTop + (int) ((barH - thumbH) * ratio);
            graphics.fill(barX, clipTop, barX + 3, clipTop + barH, 0x30_FFFFFF);
            graphics.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0x80_AAAAAA);
        }

        // MULTI_SELECT action buttons
        if (mode == Mode.MULTI_SELECT) {
            renderMultiSelectButtons(graphics, font, mouseX, mouseY, wH);
        }
    }

    private void renderMultiSelectButtons(GuiGraphics graphics, net.minecraft.client.gui.Font font,
            int mouseX, int mouseY, int wH) {
        int btnW = 50, btnH = 16;
        int btnY = guiTop + wH - 44;
        int allX = guiLeft + 10;
        int noneX = allX + btnW + 6;
        int cancelX = guiLeft + WINDOW_W - 10 - btnW;
        int okX = cancelX - btnW - 6;

        String allLbl = UIText.of("ponderer.ui.item_grid.select_all");
        String noneLbl = UIText.of("ponderer.ui.item_grid.deselect_all");
        String okLbl = UIText.of("ponderer.ui.confirm");
        String cancelLbl = UIText.of("ponderer.ui.cancel");

        renderTextButton(graphics, font, allX, btnY, btnW, btnH, allLbl, mouseX, mouseY);
        renderTextButton(graphics, font, noneX, btnY, btnW, btnH, noneLbl, mouseX, mouseY);
        renderTextButton(graphics, font, okX, btnY, btnW, btnH, okLbl, mouseX, mouseY);
        renderTextButton(graphics, font, cancelX, btnY, btnW, btnH, cancelLbl, mouseX, mouseY);
    }

    private void renderTextButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
            int x, int y, int w, int h, String label,
            int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = hov ? 0x80_4466aa : 0x60_333366;
        int bdr = hov ? 0xCC_6688cc : 0x60_555588;
        renderBoxButton(graphics, x, y, w, h, bg, bdr);
        int tc = hov ? 0xFFFFFF : 0xCCCCCC;
        int tw = font.width(label);
        graphics.drawString(font, label, x + (w - tw) / 2, y + (h - font.lineHeight) / 2 + 1, tc);
    }

    private void renderBoxButton(GuiGraphics graphics, int x, int y, int w, int h, int bg, int bdr) {
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x, y, x + w, y + 1, bdr);
        graphics.fill(x, y + h - 1, x + w, y + h, bdr);
        graphics.fill(x, y, x + 1, y + h, bdr);
        graphics.fill(x + w - 1, y, x + w, y + h, bdr);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (sections.isEmpty()) return;

        HitResult hit = hitTest(mouseX, mouseY);
        if (hit == null) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        ItemEntry entry = hit.entry;
        List<Component> tooltip = new ArrayList<>(
                entry.stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.NORMAL));
        tooltip.add(Component.literal(""));

        // Scene keys
        if (entry.sceneKeys.size() == 1) {
            String key = entry.sceneKeys.get(0);
            String prefix = mode == Mode.MULTI_SELECT && selectedSceneKeys.contains(key) ? "\u2713 " : "";
            tooltip.add(Component.literal(prefix)
                    .append(Component.translatable("ponderer.ui.item_grid.scene_label", key))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("ponderer.ui.item_grid.scenes_count", entry.sceneKeys.size())
                    .withStyle(ChatFormatting.AQUA));
            for (int j = 0; j < Math.min(entry.sceneKeys.size(), 8); j++) {
                String key = entry.sceneKeys.get(j);
                String prefix = mode == Mode.MULTI_SELECT && selectedSceneKeys.contains(key) ? "\u2713 " : "  ";
                tooltip.add(Component.literal(prefix + key)
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            if (entry.sceneKeys.size() > 8) {
                tooltip.add(Component.literal("  ...")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        // NBT filter
        if (entry.nbtFilter != null) {
            tooltip.add(Component.translatable("ponderer.ui.item_list.nbt_filter")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(entry.nbtFilter)
                    .withStyle(ChatFormatting.GRAY));
        }

        graphics.renderComponentTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
        graphics.pose().popPose();
    }

    // -- Hit testing --

    private record HitResult(PackSection section, ItemEntry entry) {
    }

    @Nullable
    private HitResult hitTest(double mx, double my) {
        int clipTop = guiTop + GRID_TOP;
        int clipBottom = clipTop + VISIBLE_H;
        if (my < clipTop || my >= clipBottom) return null;

        double currentY = 0;
        for (PackSection section : sections) {
            currentY += SECTION_HEADER_H;
            for (int i = 0; i < section.entries.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
                int iy = (int) (clipTop + currentY + row * CELL_SIZE - scrollY);
                if (mx >= ix && mx < ix + CELL_SIZE && my >= iy && my < iy + CELL_SIZE
                        && iy >= clipTop && iy + CELL_SIZE <= clipBottom) {
                    return new HitResult(section, section.entries.get(i));
                }
            }
            int rows = (section.entries.size() + COLS - 1) / COLS;
            currentY += rows * CELL_SIZE;
        }
        return null;
    }

    // -- Input handling --

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0)
            return super.mouseClicked(mouseX, mouseY, button);

        // Group mode toggle button
        var font = Minecraft.getInstance().font;
        String wHint = UIText.of("ponderer.ui.item_grid.press_w_hint");
        int wHintW = font.width(wHint);
        String groupLabel = groupMode == GroupMode.BY_PACK
                ? UIText.of("ponderer.ui.item_grid.group_by_pack")
                : UIText.of("ponderer.ui.item_grid.group_by_item");
        int groupBtnW = font.width(groupLabel) + 8;
        int groupBtnX = guiLeft + WINDOW_W - 10 - wHintW - groupBtnW - 6;
        int groupBtnY = guiTop + 22;
        int groupBtnH = 12;
        if (isInBox(mouseX, mouseY, groupBtnX, groupBtnY, groupBtnW, groupBtnH)) {
            toggleGroupMode();
            return true;
        }

        int wH = getWindowHeight();

        // MULTI_SELECT action buttons
        if (mode == Mode.MULTI_SELECT) {
            int btnW = 50, btnH = 16;
            int btnY = guiTop + wH - 44;
            int allX = guiLeft + 10;
            int noneX = allX + btnW + 6;
            int cancelX = guiLeft + WINDOW_W - 10 - btnW;
            int okX = cancelX - btnW - 6;

            if (isInBox(mouseX, mouseY, allX, btnY, btnW, btnH)) {
                handleSelectAll();
                return true;
            }
            if (isInBox(mouseX, mouseY, noneX, btnY, btnW, btnH)) {
                handleDeselectAll();
                return true;
            }
            if (isInBox(mouseX, mouseY, okX, btnY, btnW, btnH)) {
                if (onSelectMulti != null)
                    onSelectMulti.accept(selectedSceneKeys);
                return true;
            }
            if (isInBox(mouseX, mouseY, cancelX, btnY, btnW, btnH)) {
                if (onCancel != null)
                    onCancel.run();
                return true;
            }
        }

        // Item grid click
        HitResult hit = hitTest(mouseX, mouseY);
        if (hit != null) {
            handleItemClick(hit.entry);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleItemClick(ItemEntry entry) {
        switch (mode) {
            case LIST -> {
                PonderItemGridScreen returning = new PonderItemGridScreen();
                returning.scrollY = this.scrollY;
                returnScreen = returning;
                openFilteredPonderUI(entry);
            }
            case SINGLE_SELECT -> {
                if (entry.sceneKeys.size() == 1) {
                    if (onSelectSingle != null)
                        onSelectSingle.accept(entry.sceneKeys.get(0));
                } else {
                    Minecraft.getInstance().setScreen(new SceneIdListScreen(
                            entry.sceneKeys, SceneIdListScreen.SelectMode.SINGLE,
                            onSelectSingle, null, null,
                            () -> Minecraft.getInstance().setScreen(this)));
                }
            }
            case MULTI_SELECT -> {
                if (entry.sceneKeys.size() == 1) {
                    String key = entry.sceneKeys.get(0);
                    if (selectedSceneKeys.contains(key))
                        selectedSceneKeys.remove(key);
                    else
                        selectedSceneKeys.add(key);
                } else {
                    Set<String> pre = new HashSet<>();
                    for (String key : entry.sceneKeys) {
                        if (selectedSceneKeys.contains(key))
                            pre.add(key);
                    }
                    PonderItemGridScreen self = this;
                    Minecraft.getInstance().setScreen(new SceneIdListScreen(
                            entry.sceneKeys, SceneIdListScreen.SelectMode.MULTI,
                            null,
                            returnedKeys -> {
                                entry.sceneKeys.forEach(selectedSceneKeys::remove);
                                selectedSceneKeys.addAll(returnedKeys);
                                Minecraft.getInstance().setScreen(self);
                            },
                            pre,
                            () -> Minecraft.getInstance().setScreen(self)));
                }
            }
        }
    }

    /**
     * Open PonderUI filtered to only show scenes matching the entry's sceneKeys.
     * This ensures that clicking an item in a specific pack/NBT group only shows
     * the relevant scenes, not all scenes for that item across all packs.
     */
    private static void openFilteredPonderUI(ItemEntry entry) {
        PonderUI ui = PonderUI.of(entry.stack);
        var accessor = (com.nododiiiii.ponderer.mixin.PonderUIAccessor) (Object) ui;
        List<net.createmod.ponder.foundation.PonderScene> allScenes = accessor.ponderer$getScenes();

        // Resolve which PonderScene IDs + occurrence indices belong to this entry's sceneKeys
        Set<com.nododiiiii.ponderer.ponder.SceneRuntime.PonderSceneRef> refs =
                com.nododiiiii.ponderer.ponder.SceneRuntime.resolvePonderSceneRefs(entry.sceneKeys);

        if (!refs.isEmpty()) {
            // Build a set of (sceneId, occurrenceIndex) to keep
            // Count occurrences as we iterate to match the correct one
            Map<String, int[]> idCounters = new java.util.HashMap<>();
            List<net.createmod.ponder.foundation.PonderScene> filtered = new java.util.ArrayList<>();
            for (net.createmod.ponder.foundation.PonderScene ps : allScenes) {
                String psId = ps.getId().toString();
                int[] counter = idCounters.computeIfAbsent(psId, k -> new int[]{0});
                int occ = counter[0]++;
                if (refs.contains(new com.nododiiiii.ponderer.ponder.SceneRuntime.PonderSceneRef(psId, occ))) {
                    filtered.add(ps);
                }
            }
            if (!filtered.isEmpty() && filtered.size() < allScenes.size()) {
                allScenes.clear();
                allScenes.addAll(filtered);
            }
        }

        ScreenOpener.transitionTo(ui);
    }

    private void handleSelectAll() {
        for (PackSection section : sections) {
            for (ItemEntry entry : section.entries) {
                selectedSceneKeys.addAll(entry.sceneKeys);
            }
        }
    }

    private void handleDeselectAll() {
        selectedSceneKeys.clear();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY - delta * SCROLL_SPEED));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // W key – preview ponder for hovered item
        if (keyCode == GLFW.GLFW_KEY_W) {
            HitResult hit = hitTest(lastMouseX, lastMouseY);
            if (hit != null) {
                PonderItemGridScreen returning = new PonderItemGridScreen();
                returning.scrollY = this.scrollY;
                returnScreen = returning;
                openFilteredPonderUI(hit.entry);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean isInBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void onClose() {
        if (onCancel != null) {
            onCancel.run();
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // =========================================================================
    // Inner class: SceneIdListScreen (for multi-scene items)
    // =========================================================================

    /**
     * List screen for selecting one or more scene keys from a single item
     * that is associated with multiple scenes.
     */
    static class SceneIdListScreen extends AbstractSimiScreen {
        public enum SelectMode {
            SINGLE, MULTI
        }

        private static final int LIST_W = 220;
        private static final int ROW_HEIGHT = 16;
        private static final int VISIBLE_ROWS = 10;

        private final List<String> sceneKeys;
        private final SelectMode selectMode;
        private final @Nullable Consumer<String> onSelectSingle;
        private final @Nullable Consumer<Set<String>> onSelectMulti;
        private final Runnable onCancel;
        private final Set<String> selectedItems = new HashSet<>();
        private int scrollOffset = 0;
        private PonderButton confirmButton;
        private PonderButton cancelButton;

        SceneIdListScreen(List<String> sceneKeys, Consumer<String> onSelect, Runnable onCancel) {
            this(sceneKeys, SelectMode.SINGLE, onSelect, null, null, onCancel);
        }

        SceneIdListScreen(List<String> sceneKeys, SelectMode selectMode,
                @Nullable Consumer<String> onSelectSingle,
                @Nullable Consumer<Set<String>> onSelectMulti,
                @Nullable Set<String> preSelected,
                Runnable onCancel) {
            super(Component.translatable("ponderer.ui.item_grid.select_scene_id"));
            this.sceneKeys = sceneKeys;
            this.selectMode = selectMode;
            this.onSelectSingle = onSelectSingle;
            this.onSelectMulti = onSelectMulti;
            this.onCancel = onCancel;
            if (preSelected != null) {
                this.selectedItems.addAll(preSelected);
            }
        }

        private int getListWindowHeight() {
            int rows = Math.min(sceneKeys.size(), VISIBLE_ROWS);
            int h = 36 + rows * ROW_HEIGHT + 10;
            if (selectMode == SelectMode.MULTI)
                h += 30;
            return h;
        }

        @Override
        protected void init() {
            setWindowSize(LIST_W, getListWindowHeight());
            super.init();
            if (selectMode == SelectMode.MULTI) {
                int wH = getListWindowHeight();
                confirmButton = new PonderButton(guiLeft + 30, guiTop + wH - 30, 50, 16);
                confirmButton.withCallback(() -> {
                    if (onSelectMulti != null)
                        onSelectMulti.accept(selectedItems);
                });
                addRenderableWidget(confirmButton);
                cancelButton = new PonderButton(guiLeft + LIST_W - 80, guiTop + wH - 30, 50, 16);
                cancelButton.withCallback(this::onClose);
                addRenderableWidget(cancelButton);
            }
        }

        @Override
        protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int wH = getListWindowHeight();
            new BoxElement()
                    .withBackground(new Color(0xdd_000000, true))
                    .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                    .at(guiLeft, guiTop, 0)
                    .withBounds(LIST_W, wH)
                    .render(graphics);

            var font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font, this.title, guiLeft + LIST_W / 2, guiTop + 8, 0xFFFFFF);
            graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + LIST_W - 5, guiTop + 21, 0x60_FFFFFF);

            if (selectMode == SelectMode.MULTI) {
                String countText = UIText.of("ponderer.ui.item_grid.sub_selected",
                        selectedItems.size(), sceneKeys.size());
                graphics.drawString(font, countText, guiLeft + 10, guiTop + 23, 0x999999);
            }

            int rows = Math.min(sceneKeys.size(), VISIBLE_ROWS);
            for (int i = 0; i < rows; i++) {
                int idx = scrollOffset + i;
                if (idx >= sceneKeys.size())
                    break;
                int rowY = guiTop + 26 + i * ROW_HEIGHT;
                boolean hovered = mouseX >= guiLeft + 6 && mouseX < guiLeft + LIST_W - 6
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                String sceneKey = sceneKeys.get(idx);
                boolean isSelected = selectedItems.contains(sceneKey);

                if (hovered || isSelected) {
                    int color = hovered ? 0x60_FFFFFF : 0x40_4080FF;
                    graphics.fill(guiLeft + 6, rowY, guiLeft + LIST_W - 6, rowY + ROW_HEIGHT, color);
                }
                if (selectMode == SelectMode.MULTI) {
                    String prefix = isSelected ? "\u2713 " : "  ";
                    graphics.drawString(font, prefix + sceneKey, guiLeft + 10, rowY + 4,
                            hovered ? 0x80FFFF : 0xCCCCCC);
                } else {
                    graphics.drawString(font, sceneKey, guiLeft + 10, rowY + 4,
                            hovered ? 0x80FFFF : 0xCCCCCC);
                }
            }
        }

        @Override
        protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (selectMode == SelectMode.MULTI) {
                var font = Minecraft.getInstance().font;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 500);
                graphics.drawCenteredString(font, UIText.of("ponderer.ui.confirm"),
                        confirmButton.getX() + 25, confirmButton.getY() + 4, 0xFFFFFF);
                graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
                        cancelButton.getX() + 25, cancelButton.getY() + 4, 0xFFFFFF);
                graphics.pose().popPose();
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int rows = Math.min(sceneKeys.size(), VISIBLE_ROWS);
                for (int i = 0; i < rows; i++) {
                    int idx = scrollOffset + i;
                    if (idx >= sceneKeys.size())
                        break;
                    int rowY = guiTop + 26 + i * ROW_HEIGHT;
                    if (mouseX >= guiLeft + 6 && mouseX < guiLeft + LIST_W - 6
                            && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                        String sceneKey = sceneKeys.get(idx);
                        if (selectMode == SelectMode.SINGLE) {
                            if (onSelectSingle != null)
                                onSelectSingle.accept(sceneKey);
                        } else {
                            if (selectedItems.contains(sceneKey))
                                selectedItems.remove(sceneKey);
                            else
                                selectedItems.add(sceneKey);
                        }
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            int maxScroll = Math.max(0, sceneKeys.size() - VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
            return true;
        }

        @Override
        public void onClose() {
            onCancel.run();
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }
    }
}
