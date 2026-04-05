package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

public abstract class AbstractJeiAwareFormScreen extends AbstractStatefulDeclarativeFormScreen
    implements JeiTextButtonHost {

    private final JeiFieldController jeiController;

    protected AbstractJeiAwareFormScreen(@Nullable Screen parent, String scopeKey, String titleKey,
                                         int preferredListWidth,
                                         BiConsumer<JeiAwareScreen, IdFieldMode> activationAction) {
        super(parent, scopeKey, titleKey, preferredListWidth);
        this.jeiController = new JeiFieldController(activationAction);
    }

    protected AbstractJeiAwareFormScreen(@Nullable Screen parent, String scopeKey, String titleKey,
                                         BiConsumer<JeiAwareScreen, IdFieldMode> activationAction) {
        super(parent, scopeKey, titleKey);
        this.jeiController = new JeiFieldController(activationAction);
    }

    @Nullable
    @Override
    public HintableTextFieldWidget getJeiTargetField() {
        return jeiController.targetField();
    }

    @Override
    public final void toggleJeiForField(HintableTextFieldWidget field, IdFieldMode mode) {
        jeiController.toggle(this, field, mode);
        onJeiStateChanged();
    }

    @Override
    public final boolean isJeiActiveForField(HintableTextFieldWidget field) {
        return jeiController.isActiveFor(field);
    }

    @Override
    public void deactivateJei() {
        if (!jeiController.isActive()) {
            return;
        }
        jeiController.deactivate();
        onJeiStateChanged();
    }

    @Override
    public void showJeiIncompatibleWarning(IdFieldMode mode) {
        setErrorMessage(switch (mode) {
            case BLOCK -> UIText.of("ponderer.ui.jei.error.not_block");
            case ENTITY -> UIText.of("ponderer.ui.jei.error.not_spawn_egg");
            case ITEM, INGREDIENT -> null;
        });
    }

    @Override
    public int getGuiLeft() {
        return width / 2 - currentListWidthValue() / 2 - 40;
    }

    @Override
    public int getGuiTop() {
        return 35;
    }

    @Override
    public int getGuiWidth() {
        return currentListWidthValue() + 80;
    }

    @Override
    public int getGuiHeight() {
        return height - 60;
    }

    @Override
    public void removed() {
        super.removed();
        if (jeiController.isActive()) {
            deactivateJei();
        }
    }

    protected boolean rebuildOnJeiStateChange() {
        return false;
    }

    protected void onJeiStateChanged() {
        if (rebuildOnJeiStateChange()) {
            rebuildEntries(currentListScroll());
        }
    }
}
