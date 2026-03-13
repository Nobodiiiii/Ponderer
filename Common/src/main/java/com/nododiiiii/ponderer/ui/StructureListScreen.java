package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Popup screen that lists all available structures from the dynamic structure registry.
 * Users can scroll, search (by typing), and click to select a structure.
 */
public class StructureListScreen extends AbstractSimiScreen {

    private static final int WINDOW_W = 240;
    private static final int WINDOW_H = 220;
    private static final int ROW_HEIGHT = 14;
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 26;

    private final Screen parent;
    private final Consumer<String> onSelect;

    private List<String> allStructures = new ArrayList<>();
    private List<String> filtered = new ArrayList<>();
    private String searchText = "";
    private int scrollOffset = 0;
    private int hoveredRow = -1;
    private BoxWidget cancelBtn;

    public StructureListScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.translatable("ponderer.ui.scene_desc.structure_list_title"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, WINDOW_H);
        super.init();

        allStructures.clear();
        try {
            Registry<?> registry = null;
            // Try integrated server first (singleplayer has full worldgen registries)
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
            // Fallback: no connection or registry not available
        }
        Collections.sort(allStructures);
        applyFilter();

        // Cancel button
        int btnW = 60, btnH = 18;
        cancelBtn = new PonderButton(guiLeft + WINDOW_W / 2 - btnW / 2,
                guiTop + WINDOW_H - FOOTER_H + 2, btnW, btnH);
        cancelBtn.withCallback(() -> Minecraft.getInstance().setScreen(parent));
        addRenderableWidget(cancelBtn);
    }

    private void applyFilter() {
        filtered.clear();
        String lower = searchText.toLowerCase(Locale.ROOT);
        for (String s : allStructures) {
            if (lower.isEmpty() || s.toLowerCase(Locale.ROOT).contains(lower)) {
                filtered.add(s);
            }
        }
        scrollOffset = 0;
    }

    private int maxVisible() {
        return (WINDOW_H - HEADER_H - FOOTER_H) / ROW_HEIGHT;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        new BoxElement()
                .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
                .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true),
                        new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(WINDOW_W, WINDOW_H)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        // Title
        graphics.drawString(font, UIText.of("ponderer.ui.scene_desc.structure_list_title"),
                guiLeft + 10, guiTop + 4, 0xFFFFFF);

        // Search text
        String searchDisplay = "> " + searchText + "_";
        graphics.drawString(font, searchDisplay, guiLeft + 10, guiTop + 16, 0xAAFFAA);

        // Separator
        graphics.fill(guiLeft + 5, guiTop + HEADER_H - 2, guiLeft + WINDOW_W - 5,
                guiTop + HEADER_H - 1, UILayoutConstants.COLOR_SEPARATOR);

        // List
        int listTop = guiTop + HEADER_H;
        int maxVis = maxVisible();
        hoveredRow = -1;

        graphics.enableScissor(guiLeft, listTop, guiLeft + WINDOW_W,
                guiTop + WINDOW_H - FOOTER_H);

        int end = Math.min(filtered.size(), scrollOffset + maxVis);
        for (int i = scrollOffset; i < end; i++) {
            int y = listTop + (i - scrollOffset) * ROW_HEIGHT;
            String entry = filtered.get(i);

            boolean hovered = mouseX >= guiLeft + 5 && mouseX <= guiLeft + WINDOW_W - 5
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                hoveredRow = i;
                graphics.fill(guiLeft + 5, y, guiLeft + WINDOW_W - 5, y + ROW_HEIGHT, 0x40_80a0ff);
            } else if (i % 2 == 0) {
                graphics.fill(guiLeft + 5, y, guiLeft + WINDOW_W - 5, y + ROW_HEIGHT, 0x20_FFFFFF);
            }

            String clipped = font.plainSubstrByWidth(entry, WINDOW_W - 20);
            graphics.drawString(font, clipped, guiLeft + 10, y + 3, hovered ? 0xFFFFFF : 0xCCCCCC);
        }

        graphics.disableScissor();

        // Scrollbar
        if (filtered.size() > maxVis) {
            int trackX = guiLeft + WINDOW_W - UILayoutConstants.SCROLLBAR_W - 3;
            int trackTop = listTop;
            int trackH = maxVis * ROW_HEIGHT;
            graphics.fill(trackX, trackTop, trackX + UILayoutConstants.SCROLLBAR_W,
                    trackTop + trackH, UILayoutConstants.COLOR_SCROLLBAR_BG);
            int maxOff = filtered.size() - maxVis;
            int thumbH = Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB,
                    trackH * maxVis / filtered.size());
            int thumbY = trackTop + (maxOff > 0
                    ? (int) ((float) scrollOffset / maxOff * (trackH - thumbH)) : 0);
            graphics.fill(trackX, thumbY, trackX + UILayoutConstants.SCROLLBAR_W,
                    thumbY + thumbH, UILayoutConstants.COLOR_SCROLLBAR_FG);
        }

        // Footer separator
        graphics.fill(guiLeft + 5, guiTop + WINDOW_H - FOOTER_H,
                guiLeft + WINDOW_W - 5, guiTop + WINDOW_H - FOOTER_H + 1,
                UILayoutConstants.COLOR_SEPARATOR);

        // Count
        String count = filtered.size() + " / " + allStructures.size();
        graphics.drawString(font, count, guiLeft + WINDOW_W - font.width(count) - 10,
                guiTop + WINDOW_H - FOOTER_H + 6, 0x808080);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        // Cancel button label
        if (cancelBtn != null) {
            graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
                    cancelBtn.getX() + 30, cancelBtn.getY() + 5, 0xFFFFFF);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredRow >= 0 && hoveredRow < filtered.size()) {
            onSelect.accept(filtered.get(hoveredRow));
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxOff = Math.max(0, filtered.size() - maxVisible());
        scrollOffset = (int) Math.max(0, Math.min(maxOff, scrollOffset - scrollY * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
            searchText = searchText.substring(0, searchText.length() - 1);
            applyFilter();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && !filtered.isEmpty()) {
            onSelect.accept(filtered.get(Math.min(hoveredRow >= 0 ? hoveredRow : 0,
                    filtered.size() - 1)));
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        // Intercept all other keys so they don't propagate to underlying game screens.
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint >= ' ') {
            searchText += codePoint;
            applyFilter();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
