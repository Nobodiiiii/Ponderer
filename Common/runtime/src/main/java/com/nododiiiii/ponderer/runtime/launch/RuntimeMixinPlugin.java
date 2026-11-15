package com.nododiiiii.ponderer.runtime.launch;

import com.nododiiiii.ponderer.runtime.api.RuntimeFeatures;
import com.nododiiiii.ponderer.runtime.api.RuntimeFeature;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Every configured Mixin has an explicit capability rule. Unknown hooks fail closed. */
public class RuntimeMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (simple.startsWith("Sandbox")) return enabled(RuntimeFeature.INTERFACES);
        return switch (simple) {
            case "PonderUIMixin", "PonderPlaybackMixin",
                 "LerpedFloatInterpolationMixin", "WorldSectionInterpolationMixin" -> enabled(RuntimeFeature.PLAYER);
            case "PonderProgressBarMixin" -> enabled(RuntimeFeature.TIMELINE);
            case "PonderSceneViewOffsetMixin", "SceneTransformOffsetMixin" -> enabled(RuntimeFeature.VIEW);
            case "PonderSceneTextIndexMixin" -> enabled(RuntimeFeature.TEXT);
            case "PonderUINbtMixin", "PonderUINbtMixinFabric", "PonderSceneRegistryNbtMixin",
                 "PonderSceneRegistryNbtMixinFabric", "PonderTooltipNbtMixin", "PonderTooltipNbtMixinFabric" -> enabled(RuntimeFeature.NBT_BINDINGS);
            case "WorldSectionRenderCacheMixin" -> enabled(RuntimeFeature.RENDER_CACHE);
            case "AnimationTickHolderMixin", "TextWindowElementAccessor", "InputWindowElementAccessor" -> enabled(RuntimeFeature.DETACHED_RENDERING);
            case "RenderSystemShaderLightsAccessor" -> enabled(RuntimeFeature.SHADER_LIGHTS);
            case "PlaybackUiExtensionMixin" -> enabled(RuntimeFeature.UI_EXTENSIONS) && !enabled(RuntimeFeature.PLAYER);
            case "PonderLevelMixin", "PonderSceneLocalizedTextMixin", "PonderStructureSourceMixin", "WorldSectionElementImplMixin",
                 "PonderLevelModelDataMixin", "PonderSceneAccessor", "TickingInstructionAccessor", "PonderUIAccessor" -> enabled(RuntimeFeature.CORE);
            default -> throw new IllegalArgumentException("Missing startup feature rule for Mixin " + mixinClassName);
        };
    }
    private static boolean enabled(RuntimeFeature feature) { return RuntimeFeatures.enabled(feature); }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String name, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String name, IMixinInfo info) {}
}
