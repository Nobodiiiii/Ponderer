package com.nododiiiii.ponderer.runtime.spi;

import java.util.List;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Optional host registration, supplied at assembly without linking runtime to product features. */
public interface RegistrationContribution {
    default void beforeTags(PonderTagRegistrationHelper<ResourceLocation> tags) {}
    default void afterTags(PonderTagRegistrationHelper<ResourceLocation> tags) {}
    default void afterScenes(PonderSceneRegistrationHelper<ResourceLocation> scenes) {}
    default List<String> tagsFor(List<String> tags) { return tags == null ? List.of() : tags; }
    RegistrationContribution NONE = new RegistrationContribution() {};
}
