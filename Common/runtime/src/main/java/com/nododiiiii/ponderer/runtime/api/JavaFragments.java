package com.nododiiiii.ponderer.runtime.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Handwritten Java stays outside generated sources and shares the surrounding DSL build context. */
public final class JavaFragments {
    public enum Position { BEFORE, AFTER }
    private record Fragment(String scene, String segment, String anchor, Position position, Consumer<StepBuildContext> body) {}
    private final List<Fragment> fragments = new CopyOnWriteArrayList<>();
    public AutoCloseable register(String sceneId, String segmentId, String stepId, Position position, Consumer<StepBuildContext> body) {
        java.util.Objects.requireNonNull(sceneId);
        java.util.Objects.requireNonNull(segmentId);
        java.util.Objects.requireNonNull(stepId);
        Fragment fragment = new Fragment(sceneId, segmentId, stepId, java.util.Objects.requireNonNull(position), java.util.Objects.requireNonNull(body));
        fragments.add(fragment);
        return () -> fragments.remove(fragment);
    }
    public void build(String segmentId, Position position, StepBuildContext context) {
        for (Fragment fragment : fragments) {
            if (fragment.scene.equals(context.sceneId()) && fragment.segment.equals(segmentId)
                && fragment.anchor.equals(context.stepId()) && fragment.position == position) fragment.body.accept(context);
        }
    }
    public void validate(String sceneId, String segmentId, java.util.Set<String> anchors) {
        for (Fragment fragment : fragments) {
            if (fragment.scene.equals(sceneId) && fragment.segment.equals(segmentId) && !anchors.contains(fragment.anchor))
                throw new IllegalArgumentException(sceneId + "/" + segmentId + ": missing Java fragment anchor " + fragment.anchor);
        }
    }
    public void clear() { fragments.clear(); }
}
