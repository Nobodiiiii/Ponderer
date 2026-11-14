package com.nododiiiii.ponderer.runtime.api;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import java.util.List;
import java.util.Map;

/** Shared links and author-owned state last for one storyboard construction, including replay. */
public record StepBuildContext(SceneBuilder builder, SceneBuildingUtil util,
                               String sceneId, String stepId,
                               Map<String, ElementLink<WorldSectionElement>> sections,
                               Map<String, List<ElementLink<EntityElement>>> entities,
                               Map<String, Object> state, PonderActions.Context actions) {}
