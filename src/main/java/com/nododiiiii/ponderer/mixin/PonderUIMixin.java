package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ui.SceneEditorScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
                ScreenOpener.open(new SceneEditorScreen(result.scene(), result.sceneIndex()));
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
}
