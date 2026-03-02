plugins {
    id("java-gradle-plugin")
    kotlin("jvm") version "1.9.23"
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.ithundxr.dev/releases")
}

gradlePlugin {
    plugins {
        create("pondererPlugin") {
            id = "com.nododiiiii.ponderer.gradle"
            implementationClass = "com.nododiiiii.ponderer.gradle.PondererGradlePlugin"
        }
    }
}

dependencies {
    implementation("org.ow2.asm:asm:${"asm_version"()}")
    implementation("org.ow2.asm:asm-tree:${"asm_version"()}")
    implementation("org.ow2.asm:asm-util:${"asm_version"()}")

    implementation("dev.ithundxr.lotus:lotus-gradle:${"lotus_gradle_version"()}")
}

operator fun String.invoke(): String {
    return rootProject.ext[this] as? String
        ?: throw IllegalStateException("Property $this is not defined")
}
