package com.nododiiiii.ponderer.scene.step;

import com.nododiiiii.ponderer.scene.DslScene;
import java.util.List;
import java.util.Locale;

/** Shared particle defaults, legacy conversion and validation for editors and exporters. */
public final class ParticleEffectSpec {
    public static final List<String> DISTRIBUTIONS = List.of("point", "box", "sphere");
    public static final int MAX_COUNT = 4096;
    public static final int MAX_DURATION = 72000;
    public static final List<Double> ZERO = List.of(0.0, 0.0, 0.0);
    public static final List<Double> BLOCK_SPREAD = List.of(0.5, 0.5, 0.5);

    public record Preset(String key, String particle, String distribution, List<Double> spread,
                         List<Double> motion, int count, int duration) {
        public DslScene.DslStep step() {
            DslScene.DslStep step = new DslScene.DslStep();
            step.type = "spawn_particles";
            step.particle = particle;
            step.distribution = distribution;
            step.spread = spread;
            step.motion = motion;
            step.count = count;
            step.duration = duration;
            return step;
        }
    }

    // PonderSceneBuilder#createRedstoneParticles and Create's Fan, Processing,
    // Deployer and CartAssembler scenes (ref/1.21.1/); no Create runtime dependency.
    public static final List<Preset> PRESETS = List.of(
        new Preset("success", "minecraft:dust{color:[0.5019608f,1.0f,0.6666667f],scale:1.0f}", "box", BLOCK_SPREAD, ZERO, 10, 2),
        new Preset("redstone", "minecraft:dust{color:[1.0f,0.0f,0.0f],scale:1.0f}", "box", BLOCK_SPREAD, ZERO, 10, 2),
        new Preset("smoke", "minecraft:large_smoke", "point", ZERO, ZERO, 1, 60),
        new Preset("spit", "minecraft:spit", "point", ZERO, ZERO, 1, 60),
        new Preset("item", "minecraft:item{item:{id:\"minecraft:iron_ingot\",count:1}}", "box", BLOCK_SPREAD, ZERO, 10, 1),
        new Preset("block", "minecraft:block{block_state:{Name:\"minecraft:pink_wool\"}}", "box", BLOCK_SPREAD, ZERO, 10, 1)
    );

    private ParticleEffectSpec() {}

    /** Leaves the original scene untouched; legacy effects keep their exact block-space defaults. */
    public static DslScene.DslStep normalize(DslScene.DslStep step) {
        if (!"indicate_success".equalsIgnoreCase(step.type) && !"indicate_redstone".equalsIgnoreCase(step.type)) {
            return step;
        }
        DslScene.DslStep converted = PRESETS.get("indicate_success".equalsIgnoreCase(step.type) ? 0 : 1).step();
        if (step.blockPos != null && step.blockPos.size() == 3) {
            converted.pos = step.blockPos.stream().map(value -> value + 0.5).toList();
        }
        converted.attachKeyFrame = step.attachKeyFrame;
        return converted;
    }

    public static String distribution(DslScene.DslStep step) {
        return step.distribution == null ? "point" : step.distribution.toLowerCase(Locale.ROOT);
    }

    public static int count(DslScene.DslStep step) { return step.count == null ? 10 : step.count; }
    public static int duration(DslScene.DslStep step) { return step.duration == null ? 1 : step.duration; }
    public static List<Double> spread(DslScene.DslStep step) { return step.spread == null ? ZERO : step.spread; }
    public static List<Double> motion(DslScene.DslStep step) { return step.motion == null ? ZERO : step.motion; }

    public static void validate(DslScene.DslStep step) {
        if (step.particle == null || step.particle.isBlank()) {
            throw new IllegalArgumentException("particle is required");
        }
        vector(step.pos, "pos", false);
        vector(spread(step), "spread", true);
        vector(motion(step), "motion", false);
        if (!DISTRIBUTIONS.contains(distribution(step))) {
            throw new IllegalArgumentException("distribution must be point, box or sphere");
        }
        if (count(step) < 0 || count(step) > MAX_COUNT) {
            throw new IllegalArgumentException("count must be between 0 and " + MAX_COUNT);
        }
        if (duration(step) < 1 || duration(step) > MAX_DURATION) {
            throw new IllegalArgumentException("duration must be between 1 and " + MAX_DURATION);
        }
    }

    private static void vector(List<Double> vector, String name, boolean nonNegative) {
        if (vector == null || vector.size() != 3 || vector.stream().anyMatch(value ->
                value == null || !Double.isFinite(value) || (nonNegative && value < 0))) {
            throw new IllegalArgumentException(name + " must contain three finite " + (nonNegative ? "non-negative " : "") + "numbers");
        }
    }
}
