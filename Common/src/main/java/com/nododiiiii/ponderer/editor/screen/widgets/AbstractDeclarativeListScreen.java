package com.nododiiiii.ponderer.editor.screen.widgets;

import com.nododiiiii.ponderer.editor.screen.PondererDialogScreen;
import com.nododiiiii.ponderer.editor.screen.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
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
    @Nullable
    private ConfigScreenList focusedList;

    private final int preferredListWidth;
    private final List<ConfigScreenList.Entry> headerEntries = new java.util.ArrayList<>();
    private final ListScrollPreserver scrollPreserver = new ListScrollPreserver();
    private final NumericFieldDrag numericFieldDrag = new NumericFieldDrag();
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
        numericFieldDrag.cancel();
        super.init();
        headerList = null;

        listWidth = Math.min(availableListWidth(), preferredListWidth);

        int yCenter = height / 2;
        int listLeft = width / 2 - listWidth / 2 + listHorizontalOffset();
        int actionLeft = listLeft + listWidth + 6;

        saveChanges = new ScreenTooltipBoxWidget(actionLeft, yCenter - 29, 28, 28)
            .withPadding(6, 6)
            .withCallback(this::saveEdits);
        saveChanges.showingElement(PonderGuiTextures.ICON_CONFIG_SAVE.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(saveChanges)));
        setActionButtonTooltip(saveChanges, "ponderer.ui.save");
        addRenderableWidget(saveChanges);

        discardChanges = new ScreenTooltipBoxWidget(actionLeft, yCenter + 1, 28, 28)
            .withPadding(6, 6)
            .withCallback(this::confirmDiscardChanges);
        discardChanges.showingElement(PonderGuiTextures.ICON_CONFIG_DISCARD.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(discardChanges)));
        setActionButtonTooltip(discardChanges, "ponderer.ui.cancel");
        addRenderableWidget(discardChanges);

        goBack = new ScreenTooltipBoxWidget(actionLeft, yCenter + 61, 28, 28)
            .withPadding(6, 6)
            .withCallback(this::attemptBackToParent);
        goBack.showingElement(PonderGuiTextures.ICON_CONFIG_BACK.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(goBack)));
        setActionButtonTooltip(goBack, "ponderer.ui.back", "ponderer.ui.sidebar.back.tooltip");
        addRenderableWidget(goBack);

        list = createCenteredList(contentAreaHeight(), contentAreaTop(), getEntryHeight());
        list.setX(width / 2 - list.getWidth() / 2 + listHorizontalOffset());
        addRenderableWidget(list);

        search = new ClippedConfigTextField(font, width / 2 - listWidth / 2 + listHorizontalOffset(), height - 35, listWidth, 20);
        EntryTextSupport.setInputBounds(search, search.getX(), height - 36, listWidth, EntryTextSupport.inputOuterHeight());
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Navigatable screens may render a previous screen behind the current one.
        boolean currentScreen = minecraft != null && minecraft.screen == this;
        if (currentScreen) numericFieldDrag.beginFrame();
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (currentScreen) numericFieldDrag.finishFrame(mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return numericFieldDrag.drag(mouseX, mouseY, button, deltaX, deltaY)
            || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (numericFieldDrag.release(mouseX, mouseY, button)) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        numericFieldDrag.close();
        super.removed();
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
        // Catnip clears list focus when no row is focused, including scrollbar clicks.
        // Preserve the clicked list for dragging while keeping text input routing.
        if (focusedList != null && (isDragging() || ConfigScreenList.currentText == null)) {
            return focusedList;
        }
        if (ConfigScreenList.currentText != null) {
            return ConfigScreenList.currentText;
        }
        return super.getFocused();
    }

    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        super.setFocused(listener);
        focusedList = listener instanceof ConfigScreenList configList ? configList : null;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBreadcrumb(graphics, breadcrumbCenterX(), 15);
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

        return false;
    }

    protected final void rebuildEntries() {
        rebuildEntries(null);
    }

    protected final void rebuildEntries(@Nullable Double preservedScroll) {
        if (list == null) {
            return;
        }

        numericFieldDrag.cancel();
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
        return com.nododiiiii.ponderer.editor.screen.UILayoutConstants.LIST_ENTRY_H;
    }

    protected int getHeaderEntryHeight() {
        return getEntryHeight();
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

    @Override
    protected float backgroundCogCenterX() {
        return super.backgroundCogCenterX() + listHorizontalOffset();
    }

    protected int breadcrumbCenterX() {
        return width / 2;
    }

    protected final List<Rect2i> occupiedWidgetAreas() {
        List<Rect2i> areas = new java.util.ArrayList<>();
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                areas.add(new Rect2i(widget.getX() - 4, widget.getY() - 4,
                    widget.getWidth() + 8, widget.getHeight() + 8));
            }
        }
        String breadcrumb = UIText.of("ponderer.ui.mod_name") + " > " + getBreadcrumbScopeText()
            + " > " + getBreadcrumbTitleText();
        int titleWidth = font.width(breadcrumb);
        areas.add(new Rect2i(breadcrumbCenterX() - titleWidth / 2 - 2, 13, titleWidth + 4, font.lineHeight + 4));
        return areas;
    }

    protected int availableListWidth() {
        return width - 80;
    }

    protected int listBottomPadding(int viewportHeight) {
        return 0;
    }

    protected void onListScrollChanged(int firstVisibleIndex) {
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

        new PondererDialogScreen(
            this,
            List.of(Component.translatable("ponderer.ui.discard_changes.title")),
            List.of(Component.translatable("ponderer.ui.discard_changes.message", getUnsavedChangeCount())),
            List.of(
                promptButton("ponderer.ui.discard_changes.confirm", this::discardEdits),
                PondererDialogScreen.closeButton(Component.translatable("ponderer.ui.unsaved_changes.keep_editing"))))
            .open();
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

    protected final void useConfirmationActionIcons() {
        if (saveChanges != null) {
            saveChanges.showingElement(PonderGuiTextures.ICON_CONFIRM.asStencil()
                .withElementRenderer(BoxWidget.gradientFactory.apply(saveChanges)));
            setActionButtonTooltip(saveChanges, "ponderer.ui.confirm");
        }
        if (discardChanges != null) {
            discardChanges.showingElement(PonderGuiTextures.ICON_DISABLE.asStencil()
                .withElementRenderer(BoxWidget.gradientFactory.apply(discardChanges)));
            setActionButtonTooltip(discardChanges, "ponderer.ui.cancel");
        }
    }

    protected final void setActionButtonTooltip(@Nullable BoxWidget button, String titleKey) {
        if (button == null) {
            return;
        }

        button.getToolTip().clear();
        button.getToolTip().add(Component.literal(UIText.of(titleKey)));
    }

    protected final void setActionButtonTooltip(@Nullable BoxWidget button, String titleKey,
                                                String detailKey) {
        if (button == null) {
            return;
        }

        setActionButtonTooltip(button, titleKey);
        button.getToolTip().addAll(FontHelper.cutTextComponent(Component.literal(UIText.of(detailKey)), Palette.ALL_GRAY));
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
            headerList = createCenteredList(currentHeaderHeight(), contentAreaTop(), getHeaderEntryHeight());
            headerList.setX(width / 2 - headerList.getWidth() / 2 + listHorizontalOffset());
            addRenderableWidget(headerList);
        }

        headerList.children().clear();
        headerList.children().addAll(headerEntries);
        headerList.setScrollAmount(0);
    }

    private ConfigScreenList createCenteredList(int height, int top, int entryHeight) {
        return new ConfigScreenList(minecraft, listWidth, height, top, entryHeight) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                super.renderWidget(graphics, mouseX, mouseY, partialTicks);
                if (isMouseOver(mouseX, mouseY)) {
                    numericFieldDrag.hover(NumericFieldDrag.fieldAt(getEntryAtPosition(mouseX, mouseY), mouseX, mouseY));
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 1 && isMouseOver(mouseX, mouseY)) {
                    ConfigScreenList.Entry entry = getEntryAtPosition(mouseX, mouseY);
                    if (entry instanceof SceneStepListEntry && entry.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
                boolean handled = super.mouseClicked(mouseX, mouseY, button);
                if (handled && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isMouseOver(mouseX, mouseY)) {
                    numericFieldDrag.press(NumericFieldDrag.fieldAt(getEntryAtPosition(mouseX, mouseY), mouseX, mouseY),
                        mouseX, mouseY);
                }
                return handled;
            }

            @Override
            public int getMaxPosition() {
                return super.getMaxPosition() + (this == list ? listBottomPadding(getHeight()) : 0);
            }

            @Override
            public void setScrollAmount(double amount) {
                super.setScrollAmount(amount);
                if (this == list && !children().isEmpty()) {
                    int first = Math.max(0, Math.min(children().size() - 1,
                        Math.floorDiv(getY() - getRowTop(0) + 4, getEntryHeight())));
                    onListScrollChanged(first);
                }
            }

            @Override
            public int getRowWidth() {
                return getWidth() - 2 * com.nododiiiii.ponderer.editor.screen.UILayoutConstants.LIST_ROW_MARGIN;
            }

            @Override
            public int getRowLeft() {
                // Vanilla adds two pixels here, shifting equal row padding off-center.
                return getX() + (getWidth() - getRowWidth()) / 2;
            }

            @Override
            protected void renderSelection(GuiGraphics graphics, int top, int width, int height,
                                           int outerColor, int innerColor) {
                // Keep selection and focus behavior without drawing the vanilla row outline.
            }
        };
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
        return headerEntries.size() * getHeaderEntryHeight() + LIST_HEADER_HEIGHT + LIST_VERTICAL_PADDING;
    }

    protected final void updateButtonState(@Nullable BoxWidget button, boolean active) {
        if (button != null && button.active != active) {
            button.active = active;
            button.animateGradientFromState();
        }
    }
}
