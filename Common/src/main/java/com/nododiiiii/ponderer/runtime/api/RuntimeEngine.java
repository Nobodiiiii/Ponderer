package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.runtime.spi.SceneSource;
import net.createmod.ponder.api.registration.PonderPlugin;
import javax.annotation.Nullable;

public interface RuntimeEngine extends AutoCloseable {
    SceneCatalog catalog();
    void replaceCatalog(SceneSource source);
    @Nullable CompilationResult compile(CompileRequest request);
    PonderPlugin plugin();
    @Override void close();
}
