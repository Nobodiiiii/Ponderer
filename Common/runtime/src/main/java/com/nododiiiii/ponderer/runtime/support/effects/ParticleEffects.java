package com.nododiiiii.ponderer.runtime.support.effects;

import com.nododiiiii.ponderer.scene.step.ParticleEffectSpec;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.runtime.api.PonderActions;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;

public final class ParticleEffects {
    private ParticleEffects() {}

    public static ParticleOptions parse(String particle, HolderLookup.Provider registries) throws CommandSyntaxException {
        StringReader reader = new StringReader(particle.trim());
        ParticleOptions options = ParticleArgument.readParticle(reader, registries);
        reader.skipWhitespace();
        if (reader.canRead()) {
            throw new IllegalArgumentException("Unexpected trailing particle options: " + reader.getRemaining());
        }
        return options;
    }

    public static void emit(SceneBuilder scene, DslScene.DslStep original) {
        DslScene.DslStep step = ParticleEffectSpec.normalize(original);
        ParticleEffectSpec.validate(step);
        PonderActions.spawnParticles(scene, step.particle, vector(step.pos), ParticleEffectSpec.distribution(step),
            vector(ParticleEffectSpec.spread(step)), vector(ParticleEffectSpec.motion(step)),
            ParticleEffectSpec.count(step), ParticleEffectSpec.duration(step));
    }

    private static Vec3 vector(java.util.List<Double> values) {
        return new Vec3(values.get(0), values.get(1), values.get(2));
    }
}
