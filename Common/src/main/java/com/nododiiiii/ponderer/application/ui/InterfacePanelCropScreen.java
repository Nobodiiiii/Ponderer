package com.nododiiiii.ponderer.application.ui;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.application.api.RuntimeAccess;
import com.nododiiiii.ponderer.application.ui.widgets.AbstractDeclarativeScreen;
import com.nododiiiii.ponderer.editor.api.EditorResources;
import com.nododiiiii.ponderer.runtime.api.CompilationResult;
import com.nododiiiii.ponderer.runtime.api.CompileRequest;
import com.nododiiiii.ponderer.runtime.api.PlaybackExecutionContext;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.Fidelity;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.SceneSnapshot;
import com.nododiiiii.ponderer.scene.interfaces.InterfacePanelSpec;
import com.nododiiiii.ponderer.scene.interfaces.LegacyInterfaceMigrator;
import com.nododiiiii.ponderer.support.ui.ScreenTooltipBoxWidget;
import net.createmod.catnip.gui.element.TextStencilElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/** Selects a display rectangle over a read-only sandbox; no mouse input reaches the menu. */
final class InterfacePanelCropScreen extends AbstractDeclarativeScreen implements AutoCloseable {
    private static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8, DRAW = 16, MOVE = 32;
    private static final int HANDLE_RADIUS = 5;
    private final DslScene draft;
    private final int sceneIndex, stepIndex;
    private final DslScene.DslStep opening;
    private final Consumer<List<Double>> onSelected;
    @Nullable private final List<Double> initialCrop;
    @Nullable private CompilationResult compiled;
    @Nullable private SandboxSession session;
    private BoxWidget apply;
    private int[] full = {0, 0, 176, 166}, display = {0, 0, 176, 166};
    private double left, top, right = 1, bottom = 1;
    private double pressX, pressY, startLeft, startTop, startRight, startBottom;
    private int dragMode, dragButton = -1;
    private boolean emptyPixelSelection;
    private boolean selectionInitialized;

    InterfacePanelCropScreen(Screen parent, DslScene draft, int sceneIndex, int stepIndex,
                             DslScene.DslStep opening, @Nullable List<Double> crop,
                             Consumer<List<Double>> onSelected) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.interface_panel.range.title");
        this.draft = draft;
        this.sceneIndex = sceneIndex;
        this.stepIndex = stepIndex;
        this.opening = opening;
        this.onSelected = onSelected;
        initialCrop = crop == null ? null : List.copyOf(crop);
        selectionInitialized = crop != null;
        if (crop != null) {
            InterfacePanelSpec.validateCrop(crop);
            left = crop.get(0);
            top = crop.get(1);
            right = Math.min(1, left + crop.get(2));
            bottom = Math.min(1, top + crop.get(3));
        }
    }

    @Override protected void init() {
        super.init();
        dragButton = -1;
        if (session == null) openInterface();
        int buttonWidth = Math.min(100, Math.max(48, (width - 40) / 3));
        int x = (width - buttonWidth * 3 - 16) / 2;
        apply = addButton(x, buttonWidth, "gui.done", this::saveEdits);
        addButton(x + buttonWidth + 8, buttonWidth, "ponderer.ui.interface_panel.range.reset", () -> {
            left = top = 0;
            right = bottom = 1;
            emptyPixelSelection = false;
            selectionInitialized = false;
        });
        addButton(x + (buttonWidth + 8) * 2, buttonWidth, "gui.cancel", this::onClose);
        apply.active = ready();
        apply.updateGradientFromState();
        layout();
    }

    private BoxWidget addButton(int x, int buttonWidth, String label, Runnable action) {
        BoxWidget widget = new ScreenTooltipBoxWidget(x, height - 30, buttonWidth, 24).withCallback(action);
        widget.showingElement(new TextStencilElement(font, Component.translatable(label)).centered(true, true)
            .withElementRenderer(BoxWidget.gradientFactory.apply(widget)));
        return addRenderableWidget(widget);
    }

    private void openInterface() {
        EditorResources.track(this);
        try {
            compiled = RuntimeAccess.engineFor(draft).compile(new CompileRequest(SceneSnapshot.of(draft), sceneIndex));
            if (compiled == null) return;
            int tick = Math.max(0, compiled.stepTicks().get(stepIndex) - 1);
            PlaybackExecutionContext.run(() -> compiled.scene().seekToTime(tick));
            session = SandboxSession.open(LegacyInterfaceMigrator.fromStep(opening), compiled.scene().getWorld());
        } catch (RuntimeException error) {
            LogUtils.getLogger().warn("Unable to open interface panel range selector for {}", draft.id, error);
            close();
        }
    }

    private boolean ready() {
        return session != null && !session.isDestroyed() && session.currentScreen() != null && session.fidelity() != Fidelity.SCHEMATIC;
    }

    private void layout() {
        if (session == null) return;
        full = session.panelBounds();
        int y = 34 + font.split(Component.translatable("ponderer.ui.interface_panel.range.hint"), Math.max(1, width - 24)).size() * font.lineHeight;
        int availableHeight = Math.max(1, height - y - 58);
        double fit = Math.min(1, Math.min(Math.max(1, width - 32) / (double) full[2], availableHeight / (double) full[3]));
        int w = Math.max(1, (int) Math.round(full[2] * fit)), h = Math.max(1, (int) Math.round(full[3] * fit));
        display = new int[]{(width - w) / 2, y + (availableHeight - h) / 2, w, h};
    }

    @Override public void tick() {
        super.tick();
        if (session != null) session.tick();
        apply.active = ready();
        apply.updateGradientFromState();
    }

    @Override protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        layout();
        if (session != null) session.renderPanel(graphics, -1, -1, partialTicks, full, display);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        try {
            renderSelection(graphics);
            apply.active = ready();
            apply.updateGradientFromState();
            renderBreadcrumb(graphics, width / 2, 10);
            graphics.drawWordWrap(font, Component.translatable("ponderer.ui.interface_panel.range.hint"), 12, 28, Math.max(1, width - 24), 0xCCCCCC);
        } finally {
            graphics.pose().popPose();
            // Let Catnip draw its controls and tooltips above the offscreen GUI composite.
            graphics.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private void renderSelection(GuiGraphics graphics) {
        if (ready()) {
            int x0 = sx(left), y0 = sy(top), x1 = sx(right), y1 = sy(bottom);
            int dx = display[0], dy = display[1], dr = dx + display[2], db = dy + display[3];
            graphics.fill(dx, dy, dr, y0, 0xA0000000);
            graphics.fill(dx, y1, dr, db, 0xA0000000);
            graphics.fill(dx, y0, x0, y1, 0xA0000000);
            graphics.fill(x1, y0, dr, y1, 0xA0000000);
            graphics.renderOutline(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0), 0xFF7FE6FF);
            for (int x : new int[]{x0, (x0 + x1) / 2, x1})
                for (int y : new int[]{y0, (y0 + y1) / 2, y1})
                    if (x != (x0 + x1) / 2 || y != (y0 + y1) / 2) graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF7FE6FF);
            int[] selected = InterfacePanelSpec.cropRect(full, crop());
            Component label = emptyPixelSelection ? Component.translatable("ponderer.ui.interface_panel.range.no_pixels")
                : Component.translatable("ponderer.ui.interface_panel.range.selection",
                    selected[0] - full[0], selected[1] - full[1], selected[2], selected[3]);
            graphics.drawCenteredString(font, label, width / 2, height - 45, emptyPixelSelection ? 0xFFCC55 : 0xFFFFFF);
        } else {
            graphics.drawCenteredString(font, Component.translatable("ponderer.ui.interface_panel.range.unavailable"),
                width / 2, height - 45, 0xFF8888);
        }
    }

    private int sx(double x) { return display[0] + (int) Math.round(x * display[2]); }
    private int sy(double y) { return display[1] + (int) Math.round(y * display[3]); }
    private double nx(double x) { return Math.clamp((x - display[0]) / display[2], 0, 1); }
    private double ny(double y) { return Math.clamp((y - display[1]) / display[3], 0, 1); }

    private int hitEdges(double x, double y) {
        int edges = 0;
        if (y >= sy(top) - HANDLE_RADIUS && y <= sy(bottom) + HANDLE_RADIUS) {
            double dl = Math.abs(x - sx(left)), dr = Math.abs(x - sx(right));
            if (Math.min(dl, dr) <= HANDLE_RADIUS) edges |= dl <= dr ? LEFT : RIGHT;
        }
        if (x >= sx(left) - HANDLE_RADIUS && x <= sx(right) + HANDLE_RADIUS) {
            double dt = Math.abs(y - sy(top)), db = Math.abs(y - sy(bottom));
            if (Math.min(dt, db) <= HANDLE_RADIUS) edges |= dt <= db ? TOP : BOTTOM;
        }
        return edges;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!ready() || button != 0 && button != 1 && button != 2) return false;
        layout();
        if (button == 2) {
            if (mouseX < display[0] || mouseY < display[1]
                || mouseX >= display[0] + display[2] || mouseY >= display[1] + display[3]) return false;
            dragButton = -1;
            setDragging(false);
            int x = full[0] + Math.min(full[2] - 1, (int) (nx(mouseX) * full[2]));
            int y = full[1] + Math.min(full[3] - 1, (int) (ny(mouseY) * full[3]));
            int[] selected = session.connectedPanelPixels(full, x, y);
            emptyPixelSelection = selected == null;
            if (selected != null) {
                double nextLeft = (selected[0] - full[0]) / (double) full[2];
                double nextTop = (selected[1] - full[1]) / (double) full[3];
                double nextRight = (selected[0] - full[0] + selected[2]) / (double) full[2];
                double nextBottom = (selected[1] - full[1] + selected[3]) / (double) full[3];
                if (hasControlDown() && selectionInitialized) {
                    left = Math.min(left, nextLeft); top = Math.min(top, nextTop);
                    right = Math.max(right, nextRight); bottom = Math.max(bottom, nextBottom);
                } else {
                    left = nextLeft; top = nextTop; right = nextRight; bottom = nextBottom;
                }
                selectionInitialized = true;
            }
            return true;
        }
        if (mouseX < display[0] - HANDLE_RADIUS || mouseY < display[1] - HANDLE_RADIUS
            || mouseX > display[0] + display[2] + HANDLE_RADIUS || mouseY > display[1] + display[3] + HANDLE_RADIUS) return false;
        pressX = nx(mouseX);
        pressY = ny(mouseY);
        startLeft = left; startTop = top; startRight = right; startBottom = bottom;
        if (button == 1) {
            if (pressX < left || pressX > right || pressY < top || pressY > bottom) return false;
            dragMode = MOVE;
        } else {
            dragMode = hitEdges(mouseX, mouseY);
            if (dragMode == 0) dragMode = DRAW;
        }
        dragButton = button;
        setDragging(true);
        updateSelection(pressX, pressY);
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (button != dragButton || !ready()) return super.mouseDragged(x, y, button, deltaX, deltaY);
        updateSelection(nx(x), ny(y));
        return true;
    }

    private void updateSelection(double x, double y) {
        emptyPixelSelection = false;
        selectionInitialized = true;
        double minW = 1.0 / full[2], minH = 1.0 / full[3];
        double dx = x - pressX, dy = y - pressY;
        if (dragMode == MOVE) {
            dx = Math.clamp(dx, -startLeft, 1 - startRight);
            dy = Math.clamp(dy, -startTop, 1 - startBottom);
            left = startLeft + dx; right = startRight + dx;
            top = startTop + dy; bottom = startBottom + dy;
        } else if (dragMode == DRAW) {
            if (hasControlDown()) {
                double cx = Math.clamp(pressX, minW / 2, 1 - minW / 2), cy = Math.clamp(pressY, minH / 2, 1 - minH / 2);
                double hw = symmetricHalfSize(Math.abs(x - cx), cx, minW);
                double hh = symmetricHalfSize(Math.abs(y - cy), cy, minH);
                left = cx - hw; right = cx + hw; top = cy - hh; bottom = cy + hh;
            } else {
                left = Math.min(pressX, x); right = Math.max(pressX, x);
                top = Math.min(pressY, y); bottom = Math.max(pressY, y);
                if (right - left < minW) { left = Math.min(left, 1 - minW); right = left + minW; }
                if (bottom - top < minH) { top = Math.min(top, 1 - minH); bottom = top + minH; }
            }
        } else {
            if ((dragMode & (LEFT | RIGHT)) != 0) {
                if (hasControlDown()) {
                    double cx = (startLeft + startRight) / 2;
                    double hw = symmetricHalfSize((startRight - startLeft) / 2 + ((dragMode & LEFT) != 0 ? -dx : dx), cx, minW);
                    left = cx - hw; right = cx + hw;
                } else if ((dragMode & LEFT) != 0) left = Math.clamp(startLeft + dx, 0, Math.max(0, startRight - minW));
                else right = Math.clamp(startRight + dx, Math.min(1, startLeft + minW), 1);
            }
            if ((dragMode & (TOP | BOTTOM)) != 0) {
                if (hasControlDown()) {
                    double cy = (startTop + startBottom) / 2;
                    double hh = symmetricHalfSize((startBottom - startTop) / 2 + ((dragMode & TOP) != 0 ? -dy : dy), cy, minH);
                    top = cy - hh; bottom = cy + hh;
                } else if ((dragMode & TOP) != 0) top = Math.clamp(startTop + dy, 0, Math.max(0, startBottom - minH));
                else bottom = Math.clamp(startBottom + dy, Math.min(1, startTop + minH), 1);
            }
        }
    }

    private static double symmetricHalfSize(double size, double center, double minSize) {
        double limit = Math.min(center, 1 - center);
        return Math.clamp(size, Math.min(minSize / 2, limit), limit);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != dragButton) return super.mouseReleased(x, y, button);
        if (ready()) updateSelection(nx(x), ny(y));
        dragButton = -1;
        setDragging(false);
        return true;
    }

    @Nullable private List<Double> crop() {
        return left == 0 && top == 0 && right == 1 && bottom == 1 ? null : List.of(left, top, right - left, bottom - top);
    }

    @Override protected boolean saveEdits() {
        if (!ready()) return false;
        onSelected.accept(crop());
        onClose();
        return true;
    }

    @Override protected boolean hasUnsavedChanges() { return !java.util.Objects.equals(initialCrop, crop()); }
    @Override protected int getUnsavedChangeCount() { return hasUnsavedChanges() ? 1 : 0; }
    @Override protected void discardEdits() {
        selectionInitialized = initialCrop != null;
        emptyPixelSelection = false;
        left = initialCrop == null ? 0 : initialCrop.get(0);
        top = initialCrop == null ? 0 : initialCrop.get(1);
        right = initialCrop == null ? 1 : left + initialCrop.get(2);
        bottom = initialCrop == null ? 1 : top + initialCrop.get(3);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { saveEdits(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public void removed() {
        try { close(); } finally { super.removed(); }
    }

    @Override public void close() {
        var closingSession = session;
        var closingCompilation = compiled;
        session = null;
        compiled = null;
        try {
            if (closingSession != null) closingSession.destroy("panel range selector closed");
        } finally {
            try {
                if (closingCompilation != null) closingCompilation.playback().close();
            } finally {
                EditorResources.release(this);
            }
        }
    }
}
