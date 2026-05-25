package com.nododiiiii.ponderer.ponder;

import java.util.*;

/**
 * M2: StepEmitterRegistry -- converts DslScene.DslStep to PonderJS code fragments.
 * Each step type maps to a function that produces one or more JS lines.
 */
public final class PonderJsEmitters {

    @FunctionalInterface
    interface StepEmitter {
        /** Emit JS code for the given step. Return null if cannot handle. */
        String emit(DslScene.DslStep step, EmitContext ctx);
    }

    public static class EmitContext {
        public final Map<String, String> sectionLinks = new HashMap<>();
        private int linkCounter = 0;

        public String nextLinkVar() {
            return "link" + (++linkCounter);
        }
    }

    private static final Map<String, StepEmitter> EMITTERS = new LinkedHashMap<>();

    static {
        EMITTERS.put("show_structure", PonderJsEmitters::emitShowStructure);
        EMITTERS.put("show_extra_structure", PonderJsEmitters::emitShowExtraStructure);
        EMITTERS.put("idle", PonderJsEmitters::emitIdle);
        EMITTERS.put("text", PonderJsEmitters::emitText);
        EMITTERS.put("shared_text", PonderJsEmitters::emitSharedText);
        EMITTERS.put("create_entity", PonderJsEmitters::emitCreateEntity);
        EMITTERS.put("create_item_entity", PonderJsEmitters::emitCreateItemEntity);
        EMITTERS.put("rotate_camera_y", PonderJsEmitters::emitRotateCameraY);
        EMITTERS.put("show_controls", PonderJsEmitters::emitShowControls);
        EMITTERS.put("encapsulate_bounds", PonderJsEmitters::emitEncapsulateBounds);
        EMITTERS.put("play_sound", PonderJsEmitters::emitPlaySound);
        EMITTERS.put("set_block", PonderJsEmitters::emitSetBlock);
        EMITTERS.put("destroy_block", PonderJsEmitters::emitDestroyBlock);
        EMITTERS.put("replace_blocks", PonderJsEmitters::emitReplaceBlocks);
        EMITTERS.put("hide_section", PonderJsEmitters::emitHideSection);
        EMITTERS.put("show_section_and_merge", PonderJsEmitters::emitShowSectionAndMerge);
        EMITTERS.put("rotate_section", PonderJsEmitters::emitRotateSection);
        EMITTERS.put("move_section", PonderJsEmitters::emitMoveSection);
        EMITTERS.put("toggle_redstone_power", PonderJsEmitters::emitToggleRedstonePower);
        EMITTERS.put("modify_block_entity_nbt", PonderJsEmitters::emitModifyBlockEntityNbt);
        EMITTERS.put("indicate_redstone", PonderJsEmitters::emitIndicateRedstone);
        EMITTERS.put("indicate_success", PonderJsEmitters::emitIndicateSuccess);
        EMITTERS.put("next_scene", PonderJsEmitters::emitNextScene);
    }

    public static String emit(DslScene.DslStep step, EmitContext ctx) {
        if (step == null || step.type == null) return null;
        StepEmitter emitter = EMITTERS.get(step.type.toLowerCase(Locale.ROOT));
        if (emitter == null) return null;
        return emitter.emit(step, ctx);
    }

    // -- emitters ----------------------------------------------------------------

    private static String emitShowStructure(DslScene.DslStep step, EmitContext ctx) {
        String line;
        if (step.height != null && step.height > 0) {
            line = "scene.showStructure(" + step.height + ");";
        } else {
            line = "scene.showStructure();";
        }
        if (step.scale != null) {
            line += "\nscene.scaleSceneView(" + fmtFloat(step.scale) + ");";
        }
        return line;
    }

    private static String emitShowExtraStructure(DslScene.DslStep step, EmitContext ctx) {
        // PonderJS has no concept of layered NBT structure overlays + air filtering;
        // emit an explicit warning comment rather than a misleading approximation.
        String ref = step.structure == null ? "?" : step.structure;
        return "// UNSUPPORTED show_extra_structure (structure=" + ref + "): PonderJS cannot reproduce this step";
    }

    private static String emitIdle(DslScene.DslStep step, EmitContext ctx) {
        int dur = step.durationOrDefault(20);
        if (dur % 20 == 0 && dur >= 20) {
            return "scene.idleSeconds(" + (dur / 20) + ");";
        }
        return "scene.idle(" + dur + ");";
    }

    private static String emitText(DslScene.DslStep step, EmitContext ctx) {
        int dur = step.durationOrDefault(60);
        String text = step.text == null ? "" : escapeJs(step.text.resolve());
        StringBuilder sb = new StringBuilder();
        sb.append("scene\n    .text(").append(dur).append(", \"").append(text).append("\"");
        if (step.point != null && step.point.size() >= 3) {
            sb.append(", [").append(fmtDoubles(step.point)).append("]");
        }
        sb.append(")");
        if (step.color != null && !step.color.isBlank()) {
            sb.append("\n    .colored(PonderPalette.").append(step.color.toUpperCase(Locale.ROOT)).append(")");
        }
        if (Boolean.TRUE.equals(step.placeNearTarget)) {
            sb.append("\n    .placeNearTarget()");
        }
        if (Boolean.TRUE.equals(step.attachKeyFrame)) {
            sb.append("\n    .attachKeyFrame()");
        }
        sb.append(";");
        return sb.toString();
    }

    private static String emitSharedText(DslScene.DslStep step, EmitContext ctx) {
        int dur = step.durationOrDefault(60);
        String key = step.key == null ? "" : escapeJs(step.key);
        StringBuilder sb = new StringBuilder();
        sb.append("scene\n    .sharedText(").append(dur).append(", \"").append(key).append("\"");
        if (step.point != null && step.point.size() >= 3) {
            sb.append(", [").append(fmtDoubles(step.point)).append("]");
        }
        sb.append(")");
        if (step.color != null && !step.color.isBlank()) {
            sb.append("\n    .colored(PonderPalette.").append(step.color.toUpperCase(Locale.ROOT)).append(")");
        }
        if (Boolean.TRUE.equals(step.placeNearTarget)) {
            sb.append("\n    .placeNearTarget()");
        }
        if (Boolean.TRUE.equals(step.attachKeyFrame)) {
            sb.append("\n    .attachKeyFrame()");
        }
        sb.append(";");
        return sb.toString();
    }

    private static String emitCreateEntity(DslScene.DslStep step, EmitContext ctx) {
        String entity = step.entity == null ? "minecraft:pig" : escapeJs(step.entity);
        String pos = step.pos != null && step.pos.size() >= 3 ? fmtDoubles(step.pos) : "0, 1, 0";
        String varName = step.linkId == null ? ctx.nextLinkVar() : step.linkId;
        return "const " + varName + " = scene.world.createEntity(\"" + entity + "\", [" + pos + "]);";
    }

    private static String emitCreateItemEntity(DslScene.DslStep step, EmitContext ctx) {
        String item = step.item == null ? "minecraft:stone" : escapeJs(step.item);
        String pos = step.pos != null && step.pos.size() >= 3 ? fmtDoubles(step.pos) : "0, 1, 0";
        String motion = step.motion != null && step.motion.size() >= 3 ? fmtDoubles(step.motion) : "0, 0.1, 0";
        return "scene.world.createItemEntity([" + pos + "], [" + motion + "], \"" + item + "\");";
    }

    private static String emitRotateCameraY(DslScene.DslStep step, EmitContext ctx) {
        float deg = step.degrees == null ? 90f : step.degrees;
        int duration = step.durationOrDefault(20);
        String prefix = "";
        if (step.degreesX != null && step.degreesX != 0f) {
            prefix = "// note: X-axis camera rotation (" + fmtFloat(step.degreesX)
                + " deg) is not supported by Ponder's KubeJS API and was skipped\n";
        }
        if (duration <= 0) {
            return prefix + "scene.rotateCameraY(" + fmtFloat(deg) + ");";
        }
        return prefix + "scene.rotateCameraY(" + fmtFloat(deg) + ");\nscene.idle(" + duration + ");";
    }

    private static String emitShowControls(DslScene.DslStep step, EmitContext ctx) {
        int dur = step.durationOrDefault(60);
        String pos = step.point != null && step.point.size() >= 3 ? fmtDoubles(step.point) : "2.5, 2, 2.5";
        String dir = step.direction == null ? "down" : escapeJs(step.direction);
        StringBuilder sb = new StringBuilder();
        sb.append("scene\n    .showControls(").append(dur).append(", [").append(pos).append("], \"").append(dir).append("\")");
        if ("left".equalsIgnoreCase(step.action)) {
            sb.append("\n    .leftClick()");
        } else if ("right".equalsIgnoreCase(step.action)) {
            sb.append("\n    .rightClick()");
        } else if ("scroll".equalsIgnoreCase(step.action)) {
            sb.append("\n    .scroll()");
        }
        if (step.item != null && !step.item.isBlank()) {
            sb.append("\n    .withItem(\"").append(escapeJs(step.item)).append("\")");
        }
        if (Boolean.TRUE.equals(step.whileSneaking)) {
            sb.append("\n    .whileSneaking()");
        }
        if (Boolean.TRUE.equals(step.whileCTRL)) {
            sb.append("\n    .whileCTRL()");
        }
        sb.append(";");
        return sb.toString();
    }

    private static String emitEncapsulateBounds(DslScene.DslStep step, EmitContext ctx) {
        if (step.bounds == null || step.bounds.size() < 3) return "// encapsulate_bounds: missing bounds";
        return "scene.encapsulateBounds([" + fmtInts(step.bounds) + "]);";
    }

    private static String emitPlaySound(DslScene.DslStep step, EmitContext ctx) {
        String sound = step.sound == null ? "minecraft:entity.experience_orb.pickup" : escapeJs(step.sound);
        StringBuilder sb = new StringBuilder();
        sb.append("scene.playSound(\"").append(sound).append("\"");
        if (step.source != null) sb.append(", \"").append(escapeJs(step.source)).append("\"");
        if (step.soundVolume != null) sb.append(", ").append(fmtFloat(step.soundVolume));
        if (step.pitch != null) sb.append(", ").append(fmtFloat(step.pitch));
        sb.append(");");
        return sb.toString();
    }

    private static String emitSetBlock(DslScene.DslStep step, EmitContext ctx) {
        String block = step.block == null ? "minecraft:stone" : escapeJs(step.block);
        boolean particles = Boolean.TRUE.equals(step.spawnParticles);
        if (step.blockPos2 != null && step.blockPos2.size() >= 3 && step.blockPos != null && step.blockPos.size() >= 3) {
            // If both positions are identical, use single-block setBlock
            if (step.blockPos.equals(step.blockPos2)) {
                return "scene.world.setBlock([" + fmtInts(step.blockPos) + "], \"" + block + "\", " + particles + ");";
            }
            String coords = fmtInts(step.blockPos) + ", " + fmtInts(step.blockPos2);
            return "scene.world.setBlocks(util.select.fromTo(" + coords + "), \"" + block + "\", " + particles + ");";
        }
        if (step.blockPos == null || step.blockPos.size() < 3) return "// set_block: missing blockPos";
        return "scene.world.setBlock([" + fmtInts(step.blockPos) + "], \"" + block + "\", " + particles + ");";
    }

    private static String emitDestroyBlock(DslScene.DslStep step, EmitContext ctx) {
        if (step.blockPos == null || step.blockPos.size() < 3) return "// destroy_block: missing blockPos";
        boolean particles = !Boolean.FALSE.equals(step.destroyParticles);
        if (step.blockPos2 == null || step.blockPos2.size() < 3 || step.blockPos.equals(step.blockPos2)) {
            if (particles) {
                return "scene.world.destroyBlock([" + fmtInts(step.blockPos) + "]);";
            }
            return "scene.world.setBlock([" + fmtInts(step.blockPos) + "], \"minecraft:air\", false);";
        }
        if (!particles) {
            // No particles → equivalent to filling the range with air, which the Create JS API
            // already supports as a single call.
            String coords = fmtInts(step.blockPos) + ", " + fmtInts(step.blockPos2);
            return "scene.world.setBlocks(util.select.fromTo(" + coords + "), \"minecraft:air\", false);";
        }
        // Particle destroy is single-position only in the Create API; emit a loop.
        int x1 = step.blockPos.get(0), y1 = step.blockPos.get(1), z1 = step.blockPos.get(2);
        int x2 = step.blockPos2.get(0), y2 = step.blockPos2.get(1), z2 = step.blockPos2.get(2);
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
        return "for (let dx = " + minX + "; dx <= " + maxX + "; dx++)"
            + " for (let dy = " + minY + "; dy <= " + maxY + "; dy++)"
            + " for (let dz = " + minZ + "; dz <= " + maxZ + "; dz++)"
            + " scene.world.destroyBlock([dx, dy, dz]);";
    }

    private static String emitReplaceBlocks(DslScene.DslStep step, EmitContext ctx) {
        String block = step.block == null ? "minecraft:stone" : escapeJs(step.block);
        boolean particles = Boolean.TRUE.equals(step.spawnParticles);
        if (step.blockPos2 != null && step.blockPos2.size() >= 3 && step.blockPos != null && step.blockPos.size() >= 3) {
            String coords = fmtInts(step.blockPos) + ", " + fmtInts(step.blockPos2);
            return "scene.world.replaceBlocks(util.select.fromTo(" + coords + "), \"" + block + "\", " + particles + ");";
        }
        if (step.blockPos == null || step.blockPos.size() < 3) return "// replace_blocks: missing blockPos";
        return "scene.world.replaceBlocks(util.select.position(" + fmtInts(step.blockPos) + "), \"" + block + "\", " + particles + ");";
    }

    private static String emitHideSection(DslScene.DslStep step, EmitContext ctx) {
        String dir = step.direction == null ? "up" : escapeJs(step.direction);
        if (step.blockPos2 != null && step.blockPos2.size() >= 3 && step.blockPos != null && step.blockPos.size() >= 3) {
            String coords = fmtInts(step.blockPos) + ", " + fmtInts(step.blockPos2);
            return "scene.world.hideSection(util.select.fromTo(" + coords + "), \"" + dir + "\");";
        }
        if (step.blockPos == null || step.blockPos.size() < 3) return "// hide_section: missing blockPos";
        return "scene.world.hideSection([" + fmtInts(step.blockPos) + "], \"" + dir + "\");";
    }

    private static String emitShowSectionAndMerge(DslScene.DslStep step, EmitContext ctx) {
        String dir = step.direction == null ? "up" : escapeJs(step.direction);
        String linkId = step.linkId == null ? ctx.nextLinkVar() : step.linkId;
        if (step.blockPos2 != null && step.blockPos2.size() >= 3 && step.blockPos != null && step.blockPos.size() >= 3) {
            String coords = fmtInts(step.blockPos) + ", " + fmtInts(step.blockPos2);
            return "const " + linkId + " = scene.world.showIndependentSection(util.select.fromTo(" + coords + "), \"" + dir + "\");";
        }
        if (step.blockPos == null || step.blockPos.size() < 3) return "// show_section_and_merge: missing blockPos";
        return "scene.world.showSectionAndMerge([" + fmtInts(step.blockPos) + "], \"" + dir + "\", " + linkId + ");";
    }

    private static String emitRotateSection(DslScene.DslStep step, EmitContext ctx) {
        String linkId = step.linkId == null ? "link" : step.linkId;
        float rx = step.rotX == null ? 0 : step.rotX;
        float ry = step.rotY == null ? 0 : step.rotY;
        float rz = step.rotZ == null ? 0 : step.rotZ;
        int dur = step.durationOrDefault(20);
        return "scene.world.rotateSection(" + linkId + ", " + fmtFloat(rx) + ", " + fmtFloat(ry) + ", " + fmtFloat(rz) + ", " + dur + ");";
    }

    private static String emitMoveSection(DslScene.DslStep step, EmitContext ctx) {
        String linkId = step.linkId == null ? "link" : step.linkId;
        String offset = step.offset != null && step.offset.size() >= 3 ? fmtDoubles(step.offset) : "0, 0, 0";
        int dur = step.durationOrDefault(20);
        return "scene.world.moveSection(" + linkId + ", [" + offset + "], " + dur + ");";
    }

    private static String emitToggleRedstonePower(DslScene.DslStep step, EmitContext ctx) {
        // PonderJS does not expose toggleRedstonePower - emit as comment
        if (step.blockPos2 != null && step.blockPos2.size() >= 3 && step.blockPos != null && step.blockPos.size() >= 3) {
            String coords = fmtInts(step.blockPos) + ", " + fmtInts(step.blockPos2);
            return "// UNSUPPORTED_IN_PONDERJS: scene.world.toggleRedstonePower(util.select.fromTo(" + coords + "));";
        }
        if (step.blockPos == null || step.blockPos.size() < 3) return "// toggle_redstone_power: missing blockPos";
        return "// UNSUPPORTED_IN_PONDERJS: scene.world.toggleRedstonePower([" + fmtInts(step.blockPos) + "]);";
    }

    private static String emitModifyBlockEntityNbt(DslScene.DslStep step, EmitContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("scene.world.modifyBlockEntityNBT(");
        if (step.blockPos2 != null && step.blockPos2.size() >= 3 && step.blockPos != null && step.blockPos.size() >= 3) {
            sb.append("util.select.fromTo(").append(fmtInts(step.blockPos)).append(", ").append(fmtInts(step.blockPos2)).append(")");
        } else if (step.blockPos != null && step.blockPos.size() >= 3) {
            sb.append("util.select.position(").append(fmtInts(step.blockPos)).append(")");
        } else {
            return "// modify_block_entity_nbt: missing blockPos";
        }
        // Always use 2-arg form: (Selection, Consumer). Rhino cannot resolve 'true' literal
        // in the 3-arg overload (Selection, boolean, Consumer) due to class lookup issues.
        sb.append(", (nbt) => {");
        if (step.nbt != null && !step.nbt.isBlank()) {
            sb.append(" nbt.merge(NBT.toTagCompound(NBT.parseTag(\"").append(escapeJs(step.nbt)).append("\"))); ");
        }
        sb.append("});");
        return sb.toString();
    }

    private static String emitIndicateRedstone(DslScene.DslStep step, EmitContext ctx) {
        if (step.blockPos == null || step.blockPos.size() < 3) return "// indicate_redstone: missing blockPos";
        return "// UNSUPPORTED_IN_PONDERJS: scene.effects.indicateRedstone([" + fmtInts(step.blockPos) + "]);";
    }

    private static String emitIndicateSuccess(DslScene.DslStep step, EmitContext ctx) {
        if (step.blockPos == null || step.blockPos.size() < 3) return "// indicate_success: missing blockPos";
        return "// UNSUPPORTED_IN_PONDERJS: scene.effects.indicateSuccess([" + fmtInts(step.blockPos) + "]);";
    }

    private static String emitNextScene(DslScene.DslStep step, EmitContext ctx) {
        return "// next_scene";
    }

    // -- utility -----------------------------------------------------------------

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String fmtDoubles(List<Double> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            double v = list.get(i);
            if (v == (int) v) {
                sb.append((int) v);
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    private static String fmtInts(List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static String fmtFloat(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.valueOf(v);
    }
}
