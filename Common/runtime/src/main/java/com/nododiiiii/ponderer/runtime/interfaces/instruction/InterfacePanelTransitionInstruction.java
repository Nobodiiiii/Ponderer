package com.nododiiiii.ponderer.runtime.interfaces.instruction;

import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

/** Uses the five-tick squared progress of Ponder's FadeInOutInstruction. */
public final class InterfacePanelTransitionInstruction extends TickingInstruction {
    private final boolean entering;
    private SandboxSession session;

    public InterfacePanelTransitionInstruction(boolean entering) {
        super(true, 5);
        this.entering = entering;
    }

    @Override public void reset(PonderScene scene) {
        super.reset(scene);
        session = null;
    }

    @Override protected void firstTick(PonderScene scene) {
        if (!InterfaceHost.isPlaybackSuppressed(scene)) session = InterfaceHost.sessionOf(scene);
    }

    @Override public void tick(PonderScene scene) {
        super.tick(scene);
        if (session == null || InterfaceHost.sessionOf(scene) != session) return;
        float progress = (entering ? totalTicks - remainingTicks : remainingTicks) / (float) totalTicks;
        InterfaceHost.setPanelFade(scene, session, progress * progress);
    }
}
