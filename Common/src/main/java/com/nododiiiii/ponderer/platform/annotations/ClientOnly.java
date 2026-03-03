package com.nododiiiii.ponderer.platform.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method as client-only.
 * The ASM transformer in buildSrc converts this to:
 * - NeoForge: @OnlyIn(Dist.CLIENT)
 * - Fabric: @Environment(EnvType.CLIENT)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface ClientOnly {
}
