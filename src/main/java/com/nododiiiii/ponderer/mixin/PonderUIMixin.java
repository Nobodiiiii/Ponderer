package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ui.PickState;
import com.nododiiiii.ponderer.ui.SceneEditorScreen;

import com.mojang.blaze3d.platform.Window;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderUI.class)
public abstract class PonderUIMixin extends Screen {

    protected PonderUIMixin() {
        super(CommonComponents.EMPTY);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ponderer$addEditButton(CallbackInfo ci) {
        PonderUI self = (PonderUI) (Object) this;
        PonderScene active = self.getActiveScene();

        // Only show edit button for ponderer dynamic scenes
        if (!"ponderer".equals(active.getNamespace())) {
            return;
        }

        // Check if this scene has a matching DslScene
        var match = SceneRuntime.findBySceneId(active.getId());
        if (match == null) {
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
            var result = SceneRuntime.findBySceneId(active.getId());
            if (result != null) {
                Minecraft.getInstance().setScreen(new SceneEditorScreen(result.scene(), result.sceneIndex()));
            }
        });

        addRenderableWidget(editButton);
    }

    private static boolean canEdit(Player player) {
        if (player == null) return false;
        if (player.isCreative()) return true;
        for (ItemStack stack : player.getInventory().items) {
            if (BlueprintFeature.matchesCarrierStack(stack)) return true;
        }
        return false;
    }

    // ---- Pick mode integration ----

    /**
     * At the START of tick: reset identifyMode to false so the scene ticks normally.
     * PonderUI.tick() checks {@code if (!identifyMode) { activeScene.tick(); }} — if identifyMode
     * is true, the scene freezes and the structure never appears.
     * We set it false here so the scene keeps animating, then re-enable it right before
     * updateIdentifiedItem (see below).
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void ponderer$tickPickModeReset(CallbackInfo ci) {
        if (!PickState.isActive()) return;
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
    @Inject(method = "tick",
            at = @At(value = "INVOKE",
                     target = "Lnet/createmod/ponder/foundation/ui/PonderUI;updateIdentifiedItem(Lnet/createmod/ponder/foundation/PonderScene;)V",
                     remap = false))
    private void ponderer$tickPickModeEnable(CallbackInfo ci) {
        if (!PickState.isActive()) return;
        PonderUIAccessor accessor = (PonderUIAccessor) this;
        accessor.ponderer$setIdentifyMode(true);
    }

    /**
     * Intercept mouse clicks when pick mode is active.
     * Left-click on a block: pick the block's coordinates.
     * Right-click on a block: pick the adjacent block coordinates (block pos + face normal).
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ponderer$onPickClick(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!PickState.isActive()) return;

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
                PickState.completePick(pos, face);
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
        if (PickState.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                PickState.cancelPick();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Render a pick hint overlay to the right of the cursor showing both click coordinates.
     * Styled with opaque background and border matching editor tooltips.
     * Rendered at the highest z-level to avoid being occluded by structures/tooltips.
     */
    @Inject(method = "renderWidgets", at = @At("TAIL"), remap = false)
    private void ponderer$renderPickHint(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!PickState.isActive()) return;

        var font = Minecraft.getInstance().font;

        // Push to topmost z-level so hint is never occluded by structures or native tooltips
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 800);

        PonderUIAccessor accessor = (PonderUIAccessor) this;
        BlockPos pos = accessor.ponderer$getHoveredBlockPos();
        if (pos != null) {
            PonderUI self = (PonderUI) (Object) this;
            Direction face = ponderer$getHitFace(self.getActiveScene(), pos);
            BlockPos adjacent = pos.relative(face);

            String line1, line2;
            if (PickState.isHalfOffset()) {
                Direction.Axis faceAxis = face.getAxis();
                line1 = "[ " + ponderer$fmtCoord(pos.getX(), faceAxis != Direction.Axis.X)
                        + ", " + ponderer$fmtCoord(pos.getY(), faceAxis != Direction.Axis.Y)
                        + ", " + ponderer$fmtCoord(pos.getZ(), faceAxis != Direction.Axis.Z)
                        + " ] \u5de6\u952e\u9009\u53d6";
                line2 = "[ " + ponderer$fmtCoord(adjacent.getX(), faceAxis != Direction.Axis.X)
                        + ", " + ponderer$fmtCoord(adjacent.getY(), faceAxis != Direction.Axis.Y)
                        + ", " + ponderer$fmtCoord(adjacent.getZ(), faceAxis != Direction.Axis.Z)
                        + " ] \u53f3\u952e\u9009\u53d6";
            } else {
                line1 = "[ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " ] \u5de6\u952e\u9009\u53d6";
                line2 = "[ " + adjacent.getX() + ", " + adjacent.getY() + ", " + adjacent.getZ() + " ] \u53f3\u952e\u9009\u53d6";
            }

            int w1 = font.width(line1);
            int w2 = font.width(line2);
            int boxW = Math.max(w1, w2) + 8;
            int boxH = 26;

            // Position above cursor, centered
            int tx = mouseX + 10;
            int ty = mouseY - boxH - 17;
            // Clamp to screen
            if (tx < 2) tx = 2;
            if (tx + boxW > this.width - 2) tx = this.width - boxW - 2;
            if (ty < 2) ty = 2;

            // Opaque background with border (matching editor tooltip style)
            graphics.fill(tx - 2, ty - 2, tx + boxW + 2, ty + boxH + 2, 0xF0_100020);
            graphics.fill(tx - 1, ty - 1, tx + boxW + 1, ty + boxH + 1, 0xC0_5040a0);
            graphics.fill(tx, ty, tx + boxW, ty + boxH, 0xF0_100020);

            graphics.drawString(font, line1, tx + 4, ty + 3, 0xFFD700);
            graphics.drawString(font, line2, tx + 4, ty + 15, 0x66FF66);
        } else {
            // No block hovered: show minimal instruction above cursor
            String hint = "ESC/Backspace \u8fd4\u56de";
            int textW = font.width(hint) + 8;
            int tx = mouseX + 10;
            int ty = mouseY - 31;
            if (tx < 2) tx = 2;
            if (tx + textW > this.width - 2) tx = this.width - textW - 2;
            if (ty < 2) ty = 2;

            graphics.fill(tx - 2, ty - 2, tx + textW + 2, ty + 16, 0xF0_100020);
            graphics.fill(tx - 1, ty - 1, tx + textW + 1, ty + 15, 0xC0_5040a0);
            graphics.fill(tx, ty, tx + textW, ty + 14, 0xF0_100020);
            graphics.drawString(font, hint, tx + 4, ty + 3, 0x808080);
        }

        graphics.pose().popPose();
    }

    /**
     * Reset pick state if PonderUI is closed while picking unexpectedly.
     */
    @Inject(method = "removed", at = @At("TAIL"))
    private void ponderer$onRemoved(CallbackInfo ci) {
        if (PickState.isActive()) {
            PickState.reset();
        }
    }

    // ---- Pick mode helpers ----

    /** Format a coordinate: if offset is true, display as int+0.5; otherwise just the integer. */
    private static String ponderer$fmtCoord(int value, boolean offset) {
        return offset ? (value + 0.5) + "" : String.valueOf(value);
    }

    /**
     * Determine which face of a block the camera ray hits, using ray-AABB slab intersection.
     * The ray is computed from the current mouse position via the scene's transform.
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
            if (tEnter > tMin) { tMin = tEnter; result = face; }
        }

        // Y axis
        if (Math.abs(dir.y) > 1e-10) {
            double t1 = (minY - from.y) / dir.y;
            double t2 = (maxY - from.y) / dir.y;
            double tEnter = Math.min(t1, t2);
            Direction face = (t1 < t2) ? Direction.DOWN : Direction.UP;
            if (tEnter > tMin) { tMin = tEnter; result = face; }
        }

        // Z axis
        if (Math.abs(dir.z) > 1e-10) {
            double t1 = (minZ - from.z) / dir.z;
            double t2 = (maxZ - from.z) / dir.z;
            double tEnter = Math.min(t1, t2);
            Direction face = (t1 < t2) ? Direction.NORTH : Direction.SOUTH;
            if (tEnter > tMin) { tMin = tEnter; result = face; }
        }

        return result;
    }
}
