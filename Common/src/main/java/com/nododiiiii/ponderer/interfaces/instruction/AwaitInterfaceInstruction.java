package com.nododiiiii.ponderer.interfaces.instruction;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.runtime.api.PlaybackExecutionContext;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * {@code await_interface}: enters exercise mode, which holds the timeline until the goal slot
 * holds the wanted item, the player skips, or the timeout runs out. Projectors and sessions
 * that cannot take input skip it, so autoplay never stalls.
 */
public class AwaitInterfaceInstruction extends TickingInstruction {

    private final InterfaceStepSpecs.Goal goal;
    @Nullable
    private final String hint;
    private final int timeout;

    public AwaitInterfaceInstruction(InterfaceStepSpecs.Goal goal, @Nullable String hint, int timeout) {
        super(true, 1);
        this.goal = goal;
        this.hint = hint;
        this.timeout = timeout;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        if (!PlaybackExecutionContext.isActive()) {
            InterfaceHost.beginExercise(scene, goal, hint == null ? null : Component.literal(hint), timeout);
        }
    }
}
