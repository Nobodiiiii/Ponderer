package com.nododiiiii.ponderer.editor.mixin;

import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.runtime.api.SceneCatalog;
import com.nododiiiii.ponderer.editor.capture.PickState;
import com.nododiiiii.ponderer.runtime.viewer.PonderRuntimeZLayers;
import com.nododiiiii.ponderer.editor.screen.PonderScreenNavigation;
import com.nododiiiii.ponderer.editor.screen.FunctionScreen;
import com.nododiiiii.ponderer.editor.screen.PondererDialogScreen;
import com.nododiiiii.ponderer.editor.screen.ReadonlyPackImportPromptScreen;
import com.nododiiiii.ponderer.runtime.viewer.UiAnchorCoords;
import com.nododiiiii.ponderer.runtime.viewer.UiAnchorViewport;
import com.nododiiiii.ponderer.editor.screen.SceneEditorScreen;
import com.nododiiiii.ponderer.editor.capture.InterfaceSlotEditState;
import com.nododiiiii.ponderer.editor.capture.NbtExpandedPickState;
import com.nododiiiii.ponderer.editor.screen.UIText;
import com.nododiiiii.ponderer.editor.screen.widgets.PondererGuiIcons;
import com.mojang.blaze3d.platform.Window;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.nododiiiii.ponderer.runtime.mixin.PonderUIAccessor;

/** Product editor injections; ordinary runtime playback does not install this mixin. */
@Mixin(PonderUI.class)
public abstract class PonderEditorMixin extends AbstractPonderScreen {
    @Unique private PonderButton ponderer$editButton;
    protected PonderEditorMixin() { super(); }

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
                        Minecraft.getInstance().setScreen(new FunctionScreen(source, returnState));
                    }),
                PondererDialogScreen.closeButton(Component.translatable("ponderer.ui.cancel"))))
            .open();
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

    @Unique
    private static boolean ponderer$isBlockPickActive() {
        if (PickState.isActive() && !PickState.isUiPointPickActive()) {
            return true;
        }
        return NbtExpandedPickState.isActive();
    }

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
    private static SceneCatalog.SceneMatch ponderer$resolveDynamicScene(PonderUI ui) {
        PonderScene active = ui.getActiveScene();
        if (!"ponderer".equals(active.getNamespace())) {
            return null;
        }
        int occurrence = ponderer$computeOccurrenceIndex(ui, active);
        return com.nododiiiii.ponderer.bootstrap.ProductRuntime.engine().catalog().findBySceneId(active.getId(), occurrence);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/createmod/ponder/foundation/ui/PonderUI;updateIdentifiedItem(Lnet/createmod/ponder/foundation/PonderScene;)V", remap = false))
    private void ponderer$tickPickModeEnable(CallbackInfo ci) {
        if (!PickState.isActive() && !NbtExpandedPickState.isActive())
            return;
        if (PickState.isUiPointPickActive())
            return;
        PonderUIAccessor accessor = (PonderUIAccessor) this;
        accessor.ponderer$setIdentifyMode(true);
    }

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
            String line2 = "ESC 返回";

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
            String hint = "ESC 返回";
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

    private static String ponderer$fmtCoord(int value, boolean offset) {
        return offset ? (value + 0.5) + "" : String.valueOf(value);
    }

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

    @Inject(method = "init", at = @At("TAIL"))
    private void ponderer$addEditorButton(CallbackInfo ci) {
        PonderUI self = (PonderUI) (Object) this;
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
                .showing(PondererGuiIcons.EDIT_SCENE)
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
                com.nododiiiii.ponderer.application.Workspaces.files().packState().load();
                if (packId != null && !packId.isBlank() && !com.nododiiiii.ponderer.application.Workspaces.files().packState().isImported(packId)) {
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

    @Inject(method = "scroll", at = @At("RETURN"), remap = false)
    private void ponderer$editorFocusOnScroll(boolean forward, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        PonderUI self = (PonderUI) (Object) this;
        var match = ponderer$resolveDynamicScene(self);
        if (match != null) {
            SceneEditorScreen.handlePonderUiFocusChanged(match.scene());
        } else {
            SceneEditorScreen.handlePonderUiFocusChanged(null);
        }
        }

    @Inject(method = "renderHoverTooltips", at = @At("TAIL"))
    private void ponderer$editorTooltip(GuiGraphics graphics, int tooltipColor, CallbackInfo ci) {
        int tooltipY = this.height - 16;
        if (ponderer$editButton != null && ponderer$editButton.isHoveredOrFocused()) {
            graphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("ponderer.ui.tooltip.edit_scene"),
                ponderer$editButton.getX() + 10, tooltipY, tooltipColor);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ponderer$tickPickModeReset(CallbackInfo ci) {
        if (!PickState.isActive() && !NbtExpandedPickState.isActive())
            return;
        if (PickState.isUiPointPickActive())
            return;
        PonderUIAccessor accessor = (PonderUIAccessor) this;
        accessor.ponderer$setIdentifyMode(false);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ponderer$onEditorPickClick(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!PickState.isActive() && !NbtExpandedPickState.isActive()) return;


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

    @Inject(method = "removed", at = @At("TAIL"))
    private void ponderer$onEditorRemoved(CallbackInfo ci) {
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
        if (com.nododiiiii.ponderer.editor.screen.PonderItemGridScreen.returnScreen != null) {
            var ret = com.nododiiiii.ponderer.editor.screen.PonderItemGridScreen.returnScreen;
            // removed() runs inside setScreen(). Defer the return so the outer
            // transition has selected its destination before deciding to leave Ponder.
            Minecraft.getInstance().tell(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof AbstractPonderScreen
                    || com.nododiiiii.ponderer.editor.screen.PonderItemGridScreen.returnScreen != ret) {
                    return;
                }
                com.nododiiiii.ponderer.editor.screen.PonderItemGridScreen.returnScreen = null;
                mc.setScreen(ret);
            });
        }
    }
}
