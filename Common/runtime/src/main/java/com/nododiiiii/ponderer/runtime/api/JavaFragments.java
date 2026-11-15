package com.nododiiiii.ponderer.runtime.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Scene-boundary callbacks need no caller-managed step identity. Inline custom actions use registerJava. */
public final class JavaFragments {
    public enum Position { BEFORE, AFTER }
    private record Fragment(String scene, String segment, Position position, Consumer<StepBuildContext> body) {}
    private final List<Fragment> fragments = new CopyOnWriteArrayList<>();
    public record Description(Position position) {}
    /** Metadata only: the editor never attempts to parse a compiled callback. */
    public List<Description> describe(String sceneId, String segmentId) {
        return fragments.stream().filter(fragment -> fragment.scene.equals(sceneId) && fragment.segment.equals(segmentId))
            .map(fragment -> new Description(fragment.position)).toList();
    }

    public AutoCloseable beforeScene(String sceneId, String segmentId, Consumer<StepBuildContext> body) {
        return register(sceneId, segmentId, Position.BEFORE, body);
    }

    public AutoCloseable afterScene(String sceneId, String segmentId, Consumer<StepBuildContext> body) {
        return register(sceneId, segmentId, Position.AFTER, body);
    }

    private AutoCloseable register(String sceneId, String segmentId, Position position, Consumer<StepBuildContext> body) {
        java.util.Objects.requireNonNull(sceneId);
        java.util.Objects.requireNonNull(segmentId);
        Fragment fragment = new Fragment(sceneId, segmentId, position, java.util.Objects.requireNonNull(body));
        fragments.add(fragment);
        return () -> fragments.remove(fragment);
    }
    public void buildScene(String segmentId, Position position, StepBuildContext context) {
        for (Fragment fragment : fragments) {
            if (fragment.scene.equals(context.sceneId()) && fragment.segment.equals(segmentId)
                && fragment.position == position) fragment.body.accept(context);
        }
    }
    public void clear() { fragments.clear(); }
}
