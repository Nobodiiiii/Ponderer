package com.nododiiiii.ponderer.gradle

import dev.ithundxr.lotus.gradle.api.asm.LotusGradleASM
import com.nododiiiii.ponderer.gradle.transformers.EnvironmentAnnotationTransformer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

class PondererGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register Transformers
        LotusGradleASM.addTransformer(EnvironmentAnnotationTransformer())

        // Apply lotus plugin
        project.apply(plugin = "dev.ithundxr.lotus.gradle")
    }
}
