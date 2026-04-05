package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class AbstractSceneEditorFormScreen extends AbstractJeiAwareFormScreen
    implements HeldItemButtonHost, NbtPickButtonHost {

    protected final DslScene scene;
    protected final int sceneIndex;
    protected final SceneEditorScreen parent;
    @Nullable
    protected Screen returnScreen;

    @Nullable
    protected BoxWidget confirmButton;
    @Nullable
    protected BoxWidget cancelButton;
    protected String errorMessage = null;
    protected String infoMessage = null;

    private final List<SnapshotParticipant> formStateParticipants = new ArrayList<>();
    private boolean formStateParticipantsConfigured = false;
    private boolean initialStatePrepared = false;
    @Nullable
    private Map<String, String> pendingFormRestore = null;
    @Nullable
    private Map<String, String> pendingBaselineRestore = null;

    protected AbstractSceneEditorFormScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                            String scopeKey, String titleKey, int preferredListWidth,
                                            BiConsumer<JeiAwareScreen, IdFieldMode> activationAction) {
        super(parent, scopeKey, titleKey, preferredListWidth, activationAction);
        this.scene = scene;
        this.sceneIndex = sceneIndex;
        this.parent = parent;
    }

    public AbstractSceneEditorFormScreen setReturnScreen(@Nullable Screen returnScreen) {
        this.returnScreen = returnScreen;
        return this;
    }

    public AbstractSceneEditorFormScreen setPendingFormRestore(@Nullable Map<String, String> snapshot) {
        this.pendingBaselineRestore = null;
        if (snapshot == null) {
            this.pendingFormRestore = null;
            return this;
        }

        this.pendingFormRestore = new HashMap<>(snapshot);
        this.pendingBaselineRestore = extractBaselineSnapshotMetadata(this.pendingFormRestore);
        if (pendingFormRestore != null) {
            prepareSnapshotForBuild(pendingFormRestore);
        }
        return this;
    }

    @Override
    protected boolean shouldAutoCaptureBaselineOnInit() {
        return false;
    }

    @Override
    protected void init() {
        ensureFormStateParticipants();
        if (!initialStatePrepared) {
            prepareInitialState();
            initialStatePrepared = true;
        }
        if (pendingFormRestore != null) {
            prepareSnapshotForBuild(pendingFormRestore);
        }

        super.init();

        confirmButton = saveChanges;
        cancelButton = goBack;
        configureActionButtons();

        if (pendingFormRestore != null) {
            restoreFromSnapshot(pendingFormRestore);
            if (pendingBaselineRestore != null) {
                restoreBaselineState(pendingBaselineRestore);
            }
            pendingFormRestore = null;
            pendingBaselineRestore = null;
        } else if (!isBaselineCaptured()) {
            markStateSaved();
        }

        syncStatusMessages();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        syncStatusMessages();
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
        renderFormForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void attemptBackToParent() {
        if (!hasUnsavedChanges()) {
            returnToParent();
            return;
        }

        showLeavingPrompt(response -> {
            if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Cancel) {
                return;
            }
            if (response == net.createmod.catnip.gui.ConfirmationScreen.Response.Confirm) {
                if (!saveEdits()) {
                    return;
                }
            } else {
                discardEdits();
            }
            returnToParent();
        });
    }

    @Override
    public void onClose() {
        attemptBackToParent();
    }

    protected void prepareInitialState() {
    }

    protected void configureActionButtons() {
    }

    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    }

    protected String getHeaderTitle() {
        return UIText.of(titleKey);
    }

    @Override
    protected String getBreadcrumbTitleText() {
        return getHeaderTitle();
    }

    protected void returnToParent() {
        ScreenOpener.open(returnScreen != null ? returnScreen : parent);
    }

    protected void addBaseFormStateParticipants(List<SnapshotParticipant> participants) {
    }

    protected void configureFormState(List<SnapshotParticipant> participants) {
    }

    @Override
    protected final Map<String, String> snapshotState() {
        Map<String, String> snapshot = new HashMap<>();
        for (DeclarativeFormEntry entry : builtFormEntries()) {
            entry.snapshot(snapshot);
        }
        FormState.snapshotOf(formStateParticipants).forEach(snapshot::put);
        appendCustomSnapshot(snapshot);
        return snapshot;
    }

    @Override
    protected final void restoreSnapshot(Map<String, String> snapshot) {
        FormState.restoreInto(snapshot, formStateParticipants);
        for (DeclarativeFormEntry entry : builtFormEntries()) {
            entry.restore(snapshot);
        }
        restoreCustomSnapshot(snapshot);
    }

    protected void appendCustomSnapshot(Map<String, String> snapshot) {
    }

    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
    }

    protected final Map<String, String> snapshotForm() {
        Map<String, String> snapshot = snapshotState();
        appendBaselineSnapshotMetadata(snapshot);
        return snapshot;
    }

    protected final void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreSnapshot(snapshot);
    }

    @Override
    protected void prepareSnapshotForBuild(Map<String, String> snapshot) {
        FormState.restoreInto(snapshot, formStateParticipants);
        restoreCustomSnapshot(snapshot);
    }

    @Override
    protected void afterSnapshotRestored(Map<String, String> snapshot) {
        syncStatusMessages();
    }

    @Override
    public final void startNbtPickFromButton(String nbtSnapshotKey, boolean captureBlockId) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        errorMessage = null;
        infoMessage = null;
        NbtPickState.startPick(snapshotForm(), nbtSnapshotKey, captureBlockId, createReturnContext());
        mc.setScreen(null);
    }

    @Override
    public final void useHeldItemFromButton(Consumer<ItemStack> onItemPicked) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            held = mc.player.getOffhandItem();
        }
        if (held.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.held_item.error.empty");
            infoMessage = null;
            return;
        }
        errorMessage = null;
        infoMessage = null;
        onItemPicked.accept(held);
        infoMessage = UIText.of("ponderer.ui.nbt_pick.filled", held.getHoverName().getString());
    }

    protected void restoreNbtPickNotice(Map<String, String> snapshot) {
        if (!snapshot.containsKey(NbtPickState.SNAPSHOT_NOTICE_KEY)) {
            return;
        }
        String pickedName = snapshot.get(NbtPickState.SNAPSHOT_NOTICE_KEY);
        String translated = UIText.of("ponderer.ui.nbt_pick.filled", pickedName);
        infoMessage = "ponderer.ui.nbt_pick.filled".equals(translated)
            ? ("NBT <- " + pickedName)
            : translated;
    }

    @Nullable
    protected Double parseDouble(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.error.required_field", fieldName);
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            errorMessage = UIText.of("ponderer.ui.error.invalid_number", fieldName);
            return null;
        }
    }

    protected double parseDoubleOr(String value, double fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    protected Float parseFloat(String value, String fieldName) {
        Double d = parseDouble(value, fieldName);
        return d == null ? null : d.floatValue();
    }

    @Nullable
    protected Integer parseInt(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.error.required_field", fieldName);
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            errorMessage = UIText.of("ponderer.ui.error.invalid_integer", fieldName);
            return null;
        }
    }

    protected int parseIntOr(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    protected abstract SnapshotReturnContext createReturnContext();

    private void ensureFormStateParticipants() {
        if (formStateParticipantsConfigured) {
            return;
        }
        formStateParticipantsConfigured = true;
        addBaseFormStateParticipants(formStateParticipants);
        configureFormState(formStateParticipants);
    }

    private void syncStatusMessages() {
        if (errorMessage != null && !errorMessage.isBlank()) {
            setErrorMessage(errorMessage);
            return;
        }
        if (infoMessage != null && !infoMessage.isBlank()) {
            setInfoMessage(infoMessage);
            return;
        }
        clearStatusMessages();
    }
}
