package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.PonderPackInfo;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Screen for importing Ponderer packs from resourcepacks directory.
 */
public class ImportPackScreen extends AbstractSimiScreen {

    private static final int WINDOW_W = 280;
    private static final int WINDOW_H = 300;
    private static final int PACK_LIST_HEIGHT = 200;

    private List<PonderPackInfo> availablePacks;
    private int selectedIndex = -1;

    private PonderButton loadButton;
    private PonderButton cancelButton;
    private int scrollOffset = 0;

    public ImportPackScreen() {
        super(Component.literal("Import Ponderer Pack"));
        this.availablePacks = new ArrayList<>();
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, WINDOW_H);
        super.init();

        // Scan for available packs
        scanResourcePacks();

        int btnW = 80;
        int btnH = 18;

        // Load button
        loadButton = new PonderButton(guiLeft + 30, guiTop + WINDOW_H - 30, btnW, btnH);
        loadButton.withCallback(() -> onLoadClicked());
        addRenderableWidget(loadButton);

        // Cancel button
        cancelButton = new PonderButton(guiLeft + WINDOW_W - btnW - 30, guiTop + WINDOW_H - 30, btnW, btnH);
        cancelButton.withCallback(this::onClose);
        addRenderableWidget(cancelButton);
    }

    private void scanResourcePacks() {
        availablePacks.clear();
        Path resourcepacksDir = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");

        if (!Files.exists(resourcepacksDir)) {
            return;
        }

        try (Stream<Path> paths = Files.list(resourcepacksDir)) {
            for (Path p : paths.filter(path -> path.toString().toLowerCase().endsWith(".zip")).toList()) {
                PonderPackInfo info = PonderPackInfo.fromZip(p);
                if (info != null) {
                    availablePacks.add(info);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void onLoadClicked() {
        if (selectedIndex < 0 || selectedIndex >= availablePacks.size()) {
            notifyUser(UIText.of("ponderer.ui.import.select"));
            return;
        }

        PonderPackInfo pack = availablePacks.get(selectedIndex);
        try {
            int count = SceneStore.loadPonderPackFromResourcePack(pack.sourcePath, true);
            notifyUser(UIText.of("ponderer.ui.import.success", count, pack.name));
            Minecraft.getInstance().execute(this::onClose);
        } catch (Exception e) {
            notifyUser(UIText.of("ponderer.ui.import.failed", e.getMessage()));
        }
    }

    private void notifyUser(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
        }
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Background
        new BoxElement()
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(WINDOW_W, WINDOW_H)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        // Header
        graphics.drawString(font, UIText.of("ponderer.ui.import"), guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WINDOW_W - 5, guiTop + 21, 0x60_FFFFFF);

        // List header
        graphics.drawString(font, UIText.of("ponderer.ui.import.available"), guiLeft + 10, guiTop + 30, 0xCCCCCC);

        // Pack list area background
        graphics.fill(guiLeft + 10, guiTop + 48, guiLeft + WINDOW_W - 10, guiTop + 48 + PACK_LIST_HEIGHT, 0x40_000000);

        // Render pack list
        int listY = guiTop + 50;
        int itemHeight = 22;
        int visibleCount = PACK_LIST_HEIGHT / itemHeight;

        for (int i = scrollOffset; i < Math.min(scrollOffset + visibleCount, availablePacks.size()); i++) {
            PonderPackInfo pack = availablePacks.get(i);
            int y = listY + (i - scrollOffset) * itemHeight;

            // Highlight selected
            if (i == selectedIndex) {
                graphics.fill(guiLeft + 11, y, guiLeft + WINDOW_W - 11, y + itemHeight - 2, 0x60_4080FF);
            }

            // Pack info
            String label = pack.name + " v" + pack.version;
            graphics.drawString(font, label, guiLeft + 15, y + 2, 0xFFFFFF);

            if (!pack.author.isEmpty()) {
                graphics.drawString(font, "by " + pack.author, guiLeft + 15, y + 12, 0xAAAAAA);
            }
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        graphics.drawCenteredString(font, UIText.of("ponderer.ui.load"),
                loadButton.getX() + 40, loadButton.getY() + 4, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
                cancelButton.getX() + 40, cancelButton.getY() + 4, 0xFFFFFF);

        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Check if scrolling within the pack list area
        int listStartY = guiTop + 50;
        int listEndY = guiTop + 50 + PACK_LIST_HEIGHT;

        if (mouseX >= guiLeft + 10 && mouseX <= guiLeft + WINDOW_W - 10 &&
                mouseY >= listStartY && mouseY <= listEndY) {
            int maxScroll = Math.max(0, availablePacks.size() - (PACK_LIST_HEIGHT / 22));
            int newOffset = (int) (scrollOffset - delta);
            scrollOffset = Math.max(0, Math.min(newOffset, maxScroll));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check if click is within pack list
            int listStartY = guiTop + 50;
            int listEndY = guiTop + 50 + PACK_LIST_HEIGHT;

            if (mouseX >= guiLeft + 10 && mouseX <= guiLeft + WINDOW_W - 10 &&
                    mouseY >= listStartY && mouseY <= listEndY) {

                int itemHeight = 22;
                int clickedIndex = (int) ((mouseY - listStartY) / itemHeight) + scrollOffset;
                if (clickedIndex >= 0 && clickedIndex < availablePacks.size()) {
                    selectedIndex = clickedIndex;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new FunctionScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
