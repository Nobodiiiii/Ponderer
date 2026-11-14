package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.api.PlaybackUiExtensions;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import org.spongepowered.asm.mixin.Mixin;

/** Shared input bridge remains available without player enhancements. */
@Mixin(value = PonderUI.class, priority = 1200)
public abstract class PlaybackUiExtensionMixin extends AbstractPonderScreen {
    protected PlaybackUiExtensionMixin() { super(); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return PlaybackUiExtensions.keyPressed(this, keyCode, scanCode, modifiers)
            || super.keyPressed(keyCode, scanCode, modifiers);
    }
}
