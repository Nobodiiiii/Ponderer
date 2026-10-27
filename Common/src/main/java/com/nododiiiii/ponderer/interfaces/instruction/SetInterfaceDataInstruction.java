package com.nododiiiii.ponderer.interfaces.instruction;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

import java.util.List;

/**
 * {@code set_interface_data}: writes vanilla data slots, interpolating {@code from -> to} over
 * the step's duration (progress arrows, burn time). Non-blocking, like Ponder's own tweens.
 */
public class SetInterfaceDataInstruction extends TickingInstruction {

    private final List<InterfaceStepSpecs.DataTween> tweens;

    public SetInterfaceDataInstruction(List<InterfaceStepSpecs.DataTween> tweens, int duration) {
        super(false, Math.max(1, duration));
        this.tweens = tweens;
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (ProjectorRenderContext.isActive()) {
            return;
        }
        int elapsed = totalTicks - remainingTicks;
        for (InterfaceStepSpecs.DataTween tween : tweens) {
            int value = InterfaceStepSpecs.tweenValue(tween, elapsed, totalTicks);
            InterfaceHost.script(scene, "data:" + tween.id(), session -> SessionOps.writeData(session, tween.id(), value));
        }
    }
}
