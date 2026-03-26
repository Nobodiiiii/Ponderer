package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Popup screen for choosing which step type to add.
 */
public class StepTypeSelectorScreen extends AbstractSimiScreen {

    private static final int W = 180;
    private static final int ROW_H = 22;
    private static final String[][] PAGE_TYPES = {
        {
            "idle", "text", "show_controls", "rotate_camera_y", "zoom_scene"
        },
        {
            "set_block", "destroy_block", "replace_blocks", "modify_block_entity_nbt"
        },
        {
            "show_section_and_merge", "hide_section", "rotate_section", "move_section"
        },
        {
            "create_entity", "create_item_entity", "clear_entities", "clear_item_entities", "modify_entities_nbt", "modify_item_entities_nbt"
        },
        {
            "highlight_section", "indicate_success", "indicate_redstone", "toggle_redstone_power", "play_sound"
        }
    };
    private static final String[] PAGE_KEYS = {
        "ponderer.ui.step.page.story",
        "ponderer.ui.step.page.block",
        "ponderer.ui.step.page.section",
        "ponderer.ui.step.page.entity",
        "ponderer.ui.step.page.effect"
    };
    private static final String[][] INTERFACE_SCENE_PAGE_TYPES = {
        {
            "idle", "text", "show_controls", "play_sound"
        }
    };
    private static final String[] INTERFACE_SCENE_PAGE_KEYS = {
        "ponderer.ui.step.page.story"
    };

    private PonderButton backButton;
    private PonderButton prevPageButton;
    private PonderButton nextPageButton;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int displayH;
    private record TypeButton(int contentY, String type) {}
    private final List<TypeButton> typeButtons = new ArrayList<>();

    private final DslScene scene;
    private final int sceneIndex;
    private final SceneEditorScreen parent;
    private final String[][] pageTypes;
    private final String[] pageKeys;
    private final int pageIndex;
    /** Index after which to insert the new step. -1 means append. */
    private final int insertAfterIndex;

    public StepTypeSelectorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        this(scene, sceneIndex, parent, 0, -1);
    }

    public StepTypeSelectorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent, int pageIndex) {
        this(scene, sceneIndex, parent, pageIndex, -1);
    }

    public StepTypeSelectorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent, int pageIndex, int insertAfterIndex) {
        super(Component.translatable("ponderer.ui.step_selector"));
        this.scene = scene;
        this.sceneIndex = sceneIndex;
        this.parent = parent;
        if (isInterfaceStartScene(scene, sceneIndex)) {
            this.pageTypes = INTERFACE_SCENE_PAGE_TYPES;
            this.pageKeys = INTERFACE_SCENE_PAGE_KEYS;
        } else {
            this.pageTypes = PAGE_TYPES;
            this.pageKeys = PAGE_KEYS;
        }
        this.pageIndex = Math.max(0, Math.min(pageIndex, this.pageTypes.length - 1));
        this.insertAfterIndex = insertAfterIndex;
    }

    @Override
    protected void init() {
        typeButtons.clear();
        String[] types = pageTypes[pageIndex];
        int fixedRows = 6;
        int fullH = 52 + fixedRows * ROW_H + 34;
        displayH = Math.min(fullH, height - UILayoutConstants.SCREEN_MARGIN * 2);
        maxScroll = Math.max(0, fullH - displayH);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        setWindowSize(W, displayH);
        super.init();

        prevPageButton = new PonderButton(guiLeft + 10, guiTop + 25, 16, 16);
        prevPageButton.withCallback(() -> Minecraft.getInstance().setScreen(new StepTypeSelectorScreen(scene, sceneIndex, parent, Math.max(0, pageIndex - 1), insertAfterIndex)));
        addRenderableWidget(prevPageButton);

        nextPageButton = new PonderButton(guiLeft + W - 26, guiTop + 25, 16, 16);
        nextPageButton.withCallback(() -> Minecraft.getInstance().setScreen(new StepTypeSelectorScreen(scene, sceneIndex, parent, Math.min(pageTypes.length - 1, pageIndex + 1), insertAfterIndex)));
        addRenderableWidget(nextPageButton);

        for (int i = 0; i < types.length; i++) {
            typeButtons.add(new TypeButton(47 + i * ROW_H, types[i]));
        }

        backButton = new PonderButton(guiLeft + W - 56, guiTop + displayH - 24, 46, 16);
        backButton.withCallback(() -> Minecraft.getInstance().setScreen(parent));
        addRenderableWidget(backButton);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        new BoxElement()
            .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
            .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true), new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(W, displayH)
            .render(graphics);

        var font = Minecraft.getInstance().font;
        graphics.drawString(font, UIText.of("ponderer.ui.step_selector.title"), guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + W - 5, guiTop + 21, UILayoutConstants.COLOR_SEPARATOR);
        graphics.drawCenteredString(font, UIText.of(pageKeys[pageIndex]), guiLeft + W / 2, guiTop + 30, 0xCCCCFF);

        // Scrollable type buttons
        int vpTop = guiTop + 44;
        int vpBot = guiTop + displayH - 26;
        if (maxScroll > 0) {
            graphics.enableScissor(guiLeft, vpTop, guiLeft + W, vpBot);
            graphics.pose().pushPose();
            graphics.pose().translate(0, -scrollOffset, 0);
        }
        int adjustedMouseY = mouseY + scrollOffset;
        boolean inVP = maxScroll <= 0 || (mouseY >= vpTop && mouseY < vpBot);
        for (TypeButton tb : typeButtons) {
            int by = guiTop + tb.contentY;
            int bx = guiLeft + 10;
            int bw = W - 20;
            boolean hovered = inVP && mouseX >= bx && mouseX < bx + bw
                && adjustedMouseY >= by && adjustedMouseY < by + 18;
            int bgColor = hovered ? 0x60_4466aa : 0x40_333366;
            int borderColor = hovered ? 0xCC_6688cc : 0x60_555588;
            graphics.fill(bx, by, bx + bw, by + 18, bgColor);
            graphics.fill(bx, by, bx + bw, by + 1, borderColor);
            graphics.fill(bx, by + 18 - 1, bx + bw, by + 18, borderColor);
            graphics.fill(bx, by, bx + 1, by + 18, borderColor);
            graphics.fill(bx + bw - 1, by, bx + bw, by + 18, borderColor);
            graphics.drawCenteredString(font, UIText.of(stepTypeLabelKey(tb.type)),
                guiLeft + W / 2, by + 5, hovered ? 0xFFFFFF : 0xDDDDDD);
        }
        if (maxScroll > 0) {
            graphics.pose().popPose();
            graphics.disableScissor();
            renderScrollbar(graphics);
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.drawCenteredString(font, "<", prevPageButton.getX() + 8, prevPageButton.getY() + 4, pageIndex > 0 ? 0xFFFFFF : 0x666666);
        graphics.drawCenteredString(font, ">", nextPageButton.getX() + 8, nextPageButton.getY() + 4, pageIndex < pageTypes.length - 1 ? 0xFFFFFF : 0x666666);
        if (backButton != null) {
            graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.back"),
                backButton.getX() + 23, backButton.getY() + 4, 0xFFFFFF);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double adjY = mouseY + scrollOffset;
            for (TypeButton tb : typeButtons) {
                int by = guiTop + tb.contentY;
                int bx = guiLeft + 10;
                int bw = W - 20;
                if (mouseX >= bx && mouseX < bx + bw && adjY >= by && adjY < by + 18) {
                    openEditorForType(tb.type);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta * UILayoutConstants.SCROLL_SPEED));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) return;
        int barX = guiLeft + W - UILayoutConstants.SCROLLBAR_W - 2;
        int vpTop = guiTop + 44;
        int vpBot = guiTop + displayH - 26;
        int trackH = vpBot - vpTop;
        graphics.fill(barX, vpTop, barX + UILayoutConstants.SCROLLBAR_W, vpBot, UILayoutConstants.COLOR_SCROLLBAR_BG);
        String[] types = pageTypes[pageIndex];
        int contentH = types.length * ROW_H;
        if (contentH <= 0) return;
        int thumbH = Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB, trackH * trackH / contentH);
        int thumbY = vpTop + (int) ((float) scrollOffset / maxScroll * (trackH - thumbH));
        graphics.fill(barX, thumbY, barX + UILayoutConstants.SCROLLBAR_W, thumbY + thumbH, UILayoutConstants.COLOR_SCROLLBAR_FG);
    }

    private static boolean isInterfaceStartScene(DslScene scene, int sceneIndex) {
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
            return false;
        }
        if (sceneIndex < 0 || sceneIndex >= scene.scenes.size()) {
            return false;
        }
        List<DslScene.DslStep> steps = scene.scenes.get(sceneIndex).steps;
        if (steps == null) {
            return false;
        }
        for (DslScene.DslStep step : steps) {
            if (step == null || step.type == null || step.type.isBlank()) {
                continue;
            }
            return "show_interface".equals(step.type.toLowerCase(Locale.ROOT));
        }
        return false;
    }

    private void openEditorForType(String type) {
        AbstractStepEditorScreen editor = StepEditorFactory.createAddScreen(type, scene, sceneIndex, parent);
        if (editor != null) {
            editor.setReturnScreen(this);
            editor.setInsertAfterIndex(insertAfterIndex);
            ScreenOpener.open(editor);
        }
    }

    private String stepTypeLabelKey(String type) {
        return "ponderer.ui.step.type." + type;
    }

    @Override
    public void onClose() {
        // Return to parent SceneEditorScreen instead of closing to game
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
