package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.feedback.FeedbackTarget;
import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ui.PondererDialogScreen;
import com.nododiiiii.ponderer.ui.PondererUiScaling;
import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractDeclarativeScreen extends ConfigScreen implements PondererUiScaling.ScaledScreen, FeedbackTarget {

    protected final String scopeKey;
    protected final String titleKey;

    @Nullable
    private String errorMessage;
    @Nullable
    private String infoMessage;
    @Nullable private Component feedbackMessage;
    private FeedbackLevel feedbackLevel;

    protected AbstractDeclarativeScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        super(parent);
        this.scopeKey = scopeKey;
        this.titleKey = titleKey;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return mouseScrolled(x, y, 0, delta);
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(x, y, vertical);
    }

    @Override
    protected void init() {
        PondererUiScaling.apply(minecraft, this);
        super.init();
    }

    @Override
    public void removed() {
        Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
        super.removed();
        PondererUiScaling.scheduleRestore(mc);
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (minecraft != null && minecraft.level != null) {
            graphics.fill(0, 0, width, height, 0xb0282c34);
        } else {
            renderMenuBackground(graphics, partialTicks);
        }

        if (!shouldUseSafeFabricBackground()) {
            shadowElement.at(backgroundCogCenterX(), height * 0.5f, 0).render(graphics);
        }
    }

    protected float backgroundCogCenterX() {
        return width * 0.5f;
    }

    @Override
    public final void acceptFeedback(Component message, FeedbackLevel level) {
        if (level == FeedbackLevel.ERROR) setErrorMessage(message.getString());
        else setInfoMessage(message.getString());
        feedbackMessage = message;
        feedbackLevel = level;
    }

    protected final void clearStatusMessages() {
        feedbackMessage = null;
        errorMessage = null;
        infoMessage = null;
    }

    protected final void setErrorMessage(@Nullable String message) {
        feedbackMessage = null;
        errorMessage = message;
        if (message != null && !message.isBlank()) {
            infoMessage = null;
        }
    }

    protected final void setInfoMessage(@Nullable String message) {
        feedbackMessage = null;
        infoMessage = message;
        if (message != null && !message.isBlank()) {
            errorMessage = null;
        }
    }

    protected final void renderBreadcrumb(GuiGraphics graphics, int centerX, int y) {
        String breadcrumb = UIText.of("ponderer.ui.mod_name")
            + " > "
            + getBreadcrumbScopeText()
            + " > "
            + getBreadcrumbTitleText();
        graphics.drawCenteredString(
            minecraft.font,
            breadcrumb,
            centerX,
            y,
            UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
    }

    protected final void renderStatusMessage(GuiGraphics graphics, int x, int y, int maxWidth) {
        String message = errorMessage != null && !errorMessage.isBlank() ? errorMessage : infoMessage;
        if (message == null || message.isBlank()) {
            return;
        }

        int color = errorMessage != null && !errorMessage.isBlank()
            ? AbstractSimiWidget.COLOR_FAIL.getFirst().getRGB()
            : AbstractSimiWidget.COLOR_SUCCESS.getFirst().getRGB();
        if (feedbackMessage != null) color = switch (feedbackLevel) {
            case INFO -> 0xD0D0D0;
            case SUCCESS -> AbstractSimiWidget.COLOR_SUCCESS.getFirst().getRGB();
            case WARNING -> 0xFFCC55;
            case ERROR -> AbstractSimiWidget.COLOR_FAIL.getFirst().getRGB();
        };
        int lineHeight = minecraft.font.lineHeight;
        List<FormattedCharSequence> lines = minecraft.font.split(feedbackMessage != null ? feedbackMessage : Component.literal(message), Math.max(1, maxWidth));
        int topY = y - Math.max(0, lines.size() - 1) * lineHeight;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(minecraft.font, lines.get(i), x, topY + i * lineHeight, color);
        }
    }

    protected String getBreadcrumbScopeText() {
        return UIText.of(scopeKey);
    }

    protected String getBreadcrumbTitleText() {
        return UIText.of(titleKey);
    }

    protected void attemptBackToParent() {
        if (!hasUnsavedChanges()) {
            ScreenOpener.open(parent);
            return;
        }

        showLeavingPrompt(response -> {
            if (response == ConfirmationScreen.Response.Cancel) {
                return;
            }
            if (response == ConfirmationScreen.Response.Confirm) {
                if (!saveEdits()) {
                    return;
                }
            } else {
                discardEdits();
            }
            ScreenOpener.open(parent);
        });
    }

    protected void showLeavingPrompt(Consumer<ConfirmationScreen.Response> action) {
        showLeavingPrompt(action, "ponderer.ui.unsaved_changes.message",
            "ponderer.ui.unsaved_changes.save_and_back", "ponderer.ui.unsaved_changes.discard_and_back");
    }

    protected void showLeavingPrompt(Consumer<ConfirmationScreen.Response> action, String messageKey,
                                     String saveLabelKey, String discardLabelKey) {
        new PondererDialogScreen(
            this,
            List.of(Component.translatable("ponderer.ui.unsaved_changes.title")),
            List.of(Component.translatable(messageKey, getUnsavedChangeCount())),
            List.of(
                promptButton(saveLabelKey,
                    () -> action.accept(ConfirmationScreen.Response.Confirm)),
                promptButton(discardLabelKey,
                    () -> action.accept(ConfirmationScreen.Response.ConfirmDontSave)),
                promptButton("ponderer.ui.unsaved_changes.keep_editing",
                    () -> action.accept(ConfirmationScreen.Response.Cancel)))) {
            @Override
            public void onClose() {
                super.onClose();
                action.accept(ConfirmationScreen.Response.Cancel);
            }
        }.open();
    }

    protected final PondererDialogScreen.DialogButton promptButton(String labelKey, Runnable action) {
        return PondererDialogScreen.button(Component.translatable(labelKey), dialog -> {
            dialog.closeToSource();
            action.run();
        });
    }

    @Override
    public void onClose() {
        attemptBackToParent();
    }

    protected abstract boolean hasUnsavedChanges();

    protected abstract int getUnsavedChangeCount();

    protected abstract boolean saveEdits();

    protected abstract void discardEdits();

    private boolean shouldUseSafeFabricBackground() {
        return "fabric".equals(PondererServices.PLATFORM.getPlatformName())
            && PondererServices.PLATFORM.isModLoaded("sodium")
            && !PondererServices.PLATFORM.isModLoaded("indium");
    }
}
