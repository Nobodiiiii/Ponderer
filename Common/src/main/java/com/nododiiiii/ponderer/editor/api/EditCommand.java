package com.nododiiiii.ponderer.editor.api;

import com.nododiiiii.ponderer.scene.DslScene;
import java.util.List;

/** A mutation against the selected segment. The session checkpoints it before execution. */
@FunctionalInterface
public interface EditCommand { void apply(List<DslScene.DslStep> steps); }
