package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.platform.CatnipServices;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A passive hover owner; loader screen events draw its tooltip after the complete Ponder UI. */
public final class PonderPreviewTooltip extends AbstractWidget {
    private final PonderUI ui;
    private final PonderFreePreview preview;

    private PonderPreviewTooltip(PonderUI ui, PonderFreePreview preview) {
        super(0, 0, 0, 0, Component.empty());
        this.ui = ui;
        this.preview = preview;
        active = false;
    }

    @Nullable
    public static PonderPreviewTooltip create(Screen screen) {
        if (!(screen instanceof PonderUI ui)) return null;
        PonderFreePreview preview = PonderFreePreview.find(screen);
        return preview == null ? null : new PonderPreviewTooltip(ui, preview);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Ponder's identify tooltip yields to hovered children. Claim only that tooltip;
        // this inactive, unfocusable widget never consumes clicks, keys, or camera drags.
        return preview.isActive();
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Screen's ordinary widget pass runs before scene text, so draw in the post-render event.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        for (var child : screen.children()) {
            if (child instanceof PonderPreviewTooltip tooltip) {
                tooltip.renderTooltip(graphics, mouseX, mouseY);
                return;
            }
        }
    }

    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!preview.isActive() || mouseY >= ui.height - 80
            || ui.children().stream().anyMatch(child -> child != this && child.isMouseOver(mouseX, mouseY))) {
            return;
        }

        PonderScene scene = ui.getActiveScene();
        ItemStack item = pick(scene, mouseX, mouseY);
        Minecraft mc = Minecraft.getInstance();
        graphics.flush();
        graphics.pose().pushPose();
        try {
            // Keep the background and text together above scene text and the raised widget pass.
            graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER + 500);
            if (!item.isEmpty()) {
                graphics.renderTooltip(mc.font, item, mouseX, mouseY);
            } else {
                Component hint = Component.translatable(AbstractPonderScreen.IDENTIFY_MODE,
                    mc.options.keyDrop.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY);
                graphics.renderTooltip(mc.font, mc.font.split(hint, Math.max(1, ui.width / 3)), mouseX, mouseY);
            }
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private ItemStack pick(PonderScene scene, int mouseX, int mouseY) {
        var transform = scene.getTransform();
        Vec3 from = transform.screenToScene(mouseX, mouseY, 1000, preview.partialTicks());
        Vec3 to = transform.screenToScene(mouseX, mouseY, -100, preview.partialTicks());
        List<SectionHit> hits = new ArrayList<>();
        scene.forEach(WorldSectionElement.class, section -> {
            if (!section.isVisible()) return;
            var hit = section.rayTrace(scene.getWorld(), from, to);
            // WorldSectionElement also returns MISS results; they must not mask a real section behind them.
            if (hit == null || hit.getSecond().getType() != HitResult.Type.BLOCK) return;
            hits.add(new SectionHit(hit.getSecond(), from.distanceToSqr(hit.getFirst())));
        });
        SectionHit nearest = hits.stream().min(Comparator.comparingDouble(SectionHit::distance)).orElse(null);
        if (nearest == null) return ItemStack.EMPTY;

        BlockHitResult hit = nearest.hit();
        var world = scene.getWorld();
        var state = world.getBlockState(hit.getBlockPos());
        if (state.isAir()) return ItemStack.EMPTY;
        // Use Q's platform-aware pick-block hook, including schematic blocks on y=0.
        // The native scene picker suppresses every block in the configured base plate.
        return CatnipServices.HOOKS.getCloneItemFromBlockstate(state, hit, world, hit.getBlockPos(),
            Minecraft.getInstance().player);
    }

    private record SectionHit(BlockHitResult hit, double distance) {}
}
