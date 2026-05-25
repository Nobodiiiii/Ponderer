package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.mixin.MultiLineEditBoxAccessor;
import com.nododiiiii.ponderer.mixin.MultilineTextFieldAccessor;
import com.nododiiiii.ponderer.nbt.NbtCoordinateDetector;
import com.nododiiiii.ponderer.nbt.NbtPrettyPrinter;
import com.nododiiiii.ponderer.nbt.NbtTextCodec;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NbtExpandedEditorScreen extends AbstractSceneEditorFormScreen {

    public static final String TEXT_SNAPSHOT_KEY = "_expanded_nbt_text";
    private static final String SCROLL_SNAPSHOT_KEY = "_expanded_nbt_scroll";
    private static final int PREFERRED_LAYOUT_WIDTH = 620;
    private static final int HISTORY_LIMIT = 200;
    private static final int LABEL_WIDTH = 34;
    private static final int GUTTER_WIDTH = 24;
    private static final int GUTTER_GAP = 6;
    private static final int BUTTON_WIDTH = 20;
    private static final int BUTTON_HEIGHT = 12;

    private final SnapshotReturnContext parentContext;
    private final Map<String, String> parentSnapshot;
    private final String targetSnapshotKey;
    private final NbtExpandedEditorContext expandedContext;

    private String editorText = "";
    private boolean validForSave = true;
    @Nullable
    private String validationError = null;
    private double pendingScroll = 0;
    @Nullable
    private ScrollableNbtEditBox editor;
    private List<NbtCoordinateDetector.Candidate> candidates = List.of();
    private final List<RowPickButton> rowButtons = new ArrayList<>();
    private final Deque<EditorHistoryState> undoHistory = new ArrayDeque<>();
    private final Deque<EditorHistoryState> redoHistory = new ArrayDeque<>();
    private boolean captureScrollInSnapshot = false;

    public NbtExpandedEditorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                   SnapshotReturnContext parentContext,
                                   Map<String, String> parentSnapshot,
                                   String targetSnapshotKey) {
        super(scene, sceneIndex, parent, "ponderer.ui.scope.editor", "ponderer.ui.nbt_editor",
            PREFERRED_LAYOUT_WIDTH, (screen, mode) -> {});
        this.parentContext = parentContext;
        this.parentSnapshot = new HashMap<>(parentSnapshot);
        this.targetSnapshotKey = targetSnapshotKey;
        this.expandedContext = new NbtExpandedEditorContext(
            scene, sceneIndex, parent, parentContext, parentSnapshot, targetSnapshotKey);
    }

    @Override
    public NbtExpandedEditorScreen setPendingFormRestore(@Nullable Map<String, String> snapshot) {
        super.setPendingFormRestore(snapshot);
        return this;
    }

    @Override
    protected void prepareInitialState() {
        String raw = parentSnapshot.getOrDefault(targetSnapshotKey, "");
        if (raw == null || raw.trim().isEmpty()) {
            editorText = "";
            validForSave = true;
            validationError = null;
            candidates = List.of();
            return;
        }

        NbtTextCodec.ParseResult parsed = NbtTextCodec.parse(raw);
        if (!parsed.success() || parsed.tag() == null) {
            editorText = raw;
            validForSave = false;
            validationError = parsed.errorMessage() == null
                ? UIText.of("ponderer.ui.nbt_editor.error.invalid")
                : parsed.errorMessage();
            candidates = List.of();
            return;
        }

        NbtPrettyPrinter.FormattedText formatted = NbtPrettyPrinter.format(parsed.tag());
        editorText = formatted.text();
        validForSave = true;
        validationError = null;
        candidates = NbtCoordinateDetector.detect(parsed.tag(), formatted);
    }

    @Override
    protected void init() {
        super.init();
        removeListChrome();
        createEditor();
        if (validationError != null) {
            setErrorMessage(validationError);
        }
        rebuildRowButtons();
        positionRowButtons();
    }

    @Override
    protected void configureActionButtons() {
        if (saveChanges != null) {
            saveChanges.withCallback(this::saveEdits);
        }
    }

    @Override
    protected void configureFormState(List<SnapshotParticipant> participants) {
        participants.add(FieldBindings.string(TEXT_SNAPSHOT_KEY, () -> editorText, value -> {
            editorText = value == null ? "" : value;
            refreshParsedState(false);
        }));
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
    }

    @Override
    protected boolean isSaveButtonActive() {
        return validForSave;
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();
        if (editor != null) {
            editorText = editor.getValue();
        }

        String value = editorText == null ? "" : editorText.trim();
        Map<String, String> nextParentSnapshot = new HashMap<>(parentSnapshot);
        if (value.isEmpty()) {
            nextParentSnapshot.put(targetSnapshotKey, "");
            markStateSaved();
            parentContext.reopenEditor(nextParentSnapshot);
            return true;
        }

        NbtTextCodec.ParseResult parsed = NbtTextCodec.parse(value);
        if (!parsed.success() || parsed.tag() == null) {
            validForSave = false;
            validationError = parsed.errorMessage() == null
                ? UIText.of("ponderer.ui.nbt_editor.error.invalid")
                : parsed.errorMessage();
            setErrorMessage(validationError);
            rebuildRowButtons();
            return false;
        }

        nextParentSnapshot.put(targetSnapshotKey, NbtTextCodec.compact(parsed.tag()));
        markStateSaved();
        parentContext.reopenEditor(nextParentSnapshot);
        return true;
    }

    @Override
    protected void attemptBackToParent() {
        if (!hasUnsavedChanges()) {
            reopenParentSnapshot();
            return;
        }

        showLeavingPrompt(response -> {
            if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Cancel) {
                return;
            }
            if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Confirm) {
                saveEdits();
                return;
            }

            discardEdits();
            reopenParentSnapshot();
        });
    }

    @Override
    protected SnapshotReturnContext createReturnContext() {
        return expandedContext;
    }

    @Override
    protected void prepareSnapshotForBuild(Map<String, String> snapshot) {
        if (snapshot.containsKey(TEXT_SNAPSHOT_KEY)) {
            editorText = snapshot.get(TEXT_SNAPSHOT_KEY);
            refreshParsedState(false);
        }
        if (snapshot.containsKey(SCROLL_SNAPSHOT_KEY)) {
            try {
                pendingScroll = Double.parseDouble(snapshot.get(SCROLL_SNAPSHOT_KEY));
            } catch (NumberFormatException ignored) {
                pendingScroll = 0;
            }
        }
    }

    @Override
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        if (!captureScrollInSnapshot) {
            return;
        }

        if (editor != null) {
            pendingScroll = editor.visibleScroll();
        }
        snapshot.put(SCROLL_SNAPSHOT_KEY, String.valueOf(Math.max(0, pendingScroll)));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey(NbtExpandedPickState.SNAPSHOT_ERROR_KEY)) {
            setErrorMessage(snapshot.get(NbtExpandedPickState.SNAPSHOT_ERROR_KEY));
        } else if (snapshot.containsKey(NbtExpandedPickState.SNAPSHOT_NOTICE_KEY)) {
            setInfoMessage(snapshot.get(NbtExpandedPickState.SNAPSHOT_NOTICE_KEY));
        } else if (validationError != null) {
            setErrorMessage(validationError);
        }
    }

    @Override
    protected void afterSnapshotRestored(Map<String, String> snapshot) {
        syncEditorWidget(snapshot.containsKey(SCROLL_SNAPSHOT_KEY));
        resetEditorHistory();
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.nbt_editor");
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderFormForeground(graphics, mouseX, mouseY, partialTicks);
        if (editor == null) {
            return;
        }

        int labelX = editor.getX() - LABEL_WIDTH - 8;
        graphics.drawString(font, "NBT", labelX, editor.getY() + 6, UILayoutConstants.COLOR_LABEL);

        int gutterX = gutterX();
        graphics.fill(gutterX - 3, editor.getY(), gutterX - 2, editor.getY() + editor.getHeight(), 0x40_FFFFFF);
        positionRowButtons();
        for (RowPickButton rowButton : rowButtons) {
            BoxWidget button = rowButton.button();
            if (!button.visible) {
                continue;
            }
            graphics.drawCenteredString(font, "+",
                button.getX() + button.getWidth() / 2,
                button.getY() + (button.getHeight() - 8) / 2,
                0x80FFFF);
        }
    }

    @Override
    public void tick() {
        super.tick();
        positionRowButtons();
    }

    private void removeListChrome() {
        if (list != null) {
            removeWidget(list);
            list = null;
        }
        if (search != null) {
            removeWidget(search);
            search = null;
        }
    }

    private void createEditor() {
        if (editor != null) {
            removeWidget(editor);
        }

        int totalWidth = currentListWidthValue();
        int editorWidth = Math.max(220, totalWidth - LABEL_WIDTH - GUTTER_WIDTH - GUTTER_GAP);
        int editorHeight = Math.max(80, height - 104);
        int left = width / 2 - totalWidth / 2 + LABEL_WIDTH;
        int top = 38;

        editor = new ScrollableNbtEditBox(font, left, top, editorWidth, editorHeight,
            Component.literal("NBT"), Component.literal(""));
        editor.setCharacterLimit(MultilineTextField.NO_CHARACTER_LIMIT);
        editor.setValue(editorText == null ? "" : editorText);
        editor.setValueListener(this::onEditorTextChanged);
        addRenderableWidget(editor);
        editor.setVisibleScroll(Math.max(0, pendingScroll));
        resetEditorHistory();
    }

    private void onEditorTextChanged(String value) {
        editorText = value == null ? "" : value;
        refreshParsedState(true);
    }

    private void refreshParsedState(boolean updateStatus) {
        String value = editorText == null ? "" : editorText.trim();
        if (value.isEmpty()) {
            validForSave = true;
            validationError = null;
            candidates = List.of();
            if (updateStatus) {
                clearStatusMessages();
            }
            rebuildRowButtons();
            return;
        }

        NbtTextCodec.ParseResult parsed = NbtTextCodec.parse(value);
        if (!parsed.success() || parsed.tag() == null) {
            validForSave = false;
            validationError = parsed.errorMessage() == null
                ? UIText.of("ponderer.ui.nbt_editor.error.invalid")
                : parsed.errorMessage();
            candidates = List.of();
            if (updateStatus) {
                setErrorMessage(validationError);
            }
            rebuildRowButtons();
            return;
        }

        NbtPrettyPrinter.FormattedText formatted = NbtPrettyPrinter.format(parsed.tag());
        validForSave = true;
        validationError = null;
        candidates = formatted.text().equals(editorText)
            ? NbtCoordinateDetector.detect(parsed.tag(), formatted)
            : List.of();
        if (updateStatus) {
            clearStatusMessages();
        }
        rebuildRowButtons();
    }

    private void rebuildRowButtons() {
        for (RowPickButton rowButton : rowButtons) {
            removeWidget(rowButton.button());
        }
        rowButtons.clear();

        for (NbtCoordinateDetector.Candidate candidate : candidates) {
            if (candidate.lineNumber() < 0) {
                continue;
            }
            BoxWidget button = new BoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .withCallback(() -> startCoordinatePick(candidate));
            button.getToolTip().add(Component.literal(UIText.of("ponderer.ui.nbt_editor.pick.tooltip")));
            rowButtons.add(new RowPickButton(candidate, button));
            addRenderableWidget(button);
        }
        positionRowButtons();
    }

    private void positionRowButtons() {
        if (editor == null) {
            return;
        }
        int top = editor.getY() + editor.innerPaddingAmount();
        int bottom = editor.getY() + editor.getHeight() - editor.innerPaddingAmount();
        int x = gutterX();
        int scroll = (int) Math.round(editor.visibleScroll());
        for (RowPickButton rowButton : rowButtons) {
            int y = top + rowButton.candidate().lineNumber() * font.lineHeight - scroll - 1;
            BoxWidget button = rowButton.button();
            button.setX(x);
            button.setY(y);
            button.setWidth(BUTTON_WIDTH);
            button.setHeight(BUTTON_HEIGHT);
            boolean visible = y + BUTTON_HEIGHT >= top && y <= bottom;
            button.visible = visible;
            button.active = visible;
        }
    }

    private int gutterX() {
        if (editor == null) {
            return 0;
        }

        int gutterX = editor.getX() + editor.getWidth() + GUTTER_GAP;
        if (confirmButton != null) {
            gutterX = Math.min(gutterX, confirmButton.getX() - BUTTON_WIDTH - GUTTER_GAP);
        }
        return gutterX;
    }

    private void startCoordinatePick(NbtCoordinateDetector.Candidate candidate) {
        if (editor != null) {
            editorText = editor.getValue();
            pendingScroll = editor.visibleScroll();
        }
        NbtTextCodec.ParseResult parsed = NbtTextCodec.parse(editorText);
        if (!parsed.success() || parsed.tag() == null) {
            validForSave = false;
            validationError = parsed.errorMessage() == null
                ? UIText.of("ponderer.ui.nbt_editor.error.invalid")
                : parsed.errorMessage();
            setErrorMessage(validationError);
            rebuildRowButtons();
            return;
        }

        NbtCoordinateDetector.Candidate liveCandidate =
            NbtCoordinateDetector.findCandidate(parsed.tag(), candidate.path());
        if (liveCandidate == null) {
            setErrorMessage(UIText.of("ponderer.ui.nbt_editor.error.pick_target_missing"));
            rebuildRowButtons();
            return;
        }

        NbtExpandedPickState.startPick(
            snapshotFormWithScroll(),
            liveCandidate.path(),
            expandedContext,
            scene,
            sceneIndex,
            liveCandidate.usesFloatingPoint());
    }

    private void reopenParentSnapshot() {
        parentContext.reopenEditor(parentSnapshot);
    }

    private Map<String, String> snapshotFormWithScroll() {
        boolean previous = captureScrollInSnapshot;
        captureScrollInSnapshot = true;
        try {
            return snapshotForm();
        } finally {
            captureScrollInSnapshot = previous;
        }
    }

    private void syncEditorWidget(boolean restoreScroll) {
        if (editor == null) {
            return;
        }

        boolean focused = editor.isFocused();
        double targetScroll = restoreScroll ? pendingScroll : editor.visibleScroll();
        editor.setValue(editorText == null ? "" : editorText);
        editor.setVisibleScroll(targetScroll);
        editor.setFocused(focused);
    }

    private void resetEditorHistory() {
        undoHistory.clear();
        redoHistory.clear();
    }

    private void recordEditorEdit(EditorHistoryState before, EditorHistoryState after) {
        if (before.text().equals(after.text())) {
            return;
        }

        undoHistory.addLast(before);
        while (undoHistory.size() > HISTORY_LIMIT) {
            undoHistory.removeFirst();
        }
        redoHistory.clear();
    }

    private boolean undoEditorEdit() {
        if (editor == null || undoHistory.isEmpty()) {
            return false;
        }

        redoHistory.addLast(editor.captureHistoryState());
        while (redoHistory.size() > HISTORY_LIMIT) {
            redoHistory.removeFirst();
        }
        editor.applyHistoryState(undoHistory.removeLast());
        return true;
    }

    private boolean redoEditorEdit() {
        if (editor == null || redoHistory.isEmpty()) {
            return false;
        }

        undoHistory.addLast(editor.captureHistoryState());
        while (undoHistory.size() > HISTORY_LIMIT) {
            undoHistory.removeFirst();
        }
        editor.applyHistoryState(redoHistory.removeLast());
        return true;
    }

    private record EditorHistoryState(String text, int cursor, int selectCursor, double scroll) {
    }

    private record RowPickButton(NbtCoordinateDetector.Candidate candidate, BoxWidget button) {
    }

    private class ScrollableNbtEditBox extends MultiLineEditBox {

        ScrollableNbtEditBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height,
                             Component message, Component placeholder) {
            super(font, x, y, width, height, message, placeholder);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            if (button == 0 && withinContentAreaPoint(mouseX, mouseY)) {
                MultiLineEditBoxAccessor accessor = (MultiLineEditBoxAccessor) this;
                accessor.ponderer$getTextField().setSelecting(Screen.hasShiftDown());
                accessor.ponderer$seekCursorScreen(mouseX, mouseY);
                return true;
            }
            return handled;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (handleUndoRedo(keyCode)) {
                return true;
            }

            EditorHistoryState before = captureHistoryState();
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (handled) {
                recordEditorEdit(before, captureHistoryState());
            }
            return handled;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            EditorHistoryState before = captureHistoryState();
            boolean handled = super.charTyped(codePoint, modifiers);
            if (handled) {
                recordEditorEdit(before, captureHistoryState());
            }
            return handled;
        }

        double visibleScroll() {
            return scrollAmount();
        }

        void setVisibleScroll(double value) {
            setScrollAmount(value);
        }

        int innerPaddingAmount() {
            return innerPadding();
        }

        EditorHistoryState captureHistoryState() {
            MultiLineEditBoxAccessor accessor = (MultiLineEditBoxAccessor) this;
            MultilineTextField textField = accessor.ponderer$getTextField();
            MultilineTextFieldAccessor textAccessor = (MultilineTextFieldAccessor) textField;
            return new EditorHistoryState(getValue(), textField.cursor(), textAccessor.ponderer$getSelectCursor(), visibleScroll());
        }

        void applyHistoryState(EditorHistoryState state) {
            MultiLineEditBoxAccessor accessor = (MultiLineEditBoxAccessor) this;
            MultilineTextField textField = accessor.ponderer$getTextField();
            MultilineTextFieldAccessor textAccessor = (MultilineTextFieldAccessor) textField;
            boolean focused = isFocused();

            setValue(state.text());
            textField.setSelecting(false);
            textField.seekCursor(Whence.ABSOLUTE, state.cursor());
            textAccessor.ponderer$setSelectCursor(state.selectCursor());
            setVisibleScroll(state.scroll());
            setFocused(focused);
        }

        private boolean handleUndoRedo(int keyCode) {
            if (!isFocused() || !Screen.hasControlDown()) {
                return false;
            }

            if (keyCode == GLFW.GLFW_KEY_Z) {
                return Screen.hasShiftDown() ? redoEditorEdit() : undoEditorEdit();
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                return redoEditorEdit();
            }
            return false;
        }
    }
}
