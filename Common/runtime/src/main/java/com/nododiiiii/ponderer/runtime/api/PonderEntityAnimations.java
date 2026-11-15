package com.nododiiiii.ponderer.runtime.api;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Convenience animations backed by the same entity links, spawning and movement as JSON steps. */
public final class PonderEntityAnimations {
    private PonderEntityAnimations() {}

    public static final class EntityAnimContext {
        private final PonderActions.Context actions = new PonderActions.Context();
        private int anonymousId;
    }

    public static EntityAnimContext newEntityAnimContext() {
        return new EntityAnimContext();
    }

    public static void createEntityWithDropIn(SceneBuilder scene, EntityAnimContext context, String entityKey,
                                              String entityId, Vec3 pos, Vec3 lookAt,
                                              Float yaw, Float pitch, String nbt,
                                              double dropHeight, int dropDurationTicks) {
        String key = entityKey == null || entityKey.isBlank() ? "anonymous_" + context.anonymousId++ : entityKey;
        Vec3 start = pos.add(0, dropHeight, 0);
        context.actions.entities().remove(key);
        PonderActions.createEntity(scene, context.actions, entityId, start,
            lookAt == null ? start.add(0, 0, -1) : lookAt, yaw, pitch, nbt, key);
        int duration = Math.max(1, dropDurationTicks);
        move(scene, context, key, new Vec3(0, -dropHeight, 0), duration);
        scene.idle(duration);
    }

    public static void clearEntitiesWithRiseOut(SceneBuilder scene, EntityAnimContext context,
                                               double riseHeight, int riseDurationTicks) {
        if (context.actions.entities().isEmpty()) {
            PonderActions.clearEntities(scene, true, null, null, null);
            return;
        }
        int duration = Math.max(1, riseDurationTicks);
        List<String> keys = List.copyOf(context.actions.entities().keySet());
        for (String key : keys) move(scene, context, key, new Vec3(0, riseHeight, 0), duration);
        scene.idle(duration);
        for (String key : keys) PonderActions.clearEntities(scene, context.actions, false, null, key, null, null);
        context.actions.entities().clear();
    }

    private static void move(SceneBuilder scene, EntityAnimContext context, String key, Vec3 offset, int duration) {
        PonderActions.modifyEntitiesNbt(scene, context.actions, false, null, key, null,
            null, null, offset, duration, false);
    }

    public static void playWalkAnimation(SceneBuilder scene, EntityAnimContext context,
                                         Map<String, Float> speeds, int durationTicks) {
        // Walking in place is independent of translation; keep explicit authored speeds.
        Map<String, Float> captured = new LinkedHashMap<>();
        if (speeds != null) speeds.forEach((key, speed) -> captured.put(key, speed == null ? 0f : speed));
        for (int tick = 0; tick < durationTicks; tick++) {
            captured.forEach((key, speed) -> {
                for (var link : context.actions.entities().getOrDefault(key, List.of())) {
                    scene.world().modifyEntity(link, entity -> {
                        if (entity instanceof LivingEntity living) {
                            living.walkAnimation.setSpeed(speed);
                            living.walkAnimation.update(speed, 1.0f);
                        }
                    });
                }
            });
            scene.idle(1);
        }
    }
}
