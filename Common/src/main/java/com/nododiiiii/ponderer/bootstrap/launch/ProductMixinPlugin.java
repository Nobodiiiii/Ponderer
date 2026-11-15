package com.nododiiiii.ponderer.bootstrap.launch;

import com.nododiiiii.ponderer.runtime.api.RuntimeFeatures;
import com.nododiiiii.ponderer.runtime.launch.RuntimeMixinPlugin;

/** Only product Mixins are routed here; standalone Lib never knows their class names. */
public abstract class ProductMixinPlugin extends RuntimeMixinPlugin {
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return switch (simple) {
            case "InterfaceExtendedCaptureMixin", "InterfaceCaptureMixin", "InterfaceItemCaptureMixin",
                 "InterfaceBlockCaptureMixin", "InterfaceLevelCaptureMixin", "InterfaceMenuCaptureMixin" ->
                ProductFeatures.enabled(ProductFeature.EDITOR) && RuntimeFeatures.interfacesEnabled();
            case "MinecraftNbtPickMixin", "MinecraftCoordPickScrollMixin", "PonderEditorMixin" -> ProductFeatures.enabled(ProductFeature.EDITOR);
            case "PonderSceneRegistryMixin", "PonderSceneRegistryMixinFabric", "PonderLocalizationMixin",
                 "MultiLineEditBoxAccessor", "MultilineTextFieldAccessor", "PonderKeybindsAccessor" -> ProductFeatures.enabled(ProductFeature.APPLICATION);
            case "GuiFeedbackAccessor" -> ProductFeatures.enabled(ProductFeature.FEEDBACK);
            default -> throw new IllegalArgumentException("Missing product feature rule for Mixin " + mixinClassName);
        };
    }
}
