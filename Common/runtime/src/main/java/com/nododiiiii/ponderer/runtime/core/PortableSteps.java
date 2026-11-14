package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.api.PonderActions;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.step.ParticleEffectSpec;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Locale;

/** JSON is only an argument adapter; portable execution belongs to the source shared with Support. */
final class PortableSteps {
    private PortableSteps() {}
    static BlockPos pos(List<Integer> value) { return value == null || value.size() < 3 ? null : new BlockPos(value.get(0), value.get(1), value.get(2)); }
    private static Vec3 vec(List<Double> value) { return value == null || value.size() < 3 ? null : new Vec3(value.get(0), value.get(1), value.get(2)); }
    private static Vec3 point(List<Double> value) { Vec3 v = vec(value); return v == null ? new Vec3(2.5, 1.5, 2.5) : v; }
    static boolean apply(SceneBuilder scene, DslScene.DslStep s, PonderActions.Context c) {
        switch (s.type.toLowerCase(Locale.ROOT)) {
            case "show_structure" -> PonderActions.showStructure(scene, c, pos(s.blockPos), pos(s.blockPos2), s.scale, s.rotation);
            case "create_entity" -> PonderActions.createEntity(scene, c, s.entity, point(s.pos != null ? s.pos : s.point), vec(s.lookAt), s.yaw, s.pitch, s.nbt, s.linkId, s.entranceAnimation, s.entranceDuration, s.direction);
            case "create_item_entity" -> PonderActions.createItemEntity(scene, c, s.item, s.count == null ? 1 : Math.max(1, s.count), point(s.pos != null ? s.pos : s.point), point(s.motion), s.nbt, s.linkId);
            case "rotate_camera_y" -> PonderActions.rotateCameraY(scene, s.degrees == null ? 90 : s.degrees, s.degreesX == null ? 0 : s.degreesX, s.durationOrDefault(20));
            case "highlight_section" -> PonderActions.highlightSection(scene, s.color, pos(s.blockPos), pos(s.blockPos2), s.durationOrDefault(40));
            case "encapsulate_bounds" -> PonderActions.encapsulateBounds(scene, pos(s.bounds));
            case "play_sound" -> PonderActions.playSound(scene, s.sound, s.soundVolume == null ? 1 : s.soundVolume, s.pitch == null ? 1 : s.pitch, s.source);
            case "set_block" -> PonderActions.setBlock(scene, c, s.block, s.blockProperties, pos(s.blockPos), pos(s.blockPos2), s.nbt, s.immediateDisplay, s.spawnParticles, s.entranceAnimation, s.entranceDuration, s.entranceInterval, s.smartDisplay, s.linkId, s.direction);
            case "destroy_block" -> PonderActions.destroyBlock(scene, c, pos(s.blockPos), pos(s.blockPos2), s.destroyParticles);
            case "replace_blocks" -> PonderActions.replaceBlocks(scene, c, s.block, s.blockProperties, pos(s.blockPos), pos(s.blockPos2), s.spawnParticles);
            case "hide_section" -> PonderActions.hideSection(scene, c, pos(s.blockPos), pos(s.blockPos2), s.durationOrDefault(20), s.direction);
            case "show_section_and_merge" -> PonderActions.showSectionAndMerge(scene, c, pos(s.blockPos), pos(s.blockPos2), s.linkId, s.durationOrDefault(20), s.direction, s.entranceAnimation, s.entranceDuration, s.entranceInterval, s.smartDisplay);
            case "rotate_section" -> PonderActions.rotateSection(scene, c, s.linkId, pos(s.blockPos), pos(s.blockPos2), s.rotX == null ? 0 : s.rotX, s.rotY == null ? (s.degrees == null ? 0 : s.degrees) : s.rotY, s.rotZ == null ? 0 : s.rotZ, s.durationOrDefault(20));
            case "move_section" -> PonderActions.moveSection(scene, c, s.linkId, pos(s.blockPos), pos(s.blockPos2), vec(s.offset) == null ? Vec3.ZERO : vec(s.offset), s.durationOrDefault(20));
            case "toggle_redstone_power" -> PonderActions.toggleRedstonePower(scene, pos(s.blockPos), pos(s.blockPos2));
            case "modify_block_entity_nbt" -> PonderActions.modifyBlockEntity(scene, s.blockProperties, s.nbt, s.reDrawBlocks, pos(s.blockPos), pos(s.blockPos2));
            case "indicate_redstone" -> PonderActions.indicateRedstone(scene, pos(s.blockPos));
            case "indicate_success" -> PonderActions.indicateSuccess(scene, pos(s.blockPos));
            case "spawn_particles" -> PonderActions.spawnParticles(scene, s.particle, vec(s.pos), ParticleEffectSpec.distribution(s), vec(ParticleEffectSpec.spread(s)), vec(ParticleEffectSpec.motion(s)), ParticleEffectSpec.count(s), ParticleEffectSpec.duration(s));
            case "clear_entities" -> PonderActions.clearEntities(scene, c, Boolean.TRUE.equals(s.fullScene), s.entity, s.linkId, pos(s.blockPos), pos(s.blockPos2), s.entranceAnimation, s.entranceDuration, s.direction);
            case "clear_item_entities" -> PonderActions.clearItemEntities(scene, c, Boolean.TRUE.equals(s.fullScene), s.item, s.linkId, pos(s.blockPos), pos(s.blockPos2));
            case "modify_entities_nbt" -> PonderActions.modifyEntitiesNbt(scene, c, Boolean.TRUE.equals(s.fullScene), s.entity, s.linkId, s.nbt, pos(s.blockPos), pos(s.blockPos2), vec(s.offset), s.duration, s.walkAnimation);
            case "modify_item_entities_nbt" -> PonderActions.modifyItemEntitiesNbt(scene, c, Boolean.TRUE.equals(s.fullScene), s.item, s.linkId, s.nbt, pos(s.blockPos), pos(s.blockPos2));
            default -> { return false; }
        }
        return true;
    }
}
