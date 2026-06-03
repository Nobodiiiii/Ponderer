package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.ConfirmationScreen;
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

public abstract class AbstractDeclarativeListScreen extends AbstractDeclarativeScreen {

    protected static final int DEFAULT_LIST_WIDTH = 300;
    protected static final int CONTENT_TOP = 35;
    protected static final int CONTENT_BOTTOM_MARGIN = 45;

    private static final int LIST_HEADER_HEIGHT = 3;
    private static final int LIST_VERTICAL_PADDING = 8;

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
    protected ConfigScreenList headerList;

    private final int preferredListWidth;
    private final List<ConfigScreenList.Entry> headerEntries = new java.util.ArrayList<>();
    private final ListScrollPreserver scrollPreserver = new ListScrollPreserver();
    private String searchQuery = "";
    private int listWidth;

    protected AbstractDeclarativeListScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        this(parent, scopeKey, titleKey, DEFAULT_LIST_WIDTH);
    }

    protected AbstractDeclarativeListScreen(@Nullable Screen parent, String scopeKey, String titleKey, int preferredListWidth) {
        super(parent, scopeKey, titleKey);
        this.preferredListWidth = preferredListWidth;
    }

    @Override
    protected void init() {
        super.init();
        headerList = null;

        listWidth = Math.min(width - 80, preferredListWidth);

        int yCenter = height / 2;
        int listLeft = width / 2 - listWidth / 2 + listHorizontalOffset();
        int actionLeft = listLeft + listWidth + 10;

        saveChanges = new ScreenTooltipBoxWidget(actionLeft, yCenter - 25, 20, 20)
            .withPadding(2, 2)
            .withCallback(this::saveEdits);
        saveChanges.showingElement(PonderGuiTextures.ICON_CONFIG_SAVE.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(saveChanges)));
        setActionButtonTooltip(saveChanges, "ponderer.ui.save", "ponderer.ui.sidebar.save.tooltip");
        addRenderableWidget(saveChanges);

        discardChanges = new ScreenTooltipBoxWidget(actionLeft, yCenter + 5, 20, 20)
            .withPadding(2, 2)
            .withCallback(this::confirmDiscardChanges);
        discardChanges.showingElement(PonderGuiTextures.ICON_CONFIG_DISCARD.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(discardChanges)));
        setActionButtonTooltip(discardChanges, "ponderer.ui.cancel", "ponderer.ui.sidebar.cancel.tooltip");
        addRenderableWidget(discardChanges);

        goBack = new ScreenTooltipBoxWidget(actionLeft, yCenter + 65, 20, 20)
            .withPadding(2, 2)
            .withCallback(this::attemptBackToParent);
        goBack.showingElement(PonderGuiTextures.ICON_CONFIG_BACK.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(goBack)));
        setActionButtonTooltip(goBack, "ponderer.ui.back", "ponderer.ui.sidebar.back.tooltip");
        addRenderableWidget(goBack);

        list = new ConfigScreenList(minecraft, listWidth, contentAreaHeight(), contentAreaTop(), getEntryHeight());
        list.setX(width / 2 - list.getWidth() / 2 + listHorizontalOffset());
        addRenderableWidget(list);

        search = new ClippedConfigTextField(font, width / 2 - listWidth / 2 + listHorizontalOffset(), height - 35, listWidth, 20);
        search.setResponder(this::updateFilter);
        search.setHint(Component.translatable("catnip.ui.search_hint"));
        search.moveCursorToStart(false);
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
        double scroll = scrollPreserver.capture(list);
        init(client, width, height);
        scrollPreserver.remember(scroll);
        scrollPreserver.restoreRemembered(list);
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
        renderBreadcrumb(graphics, width / 2, 15);
        renderStatusMessage(graphics, width / 2 - listWidth / 2 + listHorizontalOffset(), height - 48, listWidth);
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

    protected final void rebuildEntries() {
        rebuildEntries(null);
    }

    protected final void rebuildEntries(@Nullable Double preservedScroll) {
        if (list == null) {
            return;
        }

        rebuildHeaderEntries();
        list.children().clear();
        collectEntries(list.children());
        relayoutListViewport();

        if (preservedScroll != null) {
            list.setScrollAmount(preservedScroll);
        } else {
            list.setScrollAmount(0);
        }

        updateFilter(searchQuery);
        ListScrollPreserver.restoreAfterSearch(list, preservedScroll, searchQuery);
    }

    protected final double currentListScroll() {
        return ListScrollPreserver.current(list);
    }

    protected final void rememberCurrentListScroll() {
        scrollPreserver.capture(list);
    }

    protected final void rememberListScroll(double scroll) {
        scrollPreserver.remember(scroll);
    }

    protected final void restoreRememberedListScroll() {
        scrollPreserver.restoreRemembered(list);
    }

    protected final void rebuildListPreservingScroll() {
        rebuildEntries(scrollPreserver.capture(list));
    }

    protected final void rebuildListWithRememberedScroll() {
        rebuildEntries(scrollPreserver.remembered());
    }

    protected final void rebuildListAtTop() {
        scrollPreserver.remember(0);
        rebuildEntries(0.0);
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
        return com.nododiiiii.ponderer.ui.UILayoutConstants.LIST_ENTRY_H;
    }

    protected final int currentListWidthValue() {
        return listWidth;
    }

    /**
     * Horizontal offset added to the centered list position. Subclasses can override
     * to push the list (and its anchored widgets) sideways — e.g. to free space for
     * a side panel like a preview pane.
     */
    protected int listHorizontalOffset() {
        return 0;
    }

    protected final int contentAreaTop() {
        return CONTENT_TOP;
    }

    protected final int contentAreaHeight() {
        return Math.max(0, height - CONTENT_TOP - CONTENT_BOTTOM_MARGIN);
    }

    protected final int centeredContentTop(int contentHeight) {
        return contentAreaTop() + Math.max(0, (contentAreaHeight() - contentHeight) / 2);
    }

    protected final int bodyContentTop() {
        if (!hasHeaderEntries()) {
            return contentAreaTop();
        }
        return Math.min(contentAreaTop() + contentAreaHeight(), contentAreaTop() + currentHeaderHeight() + headerListGap());
    }

    protected final int bodyContentHeight() {
        return Math.max(0, contentAreaTop() + contentAreaHeight() - bodyContentTop());
    }

    protected final int centeredBodyContentTop(int contentHeight) {
        return bodyContentTop() + Math.max(0, (bodyContentHeight() - contentHeight) / 2);
    }

    protected int fixedVisibleRowCount() {
        return -1;
    }

    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
    }

    protected int headerListGap() {
        return 8;
    }

    protected boolean fillRemainingBodyWhenHeaderPresent() {
        return true;
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
        updateButtonState(saveChanges, isSaveButtonActive());
        updateButtonState(discardChanges, isDiscardButtonActive());
    }

    protected boolean isSaveButtonActive() {
        return hasUnsavedChanges();
    }

    protected boolean isDiscardButtonActive() {
        return hasUnsavedChanges();
    }

    protected final void setActionButtonTooltip(@Nullable BoxWidget button, String titleKey) {
        setActionButtonTooltip(button, titleKey, null);
    }

    protected final void setActionButtonTooltip(@Nullable BoxWidget button, String titleKey,
                                                @Nullable String detailKey) {
        if (button == null) {
            return;
        }

        button.getToolTip().clear();
        button.getToolTip().add(Component.literal(UIText.of(titleKey)));
        if (detailKey != null && !detailKey.isBlank()) {
            button.getToolTip().addAll(FontHelper.cutTextComponent(Component.literal(UIText.of(detailKey)), Palette.ALL_GRAY));
        }
    }

    private void relayoutListViewport() {
        if (list == null) {
            return;
        }

        int left = width / 2 - listWidth / 2 + listHorizontalOffset();
        if (hasHeaderEntries()) {
            int headerHeight = currentHeaderHeight();
            int headerTop = contentAreaTop();
            if (headerList != null) {
                headerList.updateSizeAndPosition(listWidth, headerHeight, headerTop);
                headerList.setX(left);
            }

            int bodyTop = bodyContentTop();
            int availableBodyHeight = bodyContentHeight();
            int bodyHeight = fillRemainingBodyWhenHeaderPresent()
                ? availableBodyHeight
                : desiredListViewportHeight(availableBodyHeight);
            list.updateSizeAndPosition(listWidth, bodyHeight, bodyTop);
            list.setX(left);
            return;
        }

        int viewportHeight = desiredListViewportHeight(contentAreaHeight());
        int top = centeredContentTop(viewportHeight);
        list.updateSizeAndPosition(listWidth, viewportHeight, top);
        list.setX(left);
    }

    private void rebuildHeaderEntries() {
        headerEntries.clear();
        collectHeaderEntries(headerEntries);

        if (headerEntries.isEmpty()) {
            if (headerList != null) {
                removeWidget(headerList);
                headerList = null;
            }
            return;
        }

        if (headerList == null) {
            headerList = new ConfigScreenList(
                minecraft,
                listWidth,
                currentHeaderHeight(),
                contentAreaTop(),
                getEntryHeight());
            headerList.setX(width / 2 - headerList.getWidth() / 2 + listHorizontalOffset());
            addRenderableWidget(headerList);
        }

        headerList.children().clear();
        headerList.children().addAll(headerEntries);
        headerList.setScrollAmount(0);
    }

    private int desiredListViewportHeight(int availableHeight) {
        if (list == null) {
            return availableHeight;
        }

        int fixedRows = fixedVisibleRowCount();
        if (fixedRows > 0) {
            return Math.min(availableHeight,
                fixedRows * getEntryHeight() + LIST_HEADER_HEIGHT + LIST_VERTICAL_PADDING);
        }

        int contentHeight = list.children().size() * getEntryHeight() + LIST_HEADER_HEIGHT + LIST_VERTICAL_PADDING;
        int minimumHeight = getEntryHeight() + LIST_HEADER_HEIGHT + LIST_VERTICAL_PADDING;
        return Math.min(availableHeight, Math.max(minimumHeight, contentHeight));
    }

    private boolean hasHeaderEntries() {
        return !headerEntries.isEmpty();
    }

    private int currentHeaderHeight() {
        if (!hasHeaderEntries()) {
            return 0;
        }
        return headerEntries.size() * getEntryHeight() + LIST_HEADER_HEIGHT + LIST_VERTICAL_PADDING;
    }

    protected final void updateButtonState(@Nullable BoxWidget button, boolean active) {
        if (button != null && button.active != active) {
            button.active = active;
            button.animateGradientFromState();
        }
    }
}
