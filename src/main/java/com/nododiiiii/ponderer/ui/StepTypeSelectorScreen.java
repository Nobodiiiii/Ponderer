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

/**
 * Popup screen for choosing which step type to add.
 */
public class StepTypeSelectorScreen extends AbstractSimiScreen {

    private static final int W = 180;
    private static final int ROW_H = 22;
    private static final String[][] PAGE_TYPES = {
        {
            "show_structure", "idle", "text", "shared_text", "rotate_camera_y", "show_controls", "play_sound", "encapsulate_bounds"
        },
        {
            "set_block", "destroy_block", "replace_blocks", "hide_section", "show_section_and_merge", "toggle_redstone_power", "modify_block_entity_nbt"
        },
        {
            "create_entity", "create_item_entity", "clear_entities", "clear_item_entities", "rotate_section", "move_section", "indicate_redstone", "indicate_success"
        }
    };
    private static final String[] PAGE_KEYS = {
        "ponderer.ui.step.page.story",
        "ponderer.ui.step.page.world",
        "ponderer.ui.step.page.effect"
    };

    private PonderButton backButton;
    private PonderButton prevPageButton;
    private PonderButton nextPageButton;

    private final DslScene scene;
    private final int sceneIndex;
    private final SceneEditorScreen parent;
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
        this.pageIndex = Math.max(0, Math.min(pageIndex, PAGE_TYPES.length - 1));
        this.insertAfterIndex = insertAfterIndex;
    }

    @Override
    protected void init() {
        String[] types = PAGE_TYPES[pageIndex];
        int h = 52 + types.length * ROW_H + 34;
        setWindowSize(W, h);
        super.init();

        prevPageButton = new PonderButton(guiLeft + 10, guiTop + 25, 16, 16);
        prevPageButton.withCallback(() -> Minecraft.getInstance().setScreen(new StepTypeSelectorScreen(scene, sceneIndex, parent, Math.max(0, pageIndex - 1), insertAfterIndex)));
        addRenderableWidget(prevPageButton);

        nextPageButton = new PonderButton(guiLeft + W - 26, guiTop + 25, 16, 16);
        nextPageButton.withCallback(() -> Minecraft.getInstance().setScreen(new StepTypeSelectorScreen(scene, sceneIndex, parent, Math.min(PAGE_TYPES.length - 1, pageIndex + 1), insertAfterIndex)));
        addRenderableWidget(nextPageButton);

        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            var btn = new PonderButton(guiLeft + 10, guiTop + 47 + i * ROW_H, W - 20, 18);
            btn.withCallback(() -> openEditorForType(type));
            addRenderableWidget(btn);
        }

        backButton = new PonderButton(guiLeft + W - 56, guiTop + h - 24, 46, 16);
        backButton.withCallback(() -> Minecraft.getInstance().setScreen(parent));
        addRenderableWidget(backButton);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String[] types = PAGE_TYPES[pageIndex];
        int h = 52 + types.length * ROW_H + 34;
        new BoxElement()
            .withBackground(new Color(0xdd_000000, true))
            .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(W, h)
            .render(graphics);

        var font = Minecraft.getInstance().font;
        graphics.drawString(font, UIText.of("ponderer.ui.step_selector.title"), guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + W - 5, guiTop + 21, 0x60_FFFFFF);
        graphics.drawCenteredString(font, UIText.of(PAGE_KEYS[pageIndex]), guiLeft + W / 2, guiTop + 30, 0xCCCCFF);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        String[] types = PAGE_TYPES[pageIndex];
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.drawCenteredString(font, "<", prevPageButton.getX() + 8, prevPageButton.getY() + 4, pageIndex > 0 ? 0xFFFFFF : 0x666666);
        graphics.drawCenteredString(font, ">", nextPageButton.getX() + 8, nextPageButton.getY() + 4, pageIndex < PAGE_TYPES.length - 1 ? 0xFFFFFF : 0x666666);
        for (int i = 0; i < types.length; i++) {
            graphics.drawCenteredString(font, UIText.of(stepTypeLabelKey(types[i])),
                guiLeft + W / 2, guiTop + 47 + i * ROW_H + 5, 0xDDDDDD);
        }
        if (backButton != null) {
            graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.back"),
                backButton.getX() + 23, backButton.getY() + 4, 0xFFFFFF);
        }
        graphics.pose().popPose();
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
