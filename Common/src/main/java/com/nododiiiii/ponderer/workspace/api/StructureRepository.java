package com.nododiiiii.ponderer.workspace.api;

import java.io.InputStream;

/** Built-in resource fallback without runtime or UI ownership. */
public interface StructureRepository {
    InputStream openBuiltinStructure(String path);
    boolean ensureBuiltinStructure(String path);
    boolean isBuiltinStructureName(String name);
}
