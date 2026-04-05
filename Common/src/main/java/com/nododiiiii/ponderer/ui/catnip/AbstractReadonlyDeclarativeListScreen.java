package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UILayoutConstants;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

public abstract class AbstractReadonlyDeclarativeListScreen extends AbstractDeclarativeListScreen {

    protected AbstractReadonlyDeclarativeListScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        super(parent, scopeKey, titleKey);
    }

    protected AbstractReadonlyDeclarativeListScreen(@Nullable Screen parent, String scopeKey, String titleKey,
                                                    int preferredListWidth) {
        super(parent, scopeKey, titleKey, preferredListWidth);
    }

    @Override
    protected void init() {
        super.init();
        hideActionButton(saveChanges);
        hideActionButton(discardChanges);
    }

    @Override
    protected final boolean hasUnsavedChanges() {
        return false;
    }

    @Override
    protected final int getUnsavedChangeCount() {
        return 0;
    }

    @Override
    protected final boolean saveEdits() {
        return false;
    }

    @Override
    protected final void discardEdits() {
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }

    private static void hideActionButton(@Nullable BoxWidget button) {
        if (button == null) {
            return;
        }
        button.visible = false;
        button.active = false;
    }
}
