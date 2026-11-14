package com.nododiiiii.ponderer.runtime.api;

import com.nododiiiii.ponderer.scene.SceneSnapshot;

public record CompileRequest(SceneSnapshot scene, int segmentIndex) {}
