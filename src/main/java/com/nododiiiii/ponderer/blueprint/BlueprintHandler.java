package com.nododiiiii.ponderer.blueprint;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.ponder.enums.PonderSpecialTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Client-side handler for the Blueprint (蓝图与笔) selection tool.
 * Ported from Create's SchematicAndQuillHandler.
 * <p>
 * All selection state lives here on the client -- no server packets needed
 * until the user actually saves.
 */
public class BlueprintHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Object outlineSlot = new Object();

    public BlockPos firstPos;
    public BlockPos secondPos;
    private BlockPos selectedPos;
    private Direction selectedFace;
    private int range = 10;

    // -- scroll -------------------------------------------------------------------

    public boolean mouseScrolled(double delta) {
        if (!isActive())
            return false;
        if (!hasCtrlDown())
            return false;
        if (secondPos == null)
            range = (int) Mth.clamp(range + delta, 1, 100);
        if (selectedFace == null)
            return true;

        AABB bb = new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos));
        Vec3i vec = selectedFace.getNormal();
        Vec3 projectedView = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (bb.contains(projectedView))
            delta *= -1;

        int intDelta = (int) (delta > 0 ? Math.ceil(delta) : Math.floor(delta));

        int x = vec.getX() * intDelta;
        int y = vec.getY() * intDelta;
        int z = vec.getZ() * intDelta;

        AxisDirection axisDirection = selectedFace.getAxisDirection();
        if (axisDirection == AxisDirection.NEGATIVE)
            bb = bb.move(-x, -y, -z);

        double maxX = Math.max(bb.maxX - x * axisDirection.getStep(), bb.minX);
        double maxY = Math.max(bb.maxY - y * axisDirection.getStep(), bb.minY);
        double maxZ = Math.max(bb.maxZ - z * axisDirection.getStep(), bb.minZ);
        bb = new AABB(bb.minX, bb.minY, bb.minZ, maxX, maxY, maxZ);

        firstPos = BlockPos.containing(bb.minX, bb.minY, bb.minZ);
        secondPos = BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ);
        LocalPlayer player = Minecraft.getInstance().player;
        sendStatus(player, Component.translatable("item.ponderer.blueprint.dimensions",
                (int) bb.getXsize() + 1, (int) bb.getYsize() + 1, (int) bb.getZsize() + 1));
        return true;
    }

    // -- right-click --------------------------------------------------------------

    public boolean onMouseInput(int button, boolean pressed) {
        if (!pressed || button != 1)
            return false;
        if (!isActive())
            return false;

        LocalPlayer player = Minecraft.getInstance().player;

        if (player.isShiftKeyDown()) {
            discard();
            return true;
        }

        if (secondPos != null) {
            Minecraft.getInstance().setScreen(new BlueprintPromptScreen());
            return true;
        }

        if (selectedPos == null) {
            sendStatus(player, Component.translatable("item.ponderer.blueprint.no_target"));
            return true;
        }

        if (firstPos != null) {
            secondPos = selectedPos;
            sendStatus(player, Component.translatable("item.ponderer.blueprint.end_set"));
            return true;
        }

        firstPos = selectedPos;
        sendStatus(player, Component.translatable("item.ponderer.blueprint.start_set"));
        return true;
    }

    public void discard() {
        LocalPlayer player = Minecraft.getInstance().player;
        firstPos = null;
        secondPos = null;
        sendStatus(player, Component.translatable("item.ponderer.blueprint.abort"));
    }

    // -- tick ---------------------------------------------------------------------

    public void tick() {
        if (!isActive())
            return;

        LocalPlayer player = Minecraft.getInstance().player;

        // Free-aiming with Ctrl held
        if (hasCtrlDown()) {
            float pt = Minecraft.getInstance().getFrameTime();
            Vec3 targetVec = player.getEyePosition(pt).add(player.getLookAngle().scale(range));
            selectedPos = BlockPos.containing(targetVec);
        } else {
            BlockHitResult trace = RaycastHelper.rayTraceRange(player.level(), player, 75);
            if (trace != null && trace.getType() == Type.BLOCK) {
                BlockPos hit = trace.getBlockPos();
                boolean replaceable = player.level().getBlockState(hit)
                        .canBeReplaced(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, trace)));
                if (trace.getDirection().getAxis().isVertical() && !replaceable)
                    hit = hit.relative(trace.getDirection());
                selectedPos = hit;
            } else {
                selectedPos = null;
            }
        }

        // Determine which face the player is looking at (for scroll-resize)
        selectedFace = null;
        if (secondPos != null) {
            AABB bb = new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos))
                    .expandTowards(1, 1, 1).inflate(.45f);
            Vec3 projectedView = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            boolean inside = bb.contains(projectedView);
            RaycastHelper.PredicateTraceResult result =
                    RaycastHelper.rayTraceUntil(player, 70,
                            pos -> inside ^ bb.contains(VecHelper.getCenterOf(pos)));
            selectedFace = result == null || result.missed() ? null
                    : inside ? result.getFacing().getOpposite() : result.getFacing();
        }

        AABB currentSelectionBox = getCurrentSelectionBox();
        if (currentSelectionBox != null) {
            Outliner.getInstance().chaseAABB(outlineSlot, currentSelectionBox)
                    .colored(0x6886c5)
                    .lineWidth(1 / 16f)
                    .withFaceTexture(PonderSpecialTextures.BLANK)
                    .highlightFace(selectedFace);
        }
    }

    private AABB getCurrentSelectionBox() {
        if (secondPos == null) {
            if (firstPos == null)
                return selectedPos == null ? null : new AABB(selectedPos);
            return selectedPos == null ? new AABB(firstPos)
                    : new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(selectedPos))
                    .expandTowards(1, 1, 1);
        }
        return new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos))
                .expandTowards(1, 1, 1);
    }

    // -- save ---------------------------------------------------------------------

    public void saveBlueprint(String name) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || firstPos == null || secondPos == null) return;

        Path dir = SceneStore.getStructureDir();
        BlueprintExport.ExportResult result = BlueprintExport.saveBlueprint(
                dir, name, true,
                mc.level, firstPos, secondPos
        );
        if (result == null) {
            sendStatus(player, Component.translatable("item.ponderer.blueprint.save_failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Path file = result.file();
        String savedName = file.getFileName().toString();
        if (savedName.endsWith(".nbt")) {
            savedName = savedName.substring(0, savedName.length() - 4);
        }
        String id = "ponderer:" + savedName;
        sendStatus(player, Component.translatable("item.ponderer.blueprint.saved", id)
                .withStyle(ChatFormatting.GREEN));
        firstPos = null;
        secondPos = null;
    }

    // -- helpers ------------------------------------------------------------------

    private boolean isActive() {
        return isPresent() && isBlueprintInHand();
    }

    private boolean isPresent() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.screen == null;
    }

    private boolean isBlueprintInHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return BlueprintFeature.matchesCarrierStack(mc.player.getMainHandItem());
    }

    private static boolean hasCtrlDown() {
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    private static void sendStatus(LocalPlayer player, Component message) {
        if (player != null)
            player.displayClientMessage(message, true);
    }
}
