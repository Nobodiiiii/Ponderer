package com.nododiiiii.ponderer.ponder;

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
        try {
            ParticleOptions options = parse(step.particle, scene.getScene().getWorld().registryAccess());
            Vec3 pos = vector(step.pos);
            Vec3 spread = vector(ParticleEffectSpec.spread(step));
            Vec3 motion = vector(ParticleEffectSpec.motion(step));
            String distribution = ParticleEffectSpec.distribution(step);
            scene.effects().emitParticles(pos, (world, x, y, z) -> {
                var random = world.getRandom();
                Vec3 offset = Vec3.ZERO;
                if ("box".equals(distribution)) {
                    offset = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
                } else if ("sphere".equals(distribution)) {
                    // Uniform inside a unit sphere, then scale to an ellipsoid.
                    double vertical = random.nextDouble() * 2 - 1;
                    double angle = random.nextDouble() * Math.PI * 2;
                    double radius = Math.cbrt(random.nextDouble());
                    double horizontal = Math.sqrt(1 - vertical * vertical);
                    offset = new Vec3(horizontal * Math.cos(angle), vertical, horizontal * Math.sin(angle)).scale(radius);
                }
                world.addParticle(options, x + offset.x * spread.x, y + offset.y * spread.y, z + offset.z * spread.z,
                    motion.x, motion.y, motion.z);
            }, ParticleEffectSpec.count(step), ParticleEffectSpec.duration(step));
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static Vec3 vector(java.util.List<Double> values) {
        return new Vec3(values.get(0), values.get(1), values.get(2));
    }
}
