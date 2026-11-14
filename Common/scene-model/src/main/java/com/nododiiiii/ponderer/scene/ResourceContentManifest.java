package com.nododiiiii.ponderer.scene;

import java.util.List;

/** Client resource entry point: assets/<namespace>/ponderer/content/<name>.json. */
public final class ResourceContentManifest {
    public int formatVersion = 1;
    /** Fully qualified resource IDs, including ponderer/scenes/ and .json. */
    public List<String> scenes = List.of();
    /** Optional IDs of compiled providers registered by the host; never Java class names. */
    public List<String> extensions = List.of();
    public boolean attribution;
}
