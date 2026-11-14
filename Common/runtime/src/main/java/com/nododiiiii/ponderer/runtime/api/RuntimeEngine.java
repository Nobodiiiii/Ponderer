package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.runtime.spi.SceneSource;
import net.createmod.ponder.api.registration.PonderPlugin;
import javax.annotation.Nullable;

public interface RuntimeEngine extends AutoCloseable {
    StepRegistry steps();
    JavaFragments fragments();
    net.createmod.ponder.api.scene.PonderStoryBoard storyboard(com.nododiiiii.ponderer.scene.SceneSnapshot scene, int segmentIndex);
    SceneProgram program(String sceneId, String segmentId, net.createmod.ponder.api.scene.SceneBuilder builder, net.createmod.ponder.api.scene.SceneBuildingUtil util);
    SceneCatalog catalog();
    void replaceCatalog(SceneSource source);
    @Nullable CompilationResult compile(CompileRequest request);
    PonderPlugin plugin();
    @Override void close();
}
