package com.nododiiiii.ponderer.gradle.transformers

import dev.ithundxr.lotus.gradle.api.asm.util.IClassTransformer
import dev.ithundxr.lotus.gradle.api.asm.util.SubprojectType
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

class EnvironmentAnnotationTransformer : IClassTransformer {
    companion object {
        private val annotations = mapOf(
            SubprojectType.FABRIC to "Lnet/fabricmc/api/Environment;",
            SubprojectType.FORGE to "Lnet/minecraftforge/api/distmarker/OnlyIn;",
            SubprojectType.NEOFORGE to "Lnet/neoforged/api/distmarker/OnlyIn;",
        )

        private val annotationValues = mapOf(
            SubprojectType.FABRIC to "Lnet/fabricmc/api/EnvType;",
            SubprojectType.FORGE to "Lnet/minecraftforge/api/distmarker/Dist;",
            SubprojectType.NEOFORGE to "Lnet/neoforged/api/distmarker/Dist;",
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun transform(project: SubprojectType, node: ClassNode) {
        node.visibleAnnotations = node.visibleAnnotations ?: mutableListOf()
        node.invisibleAnnotations = node.invisibleAnnotations ?: mutableListOf()
        process(project, node.visibleAnnotations, node.invisibleAnnotations)

        for (fieldNode in node.fields) {
            fieldNode.visibleAnnotations = fieldNode.visibleAnnotations ?: mutableListOf()
            fieldNode.invisibleAnnotations = fieldNode.invisibleAnnotations ?: mutableListOf()
            process(project, fieldNode.visibleAnnotations, fieldNode.invisibleAnnotations)
        }

        for (methodNode in node.methods) {
            methodNode.visibleAnnotations = methodNode.visibleAnnotations ?: mutableListOf()
            methodNode.invisibleAnnotations = methodNode.invisibleAnnotations ?: mutableListOf()
            process(project, methodNode.visibleAnnotations, methodNode.invisibleAnnotations)
        }
    }

    private fun process(project: SubprojectType, visibleAnnotations: MutableList<AnnotationNode>, invisibleAnnotations: MutableList<AnnotationNode>) {
        val allAnnotationNodes = mutableListOf<AnnotationNode>()
        visibleAnnotations.let { allAnnotationNodes.addAll(it) }
        invisibleAnnotations.let { allAnnotationNodes.addAll(it) }

        val annotationsList = if (project == SubprojectType.FABRIC) invisibleAnnotations else visibleAnnotations
        val descriptor = annotations[project]!!
        val enumDesc = annotationValues[project]!!

        for (annotation in allAnnotationNodes) {
            if (annotation.desc == "Lcom/nododiiiii/ponderer/platform/annotations/ClientOnly;") {
                val newAnnotation = AnnotationNode(descriptor)
                newAnnotation.values = mutableListOf<Any>()
                newAnnotation.values.add("value")
                newAnnotation.values.add(arrayOf(enumDesc, "CLIENT"))

                annotationsList.add(newAnnotation)
                invisibleAnnotations.remove(annotation)
            }
        }
    }
}
