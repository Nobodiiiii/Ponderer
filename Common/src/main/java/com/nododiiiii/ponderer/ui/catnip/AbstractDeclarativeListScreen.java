package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractDeclarativeListScreen extends ConfigScreen {

    protected final String scopeKey;
    protected final String titleKey;

    @Nullable
    protected BoxWidget saveChanges;
    @Nullable
    protected BoxWidget discardChanges;
    @Nullable
    protected BoxWidget goBack;
    @Nullable
    protected ConfigTextField search;
    @Nullable
    protected ConfigScreenList list;

    @Nullable
    private String errorMessage;
    @Nullable
    private String infoMessage;

    private final int preferredListWidth;
    private String searchQuery = "";
    private int listWidth;

    protected AbstractDeclarativeListScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        this(parent, scopeKey, titleKey, 320);
    }

    protected AbstractDeclarativeListScreen(@Nullable Screen parent, String scopeKey, String titleKey, int preferredListWidth) {
        super(parent);
        this.scopeKey = scopeKey;
        this.titleKey = titleKey;
        this.preferredListWidth = preferredListWidth;
    }

    @Override
    protected void init() {
        super.init();

        listWidth = Math.min(width - 80, preferredListWidth);

        int yCenter = height / 2;
        int listLeft = width / 2 - listWidth / 2;

        saveChanges = new BoxWidget(listLeft - 30, yCenter - 25, 20, 20)
            .withPadding(2, 2)
            .withCallback(this::saveEdits);
        saveChanges.showingElement(PonderGuiTextures.ICON_CONFIG_SAVE.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(saveChanges)));
        saveChanges.getToolTip().add(Component.translatable("catnip.ui.save_changes_button"));
        saveChanges.getToolTip().addAll(FontHelper.cutTextComponent(
            Component.translatable("catnip.ui.save_changes_button_tooltip"),
            Palette.ALL_GRAY));
        addRenderableWidget(saveChanges);

        discardChanges = new BoxWidget(listLeft - 30, yCenter + 5, 20, 20)
            .withPadding(2, 2)
            .withCallback(this::confirmDiscardChanges);
        discardChanges.showingElement(PonderGuiTextures.ICON_CONFIG_DISCARD.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(discardChanges)));
        discardChanges.getToolTip().add(Component.translatable("catnip.ui.discard_changes_button"));
        discardChanges.getToolTip().addAll(FontHelper.cutTextComponent(
            Component.translatable("catnip.ui.discard_changes_button_tooltip"),
            Palette.ALL_GRAY));
        addRenderableWidget(discardChanges);

        goBack = new BoxWidget(listLeft - 30, yCenter + 65, 20, 20)
            .withPadding(2, 2)
            .withCallback(this::attemptBackToParent);
        goBack.showingElement(PonderGuiTextures.ICON_CONFIG_BACK.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(goBack)));
        goBack.getToolTip().add(Component.translatable("catnip.ui.go_back_button"));
        addRenderableWidget(goBack);

        list = new ConfigScreenList(minecraft, listWidth, height - 80, 35, height - 45, getEntryHeight());
        list.setLeftPos(width / 2 - list.getWidth() / 2);
        addRenderableWidget(list);

        search = new ConfigTextField(font, width / 2 - listWidth / 2, height - 35, listWidth, 20);
        search.setResponder(this::updateFilter);
        search.setHint(Component.translatable("catnip.ui.search_hint"));
        search.moveCursorToStart();
        addRenderableWidget(search);

        rebuildEntries(null);
        if (!searchQuery.isEmpty() && search != null) {
            search.setValue(searchQuery);
            updateFilter(searchQuery);
        }
        refreshActionButtons();
    }

    @Override
    public void tick() {
        super.tick();
        refreshActionButtons();
    }

    @Override
    public void resize(Minecraft client, int width, int height) {
        double scroll = currentListScroll();
        init(client, width, height);
        if (list != null) {
            list.setScrollAmount(scroll);
        }
        if (search != null && !searchQuery.isEmpty()) {
            search.setValue(searchQuery);
            updateFilter(searchQuery);
        }
    }

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        if (ConfigScreenList.currentText != null) {
            return ConfigScreenList.currentText;
        }
        return super.getFocused();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String breadcrumb = UIText.of("ponderer.ui.mod_name")
            + " > "
            + getBreadcrumbScopeText()
            + " > "
            + getBreadcrumbTitleText();
        graphics.drawCenteredString(
            minecraft.font,
            breadcrumb,
            width / 2,
            15,
            UIRenderHelper.COLOR_TEXT.getFirst().getRGB());

        String message = errorMessage != null && !errorMessage.isBlank() ? errorMessage : infoMessage;
        if (message == null || message.isBlank()) {
            return;
        }

        int color = errorMessage != null && !errorMessage.isBlank()
            ? AbstractSimiWidget.COLOR_FAIL.getFirst().getRGB()
            : AbstractSimiWidget.COLOR_SUCCESS.getFirst().getRGB();
        graphics.drawString(
            minecraft.font,
            minecraft.font.plainSubstrByWidth(message, listWidth),
            width / 2 - listWidth / 2,
            height - 48,
            color);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (search != null && hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
            search.setFocused(true);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            attemptBackToParent();
            return true;
        }

        return false;
    }

    @Override
    public void onClose() {
        attemptBackToParent();
    }

    protected final void rebuildEntries() {
        rebuildEntries(null);
    }

    protected final void rebuildEntries(@Nullable Double preservedScroll) {
        if (list == null) {
            return;
        }

        list.children().clear();
        collectEntries(list.children());

        if (preservedScroll != null) {
            list.setScrollAmount(preservedScroll);
        } else {
            list.setScrollAmount(0);
        }

        updateFilter(searchQuery);
    }

    protected final double currentListScroll() {
        return list != null ? list.getScrollAmount() : 0;
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

    protected final PlainTextListEntry textEntry(String labelKey, @Nullable String tooltipKey, @Nullable String hintKey,
                                                 String initialValue, java.util.function.Consumer<String> responder) {
        return new PlainTextListEntry(labelKey, tooltipKey, hintKey, initialValue, responder);
    }

    protected final LocalizedTextListEntry localizedTextEntry(String labelKey, @Nullable String tooltipKey,
                                                              @Nullable String hintKey, String initialValue,
                                                              java.util.function.Supplier<String> langGetter,
                                                              Runnable onToggle,
                                                              java.util.function.Consumer<String> responder) {
        return new LocalizedTextListEntry(labelKey, tooltipKey, hintKey, initialValue, langGetter, onToggle, responder);
    }

    protected int getEntryHeight() {
        return 40;
    }

    protected String getBreadcrumbScopeText() {
        return UIText.of(scopeKey);
    }

    protected String getBreadcrumbTitleText() {
        return UIText.of(titleKey);
    }

    protected final int currentListWidthValue() {
        return listWidth;
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

    protected abstract void collectEntries(List<ConfigScreenList.Entry> entries);

    protected abstract boolean hasUnsavedChanges();

    protected abstract int getUnsavedChangeCount();

    protected abstract boolean saveEdits();

    protected abstract void discardEdits();

    private void updateFilter(String query) {
        searchQuery = query == null ? "" : query;
        ListSearchHelper.applySearchFilter(list, search, searchQuery, getEntryHeight());
    }

    private void confirmDiscardChanges() {
        if (!hasUnsavedChanges()) {
            return;
        }

        int dirtyFields = getUnsavedChangeCount();
        new ConfirmationScreen()
            .centered()
            .withText(Component.translatable(
                "catnip.ui.discarding_changes_message",
                dirtyFields,
                Component.translatable(dirtyFields != 1
                    ? "catnip.ui.value_changes_plural"
                    : "catnip.ui.value_changes_singular")))
            .withAction(success -> {
                if (success) {
                    discardEdits();
                }
            })
            .open(this);
    }

    private void refreshActionButtons() {
        boolean dirty = hasUnsavedChanges();
        updateButtonState(saveChanges, dirty);
        updateButtonState(discardChanges, dirty);
    }

    private void updateButtonState(@Nullable BoxWidget button, boolean active) {
        if (button != null && button.active != active) {
            button.active = active;
            button.animateGradientFromState();
        }
    }
}
