package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PackStateStore;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.ui.PickState;
import com.nododiiiii.ponderer.ui.PonderRuntimeZLayers;
import com.nododiiiii.ponderer.ui.PonderScreenNavigation;
import com.nododiiiii.ponderer.ui.PonderTextListWidget;
import com.nododiiiii.ponderer.ui.PondererConfigScreen;
import com.nododiiiii.ponderer.ui.PondererDialogScreen;
import com.nododiiiii.ponderer.ui.ReadonlyPackImportPromptScreen;
import com.nododiiiii.ponderer.ui.UiAnchorCoords;
import com.nododiiiii.ponderer.ui.UiAnchorViewport;
import com.nododiiiii.ponderer.ui.SceneEditorScreen;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.NbtExpandedPickState;
import com.nododiiiii.ponderer.ui.UIText;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderUI.class)
public abstract class PonderUIMixin extends Screen {

    @Unique
    private PonderButton ponderer$editButton;

    protected PonderUIMixin() {
        super(CommonComponents.EMPTY);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ponderer$addEditButton(CallbackInfo ci) {
        PonderUI self = (PonderUI) (Object) this;
        addRenderableWidget(new PonderTextListWidget(self));

        // Hijack the Create "comfy reading" button (slowMode): repurpose it as
        // the text-progress panel toggle.
        PonderUIAccessor accessor = (PonderUIAccessor) this;
        PonderButton slowMode = accessor.ponderer$getSlowMode();
        if (slowMode != null) {
            slowMode.withCallback(() -> PonderTextListWidget.VISIBLE = !PonderTextListWidget.VISIBLE);
        }

        var match = ponderer$resolveDynamicScene(self);
        if (match == null) {
            return;
        }
        SceneEditorScreen.handlePonderUiFocusChanged(match.scene());

        if (PickState.isActive() || NbtExpandedPickState.isActive()) {
            return;
        }

        if (!canEdit(Minecraft.getInstance().player)) {
            return;
        }

        int bY = this.height - 20 - 31;
        PonderButton editButton = new PonderButton(this.width - 80 - 31, bY)
                .showing(new ItemStack(Items.WRITABLE_BOOK))
                .enableFade(0, 5);
        editButton.withCallback(() -> {
            PonderUI current = (PonderUI) (Object) this;
            var result = ponderer$resolveDynamicScene(current);
            if (result != null) {
                if (!SceneEditorScreen.canModifyScene(result.scene())) {
                    ponderer$showReadonlyScenePrompt(current);
                    return;
                }
                String packId = result.scene().pack;
                PackStateStore.load();
                if (packId != null && !packId.isBlank() && !PackStateStore.isImported(packId)) {
                    new ReadonlyPackImportPromptScreen(current, packId, result.scene().sceneKey(), result.sceneIndex()).open();
                    return;
                }
                SceneEditorScreen.markUiToEditorTransition(result.scene());
                PonderScreenNavigation.suppressNextPonderReturn();
                Minecraft.getInstance().setScreen(new SceneEditorScreen(result.scene(), result.sceneIndex()));
            }
        });

        ponderer$editButton = editButton;
        addRenderableWidget(editButton);
    }

    @Unique
    private static void ponderer$showReadonlyScenePrompt(Screen source) {
        new PondererDialogScreen(
            source,
            List.of(Component.translatable("ponderer.ui.readonly_scene.title")),
            List.of(Component.translatable("ponderer.ui.readonly_scene.message")),
            List.of(
                PondererDialogScreen.button(
                    Component.translatable("ponderer.ui.readonly_scene.open_config"),
                    dialog -> {
                        PonderScreenNavigation.ReturnState returnState = PonderScreenNavigation.captureReturnState();
                        dialog.closeToSource();
                        PonderScreenNavigation.suppressNextPonderReturn();
                        Minecraft.getInstance().setScreen(new PondererConfigScreen(source, returnState));
                    }),
                PondererDialogScreen.closeButton(Component.translatable("ponderer.ui.cancel"))))
            .open();
    }

    @Inject(method = "renderWindow", at = @At("TAIL"), remap = false)
    private void ponderer$renderWidgetsOnTop(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER);
        // The scene is drawn at pose-z ≈ +5800 (after ponderer$liftSceneOutOfBackgroundDepth
        // pushes it to +5000 and the projection translate of +800 adds on top), with geometry
        // extents reaching pose-z ≈ +6100. Anything added with addRenderableWidget normally
        // renders at pose-z = 0 in Screen.render and fails LEQUAL against the scene's stored
        // depth, so we re-issue every renderable here above the scene envelope.
        for (GuiEventListener child : this.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTicks);
            }
        }
        graphics.pose().popPose();
    }

    /**
     * Lift every draw inside {@link PonderUI#renderWidgets} above the scene's depth envelope.
     * The scene is rendered at pose-z ≈ 5800 (5000 pose + 800 projection) with geometry
     * extents reaching ~6100. Pushing the pose to {@link PonderRuntimeZLayers#PONDER_TEXT_BASELINE_LAYER}
     * (6500) means all of {@code renderSceneInformation}, the inner {@code renderOverlay} chain
     * (ponder text via TextWindowElement), {@code renderNextUp}, breadcrumbs, tag chips and
     * {@code renderHoverTooltips} land at NDC z below the scene's max NDC z and pass LEQUAL
     * regardless of whether depth-test is on or off at the time of buffer flush.
     */
    @Inject(method = "renderWidgets", at = @At("HEAD"), remap = false)
    private void ponderer$liftRenderWidgetsAboveScene(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_TEXT_BASELINE_LAYER);
    }

    @Inject(method = "renderWidgets", at = @At("RETURN"), remap = false)
    private void ponderer$popRenderWidgetsLift(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        graphics.pose().popPose();
    }

    @Inject(method = "replay", at = @At("TAIL"), remap = false)
    private void ponderer$resetViewOnReplay(CallbackInfo ci) {
        PonderUI self = (PonderUI) (Object) this;
        ponderer$resetCustomView(self.getActiveScene());
    }

    @Inject(method = "scroll", at = @At("RETURN"), remap = false)
    private void ponderer$resetViewOnScroll(boolean forward, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        PonderUI self = (PonderUI) (Object) this;
        ponderer$resetCustomView(self.getActiveScene());
        var match = ponderer$resolveDynamicScene(self);
        if (match != null) {
            SceneEditorScreen.handlePonderUiFocusChanged(match.scene());
        } else {
            SceneEditorScreen.handlePonderUiFocusChanged(null);
        }
    }

    /**
     * PonderUI eats the scroll wheel for scene navigation before the widget
     * layer sees it. Route scroll to the text list widget first when the cursor
     * is hovering it so users can page through the list.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void ponderer$routeScrollToTextList(double mouseX, double mouseY, double delta,
            CallbackInfoReturnable<Boolean> cir) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof PonderTextListWidget panel && panel.isMouseOver(mouseX, mouseY)) {
                if (panel.mouseScrolled(mouseX, mouseY, delta)) {
                    cir.setReturnValue(true);
                }
                return;
            }
        }
    }

    /**
     * Append our edit button's label to PonderUI's shared bottom-button hover
     * tooltip strip (the same row PonderUI uses for slowMode/userMode/etc.).
     */
    @Inject(method = "renderHoverTooltips", at = @At("TAIL"), remap = false)
    private void ponderer$renderEditButtonTooltip(GuiGraphics graphics, int tooltipColor, CallbackInfo ci) {
        if (ponderer$editButton == null || !ponderer$editButton.isHoveredOrFocused()) {
            return;
        }
        int tooltipY = this.height - 16;
        graphics.drawCenteredString(Minecraft.getInstance().font,
            Component.translatable("ponderer.ui.tooltip.edit_scene"),
            ponderer$editButton.getX() + 10, tooltipY, tooltipColor);
    }

    @Inject(method = "removed", at = @At("TAIL"), remap = false)
    private void ponderer$clearSceneEditorUndoOnTrueExit(CallbackInfo ci) {
        PonderUI self = (PonderUI) (Object) this;
        var match = ponderer$resolveDynamicScene(self);
        if (match != null) {
            SceneEditorScreen.handlePonderUiRemoved(match.scene());
        } else {
            SceneEditorScreen.handlePonderUiRemoved(null);
        }
    }

    private static void ponderer$resetCustomView(PonderScene scene) {
        if (!(scene instanceof PonderSceneViewOffsetAccess viewOffset)) {
            return;
        }
        viewOffset.ponderer$resetViewOffset();

        if (scene instanceof PonderSceneAccessor accessor) {
            float defaultScale = viewOffset.ponderer$getDefaultScale();
            if (!Float.isNaN(defaultScale)) {
                accessor.ponderer$setScaleFactor(defaultScale);
            }
        }
        viewOffset.ponderer$setDefaultScale(Float.NaN);
    }

    private static boolean canEdit(Player player) {
        if (player == null)
            return false;
        if (player.isCreative())
            return true;
        for (ItemStack stack : player.getInventory().items) {
            if (BlueprintFeature.matchesCarrierStack(stack))
                return true;
        }
        return false;
    }

    /**
     * True while the user is in the coordinate-pick flow targeting a real block
     * position (POS1 / POS2 / LOOK_AT / POINT for {@link PickState}, or any
     * {@link NbtExpandedPickState} pick). UI-anchor picks are excluded — they
     * have no block under the cursor.
     */
    @Unique
    private static boolean ponderer$isBlockPickActive() {
        if (PickState.isActive() && !PickState.isUiPointPickActive()) {
            return true;
        }
        return NbtExpandedPickState.isActive();
    }

    /**
     * Force {@code PonderIndex.editingModeActive()} to behave as true inside
     * PonderUI during block-pick mode, so Ponder's native editor view shows
     * (axis labels, hovered block coordinates, userMode toggle). Scoped to
     * PonderUI methods only — global mixin would break {@link PonderLocalizationMixin}.
     */
    @Redirect(method = "init",
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/ponder/foundation/PonderIndex;editingModeActive()Z",
            remap = false),
        remap = false, require = 0)
    private boolean ponderer$forceEditingActiveInInit() {
        return PonderIndex.editingModeActive() || ponderer$isBlockPickActive();
    }

    @Redirect(method = "renderScene",
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/ponder/foundation/PonderIndex;editingModeActive()Z",
            remap = false),
        remap = false, require = 0)
    private boolean ponderer$forceEditingActiveInRenderScene() {
        return PonderIndex.editingModeActive() || ponderer$isBlockPickActive();
    }

    @Redirect(method = "renderWidgets",
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/ponder/foundation/PonderIndex;editingModeActive()Z",
            remap = false),
        remap = false, require = 0)
    private boolean ponderer$forceEditingActiveInRenderWidgets() {
        return PonderIndex.editingModeActive() || ponderer$isBlockPickActive();
    }

    @Redirect(method = "renderHoverTooltips",
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/ponder/foundation/PonderIndex;editingModeActive()Z",
            remap = false),
        remap = false, require = 0)
    private boolean ponderer$forceEditingActiveInRenderHoverTooltips() {
        return PonderIndex.editingModeActive() || ponderer$isBlockPickActive();
    }

    /**
     * Compute the 0-based occurrence index of the given PonderScene among all scenes
     * with the same ID in the PonderUI's scene list.
     * This is used to disambiguate when multiple packs register scenes with the same ID.
     */
    private static int ponderer$computeOccurrenceIndex(PonderUI ui, PonderScene target) {
        PonderUIAccessor accessor = (PonderUIAccessor) (Object) ui;
        List<PonderScene> allScenes = accessor.ponderer$getScenes();
        net.minecraft.resources.ResourceLocation targetId = target.getId();
        int occurrence = 0;
        for (PonderScene s : allScenes) {
            if (s == target) return occurrence;
            if (s.getId().equals(targetId)) occurrence++;
        }
        return 0;
    }

    @Unique
    private static SceneRuntime.SceneMatch ponderer$resolveDynamicScene(PonderUI ui) {
        PonderScene active = ui.getActiveScene();
        if (!"ponderer".equals(active.getNamespace())) {
            return null;
        }
        int occurrence = ponderer$computeOccurrenceIndex(ui, active);
        return SceneRuntime.findBySceneId(active.getId(), occurrence);
    }

    @Unique
    private static boolean ponderer$shouldRenderFabricShowInterfaceNotice(PonderUI ui) {
        if (!"fabric".equals(PondererServices.PLATFORM.getPlatformName())) {
            return false;
        }
        if (PondererServices.PLATFORM.supportsEmbeddedInterfacePreview()) {
            return false;
        }
        return ponderer$isShowInterfaceScene(ponderer$resolveDynamicScene(ui));
    }

    @Unique
    private static boolean ponderer$isShowInterfaceScene(SceneRuntime.SceneMatch match) {
        if (match == null) {
            return false;
        }

        DslScene scene = match.scene();
        int sceneIndex = match.sceneIndex();
        if (scene == null || scene.scenes == null || sceneIndex < 0 || sceneIndex >= scene.scenes.size()) {
            return false;
        }

        DslScene.SceneSegment segment = scene.scenes.get(sceneIndex);
        if (segment == null || segment.steps == null) {
            return false;
        }

        for (DslScene.DslStep step : segment.steps) {
            if (step == null || step.type == null || step.type.isBlank()) {
                continue;
            }
            return "show_interface".equalsIgnoreCase(step.type);
        }
        return false;
    }

    // ---- Pick mode integration ----

    @Inject(method = "renderWidgets", at = @At("TAIL"), remap = false)
    private void ponderer$renderFabricShowInterfaceNotice(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        PonderUI self = (PonderUI) (Object) this;
        if (!ponderer$shouldRenderFabricShowInterfaceNotice(self)) {
            return;
        }

        UiAnchorViewport.Rect viewport = UiAnchorViewport.resolve(Minecraft.getInstance());
        if (!viewport.isValid()) {
            return;
        }

        String notice = UIText.of("ponderer.ui.show_interface.fabric_preview_unavailable");
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(notice);
        float maxWidth = (float) Math.max(1.0, viewport.width() - 12.0);
        float scale = textWidth > maxWidth ? Math.max(0.65f, maxWidth / textWidth) : 1.0f;

        int centerX = (int) Math.round(viewport.left() + viewport.width() * 0.5);
        int centerY = (int) Math.round(viewport.top() + viewport.height() * 0.5);
        int boxHalfWidth = (int) Math.ceil(textWidth * scale * 0.5f) + 8;
        int boxHalfHeight = (int) Math.ceil(font.lineHeight * scale * 0.5f) + 6;

        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.EMBEDDED_GUI_BACKGROUND_LAYER);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        graphics.fill(centerX - boxHalfWidth - 1, centerY - boxHalfHeight - 1,
            centerX + boxHalfWidth + 1, centerY + boxHalfHeight + 1, 0xC0_6A5320);
        graphics.fill(centerX - boxHalfWidth, centerY - boxHalfHeight,
            centerX + boxHalfWidth, centerY + boxHalfHeight, 0xE0_120E08);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY - font.lineHeight * scale * 0.5f, 1);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, notice, Math.round(-textWidth * 0.5f), 0, 0xF8E5B0, false);
        graphics.pose().popPose();

        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
        graphics.flush();
    }

    /**
     * At the START of tick: reset identifyMode to false so the scene ticks
     * normally.
     * PonderUI.tick() checks {@code if (!identifyMode) { activeScene.tick(); }} —
     * if identifyMode
     * is true, the scene freezes and the structure never appears.
     * We set it false here so the scene keeps animating, then re-enable it right
     * before
     * updateIdentifiedItem (see below).
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void ponderer$tickPickModeReset(CallbackInfo ci) {
        if (!PickState.isActive() && !NbtExpandedPickState.isActive())
            return;
        if (PickState.isUiPointPickActive())
            return;
        PonderUIAccessor accessor = (PonderUIAccessor) this;
        accessor.ponderer$setIdentifyMode(false);
    }

    /**
     * Right BEFORE updateIdentifiedItem: re-enable identifyMode so that
     * hoveredBlockPos is calculated by the raytrace.
     * After this, identifyMode stays true until the next tick's HEAD resets it.
     * During render (between ticks), identifyMode=true gives a cleaner scene view
     * (no overlays) and enables the native block-highlight tooltip.
     */
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/createmod/ponder/foundation/ui/PonderUI;updateIdentifiedItem(Lnet/createmod/ponder/foundation/PonderScene;)V", remap = false))
    private void ponderer$tickPickModeEnable(CallbackInfo ci) {
        if (!PickState.isActive() && !NbtExpandedPickState.isActive())
            return;
        if (PickState.isUiPointPickActive())
            return;
        PonderUIAccessor accessor = (PonderUIAccessor) this;
        accessor.ponderer$setIdentifyMode(true);
    }

    /**
     * Intercept mouse clicks when pick mode is active.
     * Left-click on a block: pick the block's coordinates.
     * Right-click on a block: pick the adjacent block coordinates (block pos + face
     * normal).
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ponderer$onPickClick(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!PickState.isActive() && !NbtExpandedPickState.isActive())
            return;

        if (PickState.isUiPointPickActive()) {
            if (button == 0) {
                UiAnchorViewport.Rect viewport = UiAnchorViewport.resolve(Minecraft.getInstance());
                double nx = UiAnchorCoords.normalizeX(x - viewport.left(), (int) Math.max(1, viewport.width()));
                double ny = UiAnchorCoords.normalizeY(y - viewport.top(), (int) Math.max(1, viewport.height()));
                PickState.completeUiPick(nx, ny);
                cir.setReturnValue(true);
            }
            return;
        }

        // Both left-click and right-click try to pick a block
        if (button == 0 || button == 1) {
            PonderUIAccessor accessor = (PonderUIAccessor) this;
            // Force identifyMode on and recalculate hoveredBlockPos right now,
            // so we don't depend on tick() timing
            accessor.ponderer$setIdentifyMode(true);
            PonderUI self = (PonderUI) (Object) this;
            self.updateIdentifiedItem(self.getActiveScene());

            BlockPos pos = accessor.ponderer$getHoveredBlockPos();
            if (pos != null) {
                Direction face = ponderer$getHitFace(self.getActiveScene(), pos);
                if (button == 1) {
                    // Right-click: pick the adjacent block (offset by hit face normal)
                    pos = pos.relative(face);
                }
                if (PickState.isActive()) {
                    PickState.completePick(pos, face);
                } else {
                    NbtExpandedPickState.completePick(pos, face);
                }
                cir.setReturnValue(true);
                return;
            }
            // No block hovered: let the click pass through to PonderUI's normal handling
            // so navigation buttons (scene arrows, etc.) still work.
        }
    }

    /**
     * Intercept ESC and Backspace to cancel pick mode and return to the editor.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (InterfaceSlotEditState.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                InterfaceSlotEditState.finishAndReopenEditor();
                return true;
            }
        }
        if (PickState.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                PickState.cancelPick();
                return true;
            }
        }
        if (NbtExpandedPickState.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                NbtExpandedPickState.cancelPick();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Render a pick hint overlay to the right of the cursor showing both click
     * coordinates.
     * Styled with opaque background and border matching editor tooltips.
     * Rendered at the highest z-level to avoid being occluded by
     * structures/tooltips.
     */
    @Inject(method = "renderWidgets", at = @At("TAIL"), remap = false)
    private void ponderer$renderPickHint(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!PickState.isActive() && !NbtExpandedPickState.isActive())
            {
                if (!InterfaceSlotEditState.isActive()) {
                    return;
                }
            }

        var font = Minecraft.getInstance().font;

        // Push to topmost z-level so hint is never occluded by structures or native
        // tooltips
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.TOOLTIP_LAYER);

        if (InterfaceSlotEditState.isActive()) {
            String line1 = UIText.of("ponderer.ui.change_interface_slot.hint.drag");
            String line2 = UIText.of("ponderer.ui.change_interface_slot.hint.exit", InterfaceSlotEditState.bindingCount());
            int w1 = font.width(line1);
            int w2 = font.width(line2);
            int boxW = Math.max(w1, w2) + 8;
            int boxH = 26;
            int tx = mouseX + 10;
            int ty = mouseY - boxH - 17;
            if (tx < 2) tx = 2;
            if (tx + boxW > this.width - 2) tx = this.width - boxW - 2;
            if (ty < 2) ty = 2;

            graphics.fill(tx - 2, ty - 2, tx + boxW + 2, ty + boxH + 2, 0xF0_100020);
            graphics.fill(tx - 1, ty - 1, tx + boxW + 1, ty + boxH + 1, 0xC0_3a7a6a);
            graphics.fill(tx, ty, tx + boxW, ty + boxH, 0xF0_100020);
            graphics.drawString(font, line1, tx + 4, ty + 3, 0x66FFCC);
            graphics.drawString(font, line2, tx + 4, ty + 15, 0xC0C0C0);

            graphics.pose().popPose();
            return;
        }

        if (PickState.isUiPointPickActive()) {
            UiAnchorViewport.Rect viewport = UiAnchorViewport.resolve(Minecraft.getInstance());
            double nx = UiAnchorCoords.normalizeX(mouseX - viewport.left(), (int) Math.max(1, viewport.width()));
            double ny = UiAnchorCoords.normalizeY(mouseY - viewport.top(), (int) Math.max(1, viewport.height()));
            String line1 = String.format("UI锚点 [%.3f, %.3f] 左键选取",
                nx, ny);
            String line2 = "ESC/Backspace 返回";

            int w1 = font.width(line1);
            int w2 = font.width(line2);
            int boxW = Math.max(w1, w2) + 8;
            int boxH = 26;

            int tx = mouseX + 10;
            int ty = mouseY - boxH - 17;
            if (tx < 2) tx = 2;
            if (tx + boxW > this.width - 2) tx = this.width - boxW - 2;
            if (ty < 2) ty = 2;

            graphics.fill(tx - 2, ty - 2, tx + boxW + 2, ty + boxH + 2, 0xF0_100020);
            graphics.fill(tx - 1, ty - 1, tx + boxW + 1, ty + boxH + 1, 0xC0_5040a0);
            graphics.fill(tx, ty, tx + boxW, ty + boxH, 0xF0_100020);
            graphics.drawString(font, line1, tx + 4, ty + 3, 0x66FF66);
            graphics.drawString(font, line2, tx + 4, ty + 15, 0x808080);

            graphics.pose().popPose();
            return;
        }

        PonderUIAccessor accessor = (PonderUIAccessor) this;
        BlockPos pos = accessor.ponderer$getHoveredBlockPos();
        if (pos != null) {
            PonderUI self = (PonderUI) (Object) this;
            Direction face = ponderer$getHitFace(self.getActiveScene(), pos);
            BlockPos adjacent = pos.relative(face);

            String line1, line2;
            boolean halfOffset = PickState.isActive() ? PickState.isHalfOffset() : NbtExpandedPickState.isFloatingTarget();
            if (halfOffset) {
                Direction.Axis faceAxis = face.getAxis();
                line1 = "[ " + ponderer$fmtCoord(pos.getX(), faceAxis != Direction.Axis.X)
                        + ", " + ponderer$fmtCoord(pos.getY(), faceAxis != Direction.Axis.Y)
                        + ", " + ponderer$fmtCoord(pos.getZ(), faceAxis != Direction.Axis.Z)
                        + " ] 左键选取";
                line2 = "[ " + ponderer$fmtCoord(adjacent.getX(), faceAxis != Direction.Axis.X)
                        + ", " + ponderer$fmtCoord(adjacent.getY(), faceAxis != Direction.Axis.Y)
                        + ", " + ponderer$fmtCoord(adjacent.getZ(), faceAxis != Direction.Axis.Z)
                        + " ] 右键选取";
            } else {
                line1 = "[ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " ] 左键选取";
                line2 = "[ " + adjacent.getX() + ", " + adjacent.getY() + ", " + adjacent.getZ() + " ] 右键选取";
            }

            int w1 = font.width(line1);
            int w2 = font.width(line2);
            int boxW = Math.max(w1, w2) + 8;
            int boxH = 26;

            // Position above cursor, centered
            int tx = mouseX + 10;
            int ty = mouseY - boxH - 17;
            // Clamp to screen
            if (tx < 2)
                tx = 2;
            if (tx + boxW > this.width - 2)
                tx = this.width - boxW - 2;
            if (ty < 2)
                ty = 2;

            // Opaque background with border (matching editor tooltip style)
            graphics.fill(tx - 2, ty - 2, tx + boxW + 2, ty + boxH + 2, 0xF0_100020);
            graphics.fill(tx - 1, ty - 1, tx + boxW + 1, ty + boxH + 1, 0xC0_5040a0);
            graphics.fill(tx, ty, tx + boxW, ty + boxH, 0xF0_100020);

            graphics.drawString(font, line1, tx + 4, ty + 3, 0xFFD700);
            graphics.drawString(font, line2, tx + 4, ty + 15, 0x66FF66);
        } else {
            // No block hovered: show minimal instruction above cursor
            String hint = "ESC/Backspace 返回";
            int textW = font.width(hint) + 8;
            int tx = mouseX + 10;
            int ty = mouseY - 31;
            if (tx < 2)
                tx = 2;
            if (tx + textW > this.width - 2)
                tx = this.width - textW - 2;
            if (ty < 2)
                ty = 2;

            graphics.fill(tx - 2, ty - 2, tx + textW + 2, ty + 16, 0xF0_100020);
            graphics.fill(tx - 1, ty - 1, tx + textW + 1, ty + 15, 0xC0_5040a0);
            graphics.fill(tx, ty, tx + textW, ty + 14, 0xF0_100020);
            graphics.drawString(font, hint, tx + 4, ty + 3, 0x808080);
        }

        graphics.pose().popPose();
    }

    /**
     * Force Ponder overlay elements (controls, text pointers, etc.) to render on
     * top of the scene, even with large structures in front.
     */
    @Inject(method = "renderOverlay", at = @At("HEAD"), remap = false)
    private void ponderer$overlayNoDepthPre(GuiGraphics graphics, int i, float partialTicks, CallbackInfo ci) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }

    @Inject(method = "renderOverlay", at = @At("RETURN"), remap = false)
    private void ponderer$overlayNoDepthPost(GuiGraphics graphics, int i, float partialTicks, CallbackInfo ci) {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * Fix the "vertical-line cut through the scene midline" bug that surfaces in PonderUI when
     * scenes are zoomed/translated to extreme values (and in any 3D content rendered over the
     * GUI in general).
     *
     * Root cause: PonderUI pairs {@code matrix4f.translate(0,0,800)} (projection) with
     * {@code poseStack.translate(0,0,-800)} (pose). The +800/-800 *cancel out* in clip space —
     * the net depth of the scene is identical to no-translate-at-all. With Mojang's 1.20.1 GUI
     * ortho (zNear=1000, zFar=11000) and the implicit ModelView z-translate of -10000, this lands
     * the scene at NDC z ≈ 0.76, which is right next to GUI widgets / panel backgrounds drawn at
     * pose z = 0 (NDC z = 0.8). LEQUAL depth tests then tie-fail for half the fragments,
     * producing the characteristic "midline cut" — also visible inside Ponder itself at extreme
     * zoom/move.
     *
     * Fix: override just the pose translate, keep the projection translate alone. With pose-z
     * pushed to +5000, the scene lands at NDC z ≈ -0.4 — well separated from anything at pose z=0
     * (NDC 0.8), and still solidly inside [-1, +1] even after the scene's own scale/rotation
     * extents.
     *
     * Ordinal 0 targets the first {@code PoseStack.translate(F,F,F)} call inside
     * {@code renderScene} (line 632 in upstream Ponder 1.20.1 sources, offset 140 in 1.0.92
     * bytecode). There is only one {@code renderScene} declaration in PonderUI, so the bare
     * method name is unambiguous and avoids depending on cross-loader signature remapping of
     * {@code GuiGraphics}.
     */
    @Redirect(method = "renderScene",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
            ordinal = 0,
            remap = true),
        remap = false)
    private void ponderer$liftSceneOutOfBackgroundDepth(PoseStack ps, float x, float y, float z) {
        ps.translate(x, y, 5000.0f);
    }

    /**
     * Reset pick state if PonderUI is closed while picking unexpectedly.
     */
    @Inject(method = "removed", at = @At("TAIL"))
    private void ponderer$onRemoved(CallbackInfo ci) {
        if (PickState.isActive()) {
            PickState.reset();
        }
        if (NbtExpandedPickState.isActive()) {
            NbtExpandedPickState.reset();
        }
        if (InterfaceSlotEditState.isActive()) {
            InterfaceSlotEditState.reset();
        }
        if (PonderScreenNavigation.consumeSuppressNextPonderReturn()) {
            return;
        }
        // Return to PonderItemGridScreen if it was set as the return target
        if (com.nododiiiii.ponderer.ui.PonderItemGridScreen.returnScreen != null) {
            var ret = com.nododiiiii.ponderer.ui.PonderItemGridScreen.returnScreen;
            com.nododiiiii.ponderer.ui.PonderItemGridScreen.returnScreen = null;
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(ret));
        }
    }

    // ---- Pick mode helpers ----

    /**
     * Format a coordinate: if offset is true, display as int+0.5; otherwise just
     * the integer.
     */
    private static String ponderer$fmtCoord(int value, boolean offset) {
        return offset ? (value + 0.5) + "" : String.valueOf(value);
    }

    /**
     * Determine which face of a block the camera ray hits, using ray-AABB slab
     * intersection.
     * The ray is computed from the current mouse position via the scene's
     * transform.
     */
    private Direction ponderer$getHitFace(PonderScene activeScene, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        double mx = mc.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth();
        double my = mc.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight();

        PonderScene.SceneTransform t = activeScene.getTransform();
        Vec3 from = t.screenToScene(mx, my, 1000, 0);
        Vec3 to = t.screenToScene(mx, my, -100, 0);
        Vec3 dir = to.subtract(from);

        double minX = pos.getX(), minY = pos.getY(), minZ = pos.getZ();
        double maxX = minX + 1, maxY = minY + 1, maxZ = minZ + 1;

        double tMin = Double.NEGATIVE_INFINITY;
        Direction result = Direction.UP;

        // X axis
        if (Math.abs(dir.x) > 1e-10) {
            double t1 = (minX - from.x) / dir.x;
            double t2 = (maxX - from.x) / dir.x;
            double tEnter = Math.min(t1, t2);
            Direction face = (t1 < t2) ? Direction.WEST : Direction.EAST;
            if (tEnter > tMin) {
                tMin = tEnter;
                result = face;
            }
        }

        // Y axis
        if (Math.abs(dir.y) > 1e-10) {
            double t1 = (minY - from.y) / dir.y;
            double t2 = (maxY - from.y) / dir.y;
            double tEnter = Math.min(t1, t2);
            Direction face = (t1 < t2) ? Direction.DOWN : Direction.UP;
            if (tEnter > tMin) {
                tMin = tEnter;
                result = face;
            }
        }

        // Z axis
        if (Math.abs(dir.z) > 1e-10) {
            double t1 = (minZ - from.z) / dir.z;
            double t2 = (maxZ - from.z) / dir.z;
            double tEnter = Math.min(t1, t2);
            Direction face = (t1 < t2) ? Direction.NORTH : Direction.SOUTH;
            if (tEnter > tMin) {
                tMin = tEnter;
                result = face;
            }
        }

        return result;
    }
}
