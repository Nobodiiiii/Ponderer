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
 * Unified item grid screen that replaces PonderItemListScreen,
 * SceneItemPickerScreen (single-select) and SceneItemPickerScreen
 * (multi-select).
 * <p>
 * Three modes:
 * <ul>
 * <li>LIST – browse all items with ponder scenes; click opens PonderUI</li>
 * <li>SINGLE_SELECT – pick one scene ID via callback</li>
 * <li>MULTI_SELECT – pick multiple scene IDs with confirm/cancel</li>
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
    private static final int ROWS_PER_PAGE = 6;
    private static final int ITEMS_PER_PAGE = COLS * ROWS_PER_PAGE;
    private static final int CELL_SIZE = 20;
    private static final int GRID_LEFT = 14;
    private static final int GRID_TOP = 42;

    // -- Data model --
    record ItemEntry(ItemStack stack, @Nullable String nbtFilter, List<String> sceneIds) {
    }

    // -- State --
    private final Mode mode;
    private final List<ItemEntry> entries;
    private final @Nullable Consumer<String> onSelectSingle;
    private final @Nullable Consumer<Set<String>> onSelectMulti;
    private final @Nullable Runnable onCancel;
    private final Set<String> selectedSceneIds = new HashSet<>();
    private int page = 0;
    private int totalPages;
    private int totalSceneCount;
    private int lastMouseX, lastMouseY;

    /** When non-null, PonderUI close will return to this screen (via Mixin). */
    @Nullable
    public static PonderItemGridScreen returnScreen;

    // -- Constructors --

    /** LIST mode – browse items, click opens PonderUI */
    public PonderItemGridScreen() {
        this(Mode.LIST, null, null, null);
    }

    /** SINGLE_SELECT mode – pick one scene ID */
    public PonderItemGridScreen(Consumer<String> onSelect, Runnable onCancel) {
        this(Mode.SINGLE_SELECT, onSelect, null, onCancel);
    }

    /** MULTI_SELECT mode – pick multiple scene IDs */
    public PonderItemGridScreen(Consumer<Set<String>> onSelectMulti, Runnable onCancel, boolean multi) {
        this(Mode.MULTI_SELECT, null, onSelectMulti, onCancel);
    }

    private PonderItemGridScreen(Mode mode, @Nullable Consumer<String> onSelectSingle,
            @Nullable Consumer<Set<String>> onSelectMulti,
            @Nullable Runnable onCancel) {
        super(Component.translatable(switch (mode) {
            case LIST -> "ponderer.ui.item_grid.title";
            case SINGLE_SELECT -> "ponderer.ui.item_grid.select_scene";
            case MULTI_SELECT -> "ponderer.ui.item_grid.select_scenes";
        }));
        this.mode = mode;
        this.onSelectSingle = onSelectSingle;
        this.onSelectMulti = onSelectMulti;
        this.onCancel = onCancel;
        this.entries = collectEntries();
        this.totalPages = Math.max(1, (entries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        this.totalSceneCount = (int) entries.stream()
                .flatMap(e -> e.sceneIds.stream()).distinct().count();
    }

    /** Collect all item entries with their associated scene IDs. */
    private static List<ItemEntry> collectEntries() {
        LinkedHashMap<String, List<String>> itemFilters = new LinkedHashMap<>();
        Map<String, List<String>> allSceneIds = new LinkedHashMap<>();

        for (DslScene scene : SceneRuntime.getScenes()) {
            if (scene.items == null || scene.id == null)
                continue;
            for (String itemId : scene.items) {
                String nf = scene.nbtFilter;
                String key = itemId + "|" + (nf != null ? nf : "");
                allSceneIds.computeIfAbsent(key, k -> new ArrayList<>());
                List<String> ids = allSceneIds.get(key);
                if (!ids.contains(scene.id))
                    ids.add(scene.id);
                itemFilters.computeIfAbsent(itemId, k -> new ArrayList<>());
                List<String> filters = itemFilters.get(itemId);
                if (nf != null && !nf.isBlank()) {
                    if (!filters.contains(nf))
                        filters.add(nf);
                } else {
                    if (!filters.contains(null))
                        filters.add(0, null);
                }
            }
        }

        List<ItemEntry> stacks = new ArrayList<>();
        for (var entry : itemFilters.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl == null)
                continue;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == Items.AIR)
                continue;
            for (String nf : entry.getValue()) {
                ItemStack stack = new ItemStack(item);
                if (nf != null) {
                    try {
                        CompoundTag filterTag = TagParser.parseTag(nf);
                        CompoundTag fullTag = new CompoundTag();
                        fullTag.putString("id", rl.toString());
                        fullTag.putByte("Count", (byte) 1);
                        fullTag.put("tag", filterTag);
                        ItemStack parsed = ItemStack.of(fullTag);
                        if (!parsed.isEmpty())
                            stack = parsed;
                    } catch (Exception ignored) {
                    }
                }
                String key = entry.getKey() + "|" + (nf != null ? nf : "");
                List<String> sceneIds = allSceneIds.getOrDefault(key, List.of());
                if (!sceneIds.isEmpty()) {
                    stacks.add(new ItemEntry(stack, nf, sceneIds));
                }
            }
        }
        return stacks;
    }

    /** For ExportPackScreen to read current selection state. */
    public Set<String> getSelectedSceneIds() {
        return selectedSceneIds;
    }

    private int getWindowHeight() {
        return GRID_TOP + ROWS_PER_PAGE * CELL_SIZE + (mode == Mode.MULTI_SELECT ? 70 : 40);
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, getWindowHeight());
        super.init();
    }

    /** 0=none, 1=partial, 2=full */
    private int getSelectionState(ItemEntry entry) {
        if (mode != Mode.MULTI_SELECT)
            return 0;
        boolean any = false, all = true;
        for (String sid : entry.sceneIds) {
            if (selectedSceneIds.contains(sid))
                any = true;
            else
                all = false;
        }
        if (all)
            return 2;
        if (any)
            return 1;
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
                    selectedSceneIds.size(), totalSceneCount);
        } else {
            subtitle = UIText.of("ponderer.ui.item_grid.total_scenes",
                    entries.size(), totalSceneCount);
        }
        graphics.drawString(font, subtitle, guiLeft + 10, guiTop + 25, 0x999999);

        // [W] hint (right-aligned on subtitle line)
        String wHint = UIText.of("ponderer.ui.item_grid.press_w_hint");
        int wHintW = font.width(wHint);
        graphics.drawString(font, wHint, guiLeft + WINDOW_W - 10 - wHintW, guiTop + 25, 0x555555);

        // Empty state
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("ponderer.ui.item_grid.empty"),
                    guiLeft + WINDOW_W / 2, guiTop + GRID_TOP + 30, 0x999999);
            return;
        }

        // -- Item grid --
        int startIdx = page * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());
        for (int i = startIdx; i < endIdx; i++) {
            int li = i - startIdx;
            int col = li % COLS;
            int row = li / COLS;
            int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
            int iy = guiTop + GRID_TOP + row * CELL_SIZE;

            ItemEntry entry = entries.get(i);
            boolean hovered = mouseX >= ix && mouseX < ix + CELL_SIZE
                    && mouseY >= iy && mouseY < iy + CELL_SIZE;
            int selState = getSelectionState(entry);

            // Selection highlight
            if (selState == 2) {
                graphics.fill(ix, iy, ix + CELL_SIZE, iy + CELL_SIZE, 0x60_4080FF);
            } else if (selState == 1) {
                // Partially selected: striped/dimmer highlight
                graphics.fill(ix, iy, ix + CELL_SIZE, iy + CELL_SIZE, 0x30_FFAA00);
            }

            // Hover highlight
            if (hovered) {
                graphics.fill(ix, iy, ix + CELL_SIZE, iy + CELL_SIZE, 0x40_FFFFFF);
            }

            graphics.renderItem(entry.stack, ix + 2, iy + 2);

            // NBT indicator (orange dot, top-right corner)
            if (entry.nbtFilter != null) {
                graphics.fill(ix + CELL_SIZE - 5, iy + 1, ix + CELL_SIZE - 1, iy + 5, 0xFF_FFAA00);
            }
            // Multi-scene indicator (blue dot, top-left corner)
            if (entry.sceneIds.size() > 1) {
                graphics.fill(ix + 1, iy + 1, ix + 5, iy + 5, 0xFF_55AAFF);
            }
        }

        // -- Pagination --
        if (totalPages > 1) {
            renderPagination(graphics, font, mouseX, mouseY, wH);
        } else {
            String pageText = UIText.of("ponderer.ui.item_list.page", 1, 1);
            graphics.drawCenteredString(font, pageText, guiLeft + WINDOW_W / 2, guiTop + wH - 22, 0xCCCCCC);
        }

        // -- MULTI_SELECT action buttons --
        if (mode == Mode.MULTI_SELECT) {
            renderMultiSelectButtons(graphics, font, mouseX, mouseY, wH);
        }
    }

    private void renderPagination(GuiGraphics graphics, net.minecraft.client.gui.Font font,
            int mouseX, int mouseY, int wH) {
        String pageText = UIText.of("ponderer.ui.item_list.page", page + 1, totalPages);
        int pageY = guiTop + wH - 22;
        graphics.drawCenteredString(font, pageText, guiLeft + WINDOW_W / 2, pageY, 0xCCCCCC);

        int btnW = 20, btnH = 16;
        int prevX = guiLeft + 10, nextX = guiLeft + WINDOW_W - 30;
        int by = pageY - 4;
        for (int bi = 0; bi < 2; bi++) {
            int bx = bi == 0 ? prevX : nextX;
            String lbl = bi == 0 ? "<" : ">";
            boolean enabled = bi == 0 ? page > 0 : page < totalPages - 1;
            boolean hov = enabled && mouseX >= bx && mouseX < bx + btnW
                    && mouseY >= by && mouseY < by + btnH;
            int bg = hov ? 0x80_4466aa : (enabled ? 0x60_333366 : 0x30_222244);
            int bdr = hov ? 0xCC_6688cc : 0x60_555588;
            renderBoxButton(graphics, bx, by, btnW, btnH, bg, bdr);
            int tc = enabled ? (hov ? 0xFFFFFF : 0xCCCCCC) : 0x666666;
            graphics.drawCenteredString(font, lbl, bx + btnW / 2,
                    by + (btnH - font.lineHeight) / 2 + 1, tc);
        }
    }

    private void renderMultiSelectButtons(GuiGraphics graphics, net.minecraft.client.gui.Font font,
            int mouseX, int mouseY, int wH) {
        int btnW = 50, btnH = 16;
        int btnY = guiTop + wH - 44;
        // [All] [None] [OK] [Cancel]
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
        if (entries.isEmpty())
            return;

        int startIdx = page * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());

        for (int i = startIdx; i < endIdx; i++) {
            int li = i - startIdx;
            int col = li % COLS;
            int row = li / COLS;
            int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
            int iy = guiTop + GRID_TOP + row * CELL_SIZE;

            if (mouseX >= ix && mouseX < ix + CELL_SIZE && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 600);
                ItemEntry entry = entries.get(i);
                List<Component> tooltip = new ArrayList<>(
                        entry.stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.NORMAL));
                tooltip.add(Component.literal(""));

                // Scene IDs
                if (entry.sceneIds.size() == 1) {
                    String sid = entry.sceneIds.get(0);
                    String prefix = mode == Mode.MULTI_SELECT && selectedSceneIds.contains(sid) ? "\u2713 " : "";
                    tooltip.add(Component.literal(prefix)
                            .append(Component.translatable("ponderer.ui.item_grid.scene_label", sid))
                            .withStyle(ChatFormatting.AQUA));
                } else {
                    tooltip.add(Component.translatable("ponderer.ui.item_grid.scenes_count", entry.sceneIds.size())
                            .withStyle(ChatFormatting.AQUA));
                    for (int j = 0; j < Math.min(entry.sceneIds.size(), 8); j++) {
                        String sid = entry.sceneIds.get(j);
                        String prefix = mode == Mode.MULTI_SELECT && selectedSceneIds.contains(sid)
                                ? "\u2713 "
                                : "  ";
                        tooltip.add(Component.literal(prefix + sid)
                                .withStyle(ChatFormatting.DARK_AQUA));
                    }
                    if (entry.sceneIds.size() > 8) {
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
                break;
            }
        }
    }

    // -- Input handling --

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0)
            return super.mouseClicked(mouseX, mouseY, button);

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
                    onSelectMulti.accept(selectedSceneIds);
                return true;
            }
            if (isInBox(mouseX, mouseY, cancelX, btnY, btnW, btnH)) {
                if (onCancel != null)
                    onCancel.run();
                return true;
            }
        }

        // Pagination
        if (totalPages > 1) {
            int pageY = guiTop + wH - 22 - 4;
            int btnW = 20, btnH = 16;
            int prevX = guiLeft + 10, nextX = guiLeft + WINDOW_W - 30;
            if (page > 0 && isInBox(mouseX, mouseY, prevX, pageY, btnW, btnH)) {
                page--;
                return true;
            }
            if (page < totalPages - 1 && isInBox(mouseX, mouseY, nextX, pageY, btnW, btnH)) {
                page++;
                return true;
            }
        }

        // Item grid click
        if (!entries.isEmpty()) {
            int startIdx = page * ITEMS_PER_PAGE;
            int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());
            for (int i = startIdx; i < endIdx; i++) {
                int li = i - startIdx;
                int col = li % COLS;
                int row = li / COLS;
                int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
                int iy = guiTop + GRID_TOP + row * CELL_SIZE;
                if (mouseX >= ix && mouseX < ix + CELL_SIZE
                        && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                    handleItemClick(entries.get(i));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleItemClick(ItemEntry entry) {
        switch (mode) {
            case LIST -> {
                returnScreen = new PonderItemGridScreen();
                returnScreen.page = this.page;
                ScreenOpener.transitionTo(PonderUI.of(entry.stack));
            }
            case SINGLE_SELECT -> {
                if (entry.sceneIds.size() == 1) {
                    if (onSelectSingle != null)
                        onSelectSingle.accept(entry.sceneIds.get(0));
                } else {
                    Minecraft.getInstance().setScreen(new SceneIdListScreen(
                            entry.sceneIds, SceneIdListScreen.SelectMode.SINGLE,
                            onSelectSingle, null, null,
                            () -> Minecraft.getInstance().setScreen(this)));
                }
            }
            case MULTI_SELECT -> {
                if (entry.sceneIds.size() == 1) {
                    String sid = entry.sceneIds.get(0);
                    if (selectedSceneIds.contains(sid))
                        selectedSceneIds.remove(sid);
                    else
                        selectedSceneIds.add(sid);
                } else {
                    // Pre-select currently selected scenes for this item
                    Set<String> pre = new HashSet<>();
                    for (String sid : entry.sceneIds) {
                        if (selectedSceneIds.contains(sid))
                            pre.add(sid);
                    }
                    PonderItemGridScreen self = this;
                    Minecraft.getInstance().setScreen(new SceneIdListScreen(
                            entry.sceneIds, SceneIdListScreen.SelectMode.MULTI,
                            null,
                            returnedIds -> {
                                entry.sceneIds.forEach(selectedSceneIds::remove);
                                selectedSceneIds.addAll(returnedIds);
                                Minecraft.getInstance().setScreen(self);
                            },
                            pre,
                            () -> Minecraft.getInstance().setScreen(self)));
                }
            }
        }
    }

    private void handleSelectAll() {
        for (ItemEntry entry : entries) {
            selectedSceneIds.addAll(entry.sceneIds);
        }
    }

    private void handleDeselectAll() {
        selectedSceneIds.clear();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // W key – preview ponder for hovered item
        if (keyCode == GLFW.GLFW_KEY_W) {
            ItemEntry hovered = getHoveredEntry(lastMouseX, lastMouseY);
            if (hovered != null) {
                returnScreen = this;
                ScreenOpener.transitionTo(PonderUI.of(hovered.stack));
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Nullable
    private ItemEntry getHoveredEntry(int mx, int my) {
        if (entries.isEmpty())
            return null;
        int startIdx = page * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());
        for (int i = startIdx; i < endIdx; i++) {
            int li = i - startIdx;
            int col = li % COLS;
            int row = li / COLS;
            int ix = guiLeft + GRID_LEFT + col * CELL_SIZE;
            int iy = guiTop + GRID_TOP + row * CELL_SIZE;
            if (mx >= ix && mx < ix + CELL_SIZE && my >= iy && my < iy + CELL_SIZE) {
                return entries.get(i);
            }
        }
        return null;
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
     * List screen for selecting one or more scene IDs from a single item
     * that is associated with multiple scenes.
     */
    static class SceneIdListScreen extends AbstractSimiScreen {
        public enum SelectMode {
            SINGLE, MULTI
        }

        private static final int LIST_W = 220;
        private static final int ROW_HEIGHT = 16;
        private static final int VISIBLE_ROWS = 10;

        private final List<String> sceneIds;
        private final SelectMode selectMode;
        private final @Nullable Consumer<String> onSelectSingle;
        private final @Nullable Consumer<Set<String>> onSelectMulti;
        private final Runnable onCancel;
        private final Set<String> selectedItems = new HashSet<>();
        private int scrollOffset = 0;
        private PonderButton confirmButton;
        private PonderButton cancelButton;

        SceneIdListScreen(List<String> sceneIds, Consumer<String> onSelect, Runnable onCancel) {
            this(sceneIds, SelectMode.SINGLE, onSelect, null, null, onCancel);
        }

        SceneIdListScreen(List<String> sceneIds, SelectMode selectMode,
                @Nullable Consumer<String> onSelectSingle,
                @Nullable Consumer<Set<String>> onSelectMulti,
                @Nullable Set<String> preSelected,
                Runnable onCancel) {
            super(Component.translatable("ponderer.ui.item_grid.select_scene_id"));
            this.sceneIds = sceneIds;
            this.selectMode = selectMode;
            this.onSelectSingle = onSelectSingle;
            this.onSelectMulti = onSelectMulti;
            this.onCancel = onCancel;
            if (preSelected != null) {
                this.selectedItems.addAll(preSelected);
            }
        }

        private int getListWindowHeight() {
            int rows = Math.min(sceneIds.size(), VISIBLE_ROWS);
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
                        selectedItems.size(), sceneIds.size());
                graphics.drawString(font, countText, guiLeft + 10, guiTop + 23, 0x999999);
            }

            int rows = Math.min(sceneIds.size(), VISIBLE_ROWS);
            for (int i = 0; i < rows; i++) {
                int idx = scrollOffset + i;
                if (idx >= sceneIds.size())
                    break;
                int rowY = guiTop + 26 + i * ROW_HEIGHT;
                boolean hovered = mouseX >= guiLeft + 6 && mouseX < guiLeft + LIST_W - 6
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                String sceneId = sceneIds.get(idx);
                boolean isSelected = selectedItems.contains(sceneId);

                if (hovered || isSelected) {
                    int color = hovered ? 0x60_FFFFFF : 0x40_4080FF;
                    graphics.fill(guiLeft + 6, rowY, guiLeft + LIST_W - 6, rowY + ROW_HEIGHT, color);
                }
                if (selectMode == SelectMode.MULTI) {
                    String prefix = isSelected ? "\u2713 " : "  ";
                    graphics.drawString(font, prefix + sceneId, guiLeft + 10, rowY + 4,
                            hovered ? 0x80FFFF : 0xCCCCCC);
                } else {
                    graphics.drawString(font, sceneId, guiLeft + 10, rowY + 4,
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
                int rows = Math.min(sceneIds.size(), VISIBLE_ROWS);
                for (int i = 0; i < rows; i++) {
                    int idx = scrollOffset + i;
                    if (idx >= sceneIds.size())
                        break;
                    int rowY = guiTop + 26 + i * ROW_HEIGHT;
                    if (mouseX >= guiLeft + 6 && mouseX < guiLeft + LIST_W - 6
                            && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                        String sceneId = sceneIds.get(idx);
                        if (selectMode == SelectMode.SINGLE) {
                            if (onSelectSingle != null)
                                onSelectSingle.accept(sceneId);
                        } else {
                            if (selectedItems.contains(sceneId))
                                selectedItems.remove(sceneId);
                            else
                                selectedItems.add(sceneId);
                        }
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            int maxScroll = Math.max(0, sceneIds.size() - VISIBLE_ROWS);
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
