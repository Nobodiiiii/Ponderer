package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.ButtonPairListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.List;

public class PondererConfigScreen extends AbstractReadonlyDeclarativeListScreen {

    private record ButtonDef(String labelKey, Runnable action, String tooltipKey) {
    }

    private record Section(String titleKey, List<ButtonDef> buttons) {
    }

    private final List<Section> sections;
    @Nullable
    private final PonderScreenNavigation.ReturnState ponderReturnState;

    public PondererConfigScreen(Screen parent) {
        this(parent, null);
    }

    public PondererConfigScreen(Screen parent, @Nullable PonderScreenNavigation.ReturnState ponderReturnState) {
        super(parent, "ponderer.ui.scope.config", "ponderer.ui.mod_config.title", UILayoutConstants.EDITOR_LIST_W);
        this.ponderReturnState = ponderReturnState;
        this.sections = List.of(
            new Section("ponderer.ui.mod_config.section.categories", List.of(
                new ButtonDef("ponderer.ui.mod_config.category.general",
                    () -> Minecraft.getInstance().setScreen(new PondererGeneralConfigScreen(this)),
                    "ponderer.ui.mod_config.category.general.tooltip"),
                new ButtonDef("ponderer.ui.function_page.ai_config",
                    () -> Minecraft.getInstance().setScreen(new AiConfigScreen(this)),
                    "ponderer.ui.function_page.ai_config.tooltip"),
                new ButtonDef("ponderer.ui.mod_config.category.blueprint",
                    () -> Minecraft.getInstance().setScreen(new BlueprintItemConfigScreen(this)),
                    "ponderer.ui.mod_config.category.blueprint.tooltip"),
                new ButtonDef("ponderer.ui.mod_config.category.projector",
                    () -> Minecraft.getInstance().setScreen(new ProjectorFeatureConfigScreen(this)),
                    "ponderer.ui.mod_config.category.projector.tooltip")
            )),
            new Section("ponderer.ui.mod_config.section.controls", List.of(
                new ButtonDef("ponderer.ui.function_page.keybindings",
                    () -> Minecraft.getInstance().setScreen(new PondererKeyBindingsScreen(this)),
                    "ponderer.ui.function_page.keybindings.tooltip")
            ))
        );
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
    protected void attemptBackToParent() {
        if (ponderReturnState == null) {
            super.attemptBackToParent();
            return;
        }

        PonderScreenNavigation.restoreReturnState(ponderReturnState);
        Minecraft.getInstance().setScreen(parent);
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
}
