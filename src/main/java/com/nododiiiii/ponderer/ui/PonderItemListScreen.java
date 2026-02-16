package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.NbtSceneFilter;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
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

import javax.annotation.Nullable;
import java.util.*;

/**
 * Screen that lists all items with registered ponder scenes.
 * Items with different NBT filters appear as separate entries.
 * Clicking an item opens its PonderUI.
 */
public class PonderItemListScreen extends NavigatableSimiScreen {

    private static final int WINDOW_W = 256;
    private static final int COLS = 11;
    private static final int ROWS_PER_PAGE = 6;
    private static final int ITEMS_PER_PAGE = COLS * ROWS_PER_PAGE;
    private static final int CELL_SIZE = 20;
    private static final int GRID_LEFT = 14;
    private static final int GRID_TOP = 42;

    private record ItemEntry(ItemStack stack, @Nullable String nbtFilter) {}

    private final List<ItemEntry> entries;
    private int page = 0;
    private int totalPages;
    private PonderButton prevButton;
    private PonderButton nextButton;

    public PonderItemListScreen() {
        // Collect unique (itemId, nbtFilter) pairs from all loaded scenes
        LinkedHashMap<String, List<String>> itemFilters = new LinkedHashMap<>();
        for (DslScene scene : SceneRuntime.getScenes()) {
            if (scene.items == null) continue;
            for (String itemId : scene.items) {
                itemFilters.computeIfAbsent(itemId, k -> new ArrayList<>());
                String nf = scene.nbtFilter;
                List<String> filters = itemFilters.get(itemId);
                if (nf != null && !nf.isBlank()) {
                    if (!filters.contains(nf)) filters.add(nf);
                } else {
                    if (!filters.contains(null)) filters.add(0, null);
                }
            }
        }
        List<ItemEntry> stacks = new ArrayList<>();
        for (var entry : itemFilters.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl == null) continue;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == Items.AIR) continue;
            for (String nf : entry.getValue()) {
                ItemStack stack = new ItemStack(item);
                if (nf != null) {
                    // Try to apply NBT filter to stack for visual distinction
                    try {
                        CompoundTag filterTag = TagParser.parseTag(nf);
                        CompoundTag fullTag = new CompoundTag();
                        fullTag.putString("id", rl.toString());
                        fullTag.putByte("Count", (byte) 1);
                        fullTag.put("tag", filterTag);
                        ItemStack parsed = ItemStack.of(fullTag);
                        if (!parsed.isEmpty()) stack = parsed;
                    } catch (Exception ignored) {}
                }
                stacks.add(new ItemEntry(stack, nf));
            }
        }
        this.entries = stacks;
        this.totalPages = Math.max(1, (entries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    @Override
    protected void init() {
        super.init();
        int wH = getWindowHeight();

        int gLeft = (width - WINDOW_W) / 2;
        int gTop = (height - wH) / 2;

        // Prev / Next page buttons
        prevButton = new PonderButton(gLeft + 10, gTop + wH - 26, 30, 16);
        prevButton.withCallback(() -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        });
        addRenderableWidget(prevButton);

        nextButton = new PonderButton(gLeft + WINDOW_W - 40, gTop + wH - 26, 30, 16);
        nextButton.withCallback(() -> {
            if (page < totalPages - 1) {
                page++;
                rebuildWidgets();
            }
        });
        addRenderableWidget(nextButton);
    }

    @Override
    protected void initBackTrackIcon(BoxWidget backTrack) {
        backTrack.showing(PonderGuiTextures.ICON_PONDER_CLOSE);
    }

    private int getWindowHeight() {
        return GRID_TOP + ROWS_PER_PAGE * CELL_SIZE + 40;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);

        int wH = getWindowHeight();
        int gLeft = (width - WINDOW_W) / 2;
        int gTop = (height - wH) / 2;

        // Background
        new BoxElement()
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(gLeft, gTop, 0)
                .withBounds(WINDOW_W, wH)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        // Title
        graphics.drawString(font, Component.translatable("ponderer.ui.item_list.title"), gLeft + 10, gTop + 8, 0xFFFFFF);
        graphics.fill(gLeft + 5, gTop + 20, gLeft + WINDOW_W - 5, gTop + 21, 0x60_FFFFFF);

        // Sub-title: scene count
        String subtitle = UIText.of("ponderer.ui.item_list.count", entries.size());
        graphics.drawString(font, subtitle, gLeft + 10, gTop + 25, 0x999999);

        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("ponderer.ui.item_list.empty"),
                    gLeft + WINDOW_W / 2, gTop + GRID_TOP + 30, 0x999999);
            return;
        }

        // Render item grid
        int startIdx = page * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());
        for (int i = startIdx; i < endIdx; i++) {
            int localIdx = i - startIdx;
            int col = localIdx % COLS;
            int row = localIdx / COLS;
            int ix = gLeft + GRID_LEFT + col * CELL_SIZE;
            int iy = gTop + GRID_TOP + row * CELL_SIZE;

            ItemEntry entry = entries.get(i);

            // Highlight on hover
            if (mouseX >= ix && mouseX < ix + CELL_SIZE && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                graphics.fill(ix, iy, ix + CELL_SIZE, iy + CELL_SIZE, 0x40_FFFFFF);
            }

            graphics.renderItem(entry.stack, ix + 2, iy + 2);

            // NBT indicator: small colored dot in top-right corner
            if (entry.nbtFilter != null) {
                graphics.fill(ix + CELL_SIZE - 5, iy + 1, ix + CELL_SIZE - 1, iy + 5, 0xFF_FFAA00);
            }
        }

        // Page indicator
        String pageText = UIText.of("ponderer.ui.item_list.page", page + 1, totalPages);
        graphics.drawCenteredString(font, pageText, gLeft + WINDOW_W / 2, gTop + wH - 22, 0xCCCCCC);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;

        int wH = getWindowHeight();
        int gLeft = (width - WINDOW_W) / 2;
        int gTop = (height - wH) / 2;

        // Push z above buttons
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        // Prev / Next labels
        if (page > 0) {
            graphics.drawCenteredString(font, "<", prevButton.getX() + 15, prevButton.getY() + 4, 0xFFFFFF);
        }
        if (page < totalPages - 1) {
            graphics.drawCenteredString(font, ">", nextButton.getX() + 15, nextButton.getY() + 4, 0xFFFFFF);
        }

        graphics.pose().popPose();

        // Tooltip for hovered item
        if (!entries.isEmpty()) {
            int startIdx = page * ITEMS_PER_PAGE;
            int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());
            for (int i = startIdx; i < endIdx; i++) {
                int localIdx = i - startIdx;
                int col = localIdx % COLS;
                int row = localIdx / COLS;
                int ix = gLeft + GRID_LEFT + col * CELL_SIZE;
                int iy = gTop + GRID_TOP + row * CELL_SIZE;

                if (mouseX >= ix && mouseX < ix + CELL_SIZE && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0, 0, 600);
                    ItemEntry entry = entries.get(i);
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.addAll(getItemTooltip(Minecraft.getInstance(), entry.stack));
                    if (entry.nbtFilter != null) {
                        tooltip.add(Component.literal(""));
                        tooltip.add(Component.translatable("ponderer.ui.item_list.nbt_filter")
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
                        tooltip.add(Component.literal(entry.nbtFilter)
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                    graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                    graphics.pose().popPose();
                    break;
                }
            }
        }
    }

    private static List<Component> getItemTooltip(Minecraft mc, ItemStack stack) {
        return stack.getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !entries.isEmpty()) {
            int wH = getWindowHeight();
            int gLeft = (width - WINDOW_W) / 2;
            int gTop = (height - wH) / 2;

            int startIdx = page * ITEMS_PER_PAGE;
            int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());
            for (int i = startIdx; i < endIdx; i++) {
                int localIdx = i - startIdx;
                int col = localIdx % COLS;
                int row = localIdx / COLS;
                int ix = gLeft + GRID_LEFT + col * CELL_SIZE;
                int iy = gTop + GRID_TOP + row * CELL_SIZE;

                if (mouseX >= ix && mouseX < ix + CELL_SIZE && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                    ItemEntry clicked = entries.get(i);
                    openPonderForItem(clicked);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openPonderForItem(ItemEntry entry) {
        ScreenOpener.transitionTo(PonderUI.of(entry.stack));
    }

    @Override
    public boolean isEquivalentTo(NavigatableSimiScreen other) {
        return other instanceof PonderItemListScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
