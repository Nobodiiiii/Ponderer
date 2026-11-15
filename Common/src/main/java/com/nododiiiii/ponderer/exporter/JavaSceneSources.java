package com.nododiiiii.ponderer.exporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.runtime.api.JavaScene;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.scene.SceneStepTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Ordinary Java parameter calls build the same editable document used by playback and preview. */
final class JavaSceneSources {
    private static final Gson CODEC = new GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter()).create();
    private static final Map<String, Method> ACTIONS = Arrays.stream(JavaScene.Step.class.getMethods())
        .filter(method -> method.isAnnotationPresent(JavaScene.Action.class))
        .collect(Collectors.toUnmodifiableMap(method -> method.getAnnotation(JavaScene.Action.class).type(), method -> method));

    private JavaSceneSources() {}

    static String members(DslScene document) {
        var metadata = new JavaValueEmitter(true);
        metadata.fields("document", document, Set.of("scenes"));
        var source = new StringBuilder("    private static DslScene loadDocument() {\n        var document = new DslScene();\n")
            .append(metadata.statements());
        if (document.scenes == null) source.append("        document.scenes = null;\n");
        else {
            source.append(document.scenes.stream().anyMatch(java.util.Objects::isNull)
                ? "        document.scenes = Arrays.asList(" : "        document.scenes = List.of(");
            for (int i = 0; i < document.scenes.size(); i++) {
                if (i > 0) source.append(", ");
                source.append("scene").append(i + 1).append("()");
            }
            source.append(");\n");
        }
        source.append("        return document;\n    }\n\n");
        if (document.scenes != null) for (int i = 0; i < document.scenes.size(); i++) {
            var segment = document.scenes.get(i);
            var values = new JavaValueEmitter(true);
            source.append("    private static DslScene.SceneSegment scene").append(i + 1).append("() {\n");
            if (segment == null) {
                source.append("        return null;\n    }\n\n");
                continue;
            }
            source.append("        var scene = new JavaScene(").append(values.value(segment.id)).append(", ")
                .append(values.value(segment.title)).append(");\n");
            if (segment.steps != null) for (var step : segment.steps) source.append(step(step));
            if (segment.steps == null) source.append("        var segment = scene.build();\n        segment.steps = null;\n        return segment;\n");
            else source.append("        return scene.build();\n");
            source.append("    }\n\n");
        }
        return source.toString();
    }

    private static String step(DslScene.DslStep step) {
        if (step == null) return "        scene.emptyStep();\n";
        var values = new JavaValueEmitter(true);
        JsonObject extra = CODEC.toJsonTree(step).getAsJsonObject();
        extra.remove("id");
        extra.remove("type");
        if (Boolean.TRUE.equals(step.attachKeyFrame)) extra.remove("attachKeyFrame");
        String type = step.type == null ? "" : step.type.toLowerCase(java.util.Locale.ROOT);
        Method action = ACTIONS.get(type);
        String invocation;
        if (action != null && compatible(action, step)) {
            var fields = action.getAnnotation(JavaScene.Action.class).fields();
            var args = new java.util.ArrayList<String>();
            try {
                for (int i = 0; i < fields.length; i++) {
                    Object value = DslScene.DslStep.class.getField(fields[i]).get(step);
                    args.add(argument(action.getParameterTypes()[i], value, values));
                    extra.remove(fields[i]);
                }
            } catch (ReflectiveOperationException error) { throw new IllegalArgumentException("Cannot emit step " + step.id, error); }
            invocation = action.getName() + "(" + String.join(", ", args) + ")";
        } else if (!SceneStepTypes.BUILTIN.contains(type)) {
            extra.remove("parameters");
            invocation = "javaStep(" + values.value(step.type) + ", " + values.value(step.parameters) + ")";
        } else {
            invocation = "raw(" + values.value(step.type) + ", " + values.value(extra) + ")";
            extra = new JsonObject();
        }
        return "        scene"
            + (extra.isEmpty() ? "" : ".fields(" + values.value(extra) + ")")
            + (Boolean.TRUE.equals(step.attachKeyFrame) ? ".keyframe()" : "") + "." + invocation + ";\n";
    }

    private static boolean compatible(Method action, DslScene.DslStep step) {
        String[] fields = action.getAnnotation(JavaScene.Action.class).fields();
        try {
            for (int i = 0; i < fields.length; i++) {
                Class<?> type = action.getParameterTypes()[i];
                Object value = DslScene.DslStep.class.getField(fields[i]).get(step);
                if ((type == BlockPos.class || type == Vec3.class) && value != null
                    && (!(value instanceof List<?> vector) || vector.size() != 3 || vector.stream().anyMatch(java.util.Objects::isNull))) return false;
            }
            return true;
        } catch (ReflectiveOperationException error) { throw new IllegalArgumentException("Invalid JavaScene action " + action, error); }
    }

    private static String argument(Class<?> type, Object value, JavaValueEmitter values) {
        if (value instanceof List<?> vector && (type == BlockPos.class || type == Vec3.class))
            return "new " + type.getSimpleName() + "(" + vector.stream().map(values::value).collect(Collectors.joining(", ")) + ")";
        return values.value(value);
    }
}
