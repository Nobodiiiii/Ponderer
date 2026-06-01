package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.platform.PondererServices;
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

public abstract class AbstractDeclarativeScreen extends ConfigScreen implements PondererUiScaling.ScaledScreen {

    protected final String scopeKey;
    protected final String titleKey;

    @Nullable
    private String errorMessage;
    @Nullable
    private String infoMessage;

    protected AbstractDeclarativeScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        super(parent);
        this.scopeKey = scopeKey;
        this.titleKey = titleKey;
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
        if (!shouldUseSafeFabricBackground()) {
            super.renderWindowBackground(graphics, mouseX, mouseY, partialTicks);
            return;
        }

        if (minecraft != null && minecraft.level != null) {
            graphics.fill(0, 0, width, height, 0xb0_282c34);
            return;
        }

        renderMenuBackground(graphics, partialTicks);
    }

    private boolean shouldUseSafeFabricBackground() {
        return "fabric".equals(PondererServices.PLATFORM.getPlatformName())
            && PondererServices.PLATFORM.isModLoaded("sodium")
            && !PondererServices.PLATFORM.isModLoaded("indium");
    }

    protected final void clearStatusMessages() {
        errorMessage = null;
        infoMessage = null;
    }

    protected final void setErrorMessage(@Nullable String message) {
        errorMessage = message;
        if (message != null && !message.isBlank()) {
            infoMessage = null;
        }
    }

    protected final void setInfoMessage(@Nullable String message) {
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

        // Wrap long messages across multiple lines instead of truncating to a single line.
        // The given y is the bottom anchor (just above the screen's bottom controls), so extra
        // lines stack upward into the empty space above rather than overlapping the controls.
        int lineHeight = minecraft.font.lineHeight;
        List<FormattedCharSequence> lines = minecraft.font.split(Component.literal(message), Math.max(1, maxWidth));
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
        int dirtyFields = getUnsavedChangeCount();
        new ConfirmationScreen()
            .centered()
            .withThreeActions(action)
            .addText(Component.translatable(
                "catnip.ui.leaving_with_changes_message",
                dirtyFields,
                Component.translatable(dirtyFields != 1
                    ? "catnip.ui.value_changes_plural"
                    : "catnip.ui.value_changes_singular")))
            .open(this);
    }

    @Override
    public void onClose() {
        attemptBackToParent();
    }

    protected abstract boolean hasUnsavedChanges();

    protected abstract int getUnsavedChangeCount();

    protected abstract boolean saveEdits();

    protected abstract void discardEdits();
}
