package com.nododiiiii.ponderer.editor.screen.widgets;

import com.nododiiiii.ponderer.editor.screen.UIText;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A Ponder category with its native texture/item icon and a multi-selection control. */
public class PonderTagListEntry extends SelectionRowListEntry {
    private final String id;
    @Nullable
    private final PonderTag tag;

    public PonderTagListEntry(String id, @Nullable PonderTag tag, BooleanSupplier selected, Runnable onToggle) {
        this(id, tag, selected, onToggle, null, null);
    }

    public PonderTagListEntry(String id, @Nullable PonderTag tag, BooleanSupplier selected, Runnable onToggle,
                              @Nullable Runnable onEdit, @Nullable Runnable onDelete) {
        super(() -> title(tag), () -> id,
            () -> title(tag) + " " + id + " " + (tag == null ? "" : tag.getDescription()),
            selected, onToggle, () -> tooltip(id, tag), () -> ItemStack.EMPTY,
            editButtons(onEdit, onDelete));
        this.id = id;
        this.tag = tag;
    }

    private static List<ActionStripListEntry.ButtonModel> editButtons(@Nullable Runnable onEdit, @Nullable Runnable onDelete) {
        List<ActionStripListEntry.ButtonModel> buttons = new ArrayList<>();
        if (onEdit != null) {
            buttons.add(ActionStripListEntry.rowIconButton(PondererGuiIcons.EDIT_SCENE.asStencil(12), onEdit,
                () -> List.of(Component.translatable("ponderer.ui.custom_tag.edit")), () -> true, false));
        }
        if (onDelete != null) {
            buttons.add(ActionStripListEntry.rowIconButton(PondererGuiIcons.REMOVE.asStencil(12), onDelete,
                () -> List.of(Component.translatable("ponderer.ui.custom_tag.delete")), () -> true, true));
        }
        return buttons;
    }

    @Override
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        if (tag != null) {
            tag.render(graphics, x, y);
        } else {
            super.renderIcon(graphics, x, y);
        }
    }

    @Override
    public Component getNarration() {
        return Component.literal(title(tag) + ", " + id);
    }

    private static String title(@Nullable PonderTag tag) {
        return tag == null ? UIText.of("ponderer.ui.scene_tags.unavailable") : tag.getTitle();
    }

    private static List<Component> tooltip(String id, @Nullable PonderTag tag) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(title(tag)));
        lines.add(Component.literal(id));
        lines.addAll(FontHelper.cutTextComponent(Component.literal(tag == null
            ? UIText.of("ponderer.ui.scene_tags.unavailable.tooltip") : tag.getDescription()), Palette.ALL_GRAY));
        return lines;
    }
}
