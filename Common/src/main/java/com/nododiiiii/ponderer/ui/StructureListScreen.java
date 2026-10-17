package com.nododiiiii.ponderer.ui;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.network.StructurePreviewRequestPayload;
import com.nododiiiii.ponderer.network.StructurePreviewResponsePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class StructureListScreen extends AbstractDeclarativeListScreen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static final int PREVIEW_WIDTH = 220;
    private static final int PREVIEW_GAP = 10;
    private static final long REQUEST_TIMEOUT_MS = 15000;
    private static final String STATUS_PREFIX = StructurePreviewRequestPayload.STATUS_PREFIX;

    private final Screen returnScreen;
    private final Consumer<String> onSelect;
    private final List<String> allStructures = new ArrayList<>();
    private final StructurePreviewWidget preview = new StructurePreviewWidget(0, 0, 0, 0);
    private final Map<String, StructurePreviewResponsePayload> previewCache = new LinkedHashMap<>();
    @Nullable private String selectedId;
    private int previewX, previewY, previewW, previewH;
    private boolean previewVisible;
    private boolean catalogRequested;
    private long catalogRequestId, previewRequestId;
    private long catalogRequestedAt, previewRequestedAt;

    public StructureListScreen(Screen parent, Consumer<String> onSelect, @Nullable String initialValue) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.scene_desc.structure_list_title", UILayoutConstants.EDITOR_LIST_W);
        this.returnScreen = parent;
        this.onSelect = onSelect;
        selectedId = initialValue == null || initialValue.isBlank() ? null : initialValue.trim();
    }

    @Override
    protected void init() {
        if (!catalogRequested) reloadLocalStructures();
        super.init();
        setActionButtonTooltip(saveChanges, "ponderer.ui.confirm");
        setActionButtonTooltip(discardChanges, "ponderer.ui.structure_picker.clear_selection");
        if (discardChanges != null) discardChanges.withCallback(this::discardEdits);
        layoutPreviewPanel();
        if (!catalogRequested) {
            catalogRequested = true;
            catalogRequestId = REQUEST_IDS.incrementAndGet();
            catalogRequestedAt = System.currentTimeMillis();
            rebuildListPreservingScroll();
            sendRequest(catalogRequestId, "");
        }
        if (selectedId != null && previewVisible) requestPreview();
    }

    private void layoutPreviewPanel() {
        int listLeft = width / 2 - currentListWidthValue() / 2 + listHorizontalOffset();
        previewVisible = listLeft - PREVIEW_GAP >= PREVIEW_WIDTH + PREVIEW_GAP;
        previewW = previewVisible ? PREVIEW_WIDTH : 0;
        previewH = contentAreaHeight();
        previewX = listLeft - PREVIEW_GAP - previewW;
        previewY = contentAreaTop();
        preview.setBounds(previewX, previewY, previewW, previewH);
    }

    @Override
    protected int listHorizontalOffset() {
        int composite = PREVIEW_WIDTH + PREVIEW_GAP + currentListWidthValue() + 40;
        return composite > width - 20 ? 0 : (PREVIEW_WIDTH + PREVIEW_GAP) / 2;
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (allStructures.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of(catalogRequestId != 0
                ? STATUS_PREFIX + "loading" : "ponderer.ui.structure_list.empty")));
            return;
        }
        for (String id : allStructures) {
            entries.add(new FullButtonListEntry(
                () -> (id.equals(selectedId) ? "● " : "  ") + id, null,
                () -> selectStructure(id), () -> id.equals(selectedId) ? 0xFFE082 : 0xFFFFFF, () -> true));
        }
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }

    private void selectStructure(String id) {
        selectedId = id;
        previewRequestId = 0;
        rebuildListPreservingScroll();
        if (previewVisible) requestPreview();
    }

    private void requestPreview() {
        StructurePreviewResponsePayload cached = previewCache.get(selectedId);
        if (cached != null) {
            showPreview(cached);
            return;
        }
        if (previewRequestId != 0 || selectedId == null) return;
        preview.setStatus(STATUS_PREFIX + "loading");
        previewRequestId = REQUEST_IDS.incrementAndGet();
        previewRequestedAt = System.currentTimeMillis();
        sendRequest(previewRequestId, selectedId);
    }

    private void sendRequest(long requestId, String id) {
        try {
            PondererServices.NETWORK.sendToServer(new StructurePreviewRequestPayload(requestId, id));
        } catch (Exception | LinkageError e) {
            LOGGER.debug("Cannot request structure preview for {}", id, e);
            if (id.isEmpty()) {
                catalogRequestId = 0;
                rebuildListPreservingScroll();
            } else {
                previewRequestId = 0;
            }
            preview.setStatus(STATUS_PREFIX + "unavailable");
        }
    }

    public void acceptPreviewResponse(StructurePreviewResponsePayload response) {
        try {
            if (response.structureId().isEmpty() && response.requestId() == catalogRequestId) {
                catalogRequestId = 0;
                if (response.statusKey().isEmpty()) {
                    allStructures.clear();
                    allStructures.addAll(response.structures());
                } else {
                    preview.setStatus(STATUS_PREFIX + "unavailable");
                }
                rebuildListPreservingScroll();
            } else if (response.requestId() == previewRequestId && response.structureId().equals(selectedId)) {
                previewRequestId = 0;
                if (previewCache.size() >= 4) previewCache.remove(previewCache.keySet().iterator().next());
                previewCache.put(selectedId, response);
                showPreview(response);
            }
        } catch (Exception | LinkageError e) {
            LOGGER.debug("Cannot apply structure preview for {}", response.structureId(), e);
            previewRequestId = 0;
            preview.setStatus(STATUS_PREFIX + "unavailable");
        }
    }

    private void showPreview(StructurePreviewResponsePayload response) {
        try {
            if (!response.statusKey().isEmpty()) {
                preview.setStatus(response.statusKey());
                return;
            }
            try (var input = new ByteArrayInputStream(response.template())) {
                CompoundTag nbt = NbtIo.readCompressed(input, new NbtAccounter(8 * 1024 * 1024L, 64));
                preview.loadTemplate(nbt);
            }
        } catch (Exception | LinkageError e) {
            LOGGER.debug("Cannot load structure preview for {}", response.structureId(), e);
            preview.setStatus(STATUS_PREFIX + "unavailable");
        }
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (catalogRequestId != 0 && now - catalogRequestedAt > REQUEST_TIMEOUT_MS) {
            catalogRequestId = 0;
            rebuildListPreservingScroll();
            if (selectedId == null) preview.setStatus(STATUS_PREFIX + "unavailable");
        }
        if (previewRequestId != 0 && now - previewRequestedAt > REQUEST_TIMEOUT_MS) {
            previewRequestId = 0;
            preview.setStatus(STATUS_PREFIX + "unavailable");
        }
    }

    @Override
    protected boolean saveEdits() {
        if (selectedId == null) return false;
        onSelect.accept(selectedId);
        Minecraft.getInstance().setScreen(returnScreen);
        return true;
    }

    @Override
    protected void discardEdits() {
        selectedId = null;
        previewRequestId = 0;
        preview.clear();
        rebuildListPreservingScroll();
    }

    @Override protected boolean hasUnsavedChanges() { return false; }
    @Override protected int getUnsavedChangeCount() { return 0; }
    @Override protected boolean isSaveButtonActive() { return selectedId != null; }
    @Override protected boolean isDiscardButtonActive() { return selectedId != null; }

    @Override
    public void removed() {
        super.removed();
        preview.dispose();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
        if (!previewVisible) return;
        graphics.enableScissor(previewX, previewY, previewX + previewW, previewY + previewH);
        try {
            preview.render(graphics, partialTicks);
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (previewVisible && preview.onMouseDown(mouseX, mouseY)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return preview.onMouseDrag(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = preview.onMouseUp();
        return super.mouseReleased(mouseX, mouseY, button) || handled;
    }

    private void reloadLocalStructures() {
        allStructures.clear();
        try {
            Registry<?> registry = null;
            var mc = Minecraft.getInstance();
            var server = mc.getSingleplayerServer();
            if (server != null) registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            if ((registry == null || registry.keySet().isEmpty()) && mc.getConnection() != null) {
                registry = mc.getConnection().registryAccess().registryOrThrow(Registries.STRUCTURE);
            }
            if (registry != null) {
                for (var key : registry.keySet()) allStructures.add(key.toString());
            }
        } catch (Exception | LinkageError e) {
            LOGGER.debug("Cannot read local structure registry; requesting the server catalog", e);
        }
        Collections.sort(allStructures);
    }
}
