package com.nododiiiii.ponderer.exporter;

import com.nododiiiii.ponderer.exporter.JavaModuleExportRequest;
import com.nododiiiii.ponderer.exporter.JavaModuleExportService;
import com.nododiiiii.ponderer.exporter.JavaModuleScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaModuleExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsNeoForgeTemplateMetadataAndBuildsNeoForgeClientEntrypoint() throws Exception {
        Path targetRoot = tempDir.resolve("NeoForge");
        Path javaPackage = targetRoot.resolve("src/main/java/com/example/examplemod");
        Path metadata = targetRoot.resolve("src/main/templates/META-INF/neoforge.mods.toml");
        Files.createDirectories(javaPackage);
        Files.createDirectories(targetRoot.resolve("src/main/resources"));
        Files.createDirectories(metadata.getParent());
        Files.writeString(targetRoot.resolve("gradle.properties"), "mod_id=examplemod\n");
        Files.writeString(metadata, """
            modLoader = "javafml"
            [[mods]]
            modId = "${mod_id}"
            """);
        Files.writeString(javaPackage.resolve("ExampleMod.java"), """
            package com.example.examplemod;

            @Mod("examplemod")
            public final class ExampleMod {
            }
            """);

        JavaModuleScanResult scan = new JavaModuleExportService((id, pack) -> null).scan(
            new JavaModuleExportRequest(targetRoot, Set.of()));

        assertFalse(scan.hasFatalFindings());
        assertNotNull(scan.targetProject);
        assertEquals("neoforge", scan.targetProject.loader);
        assertEquals("examplemod", scan.targetProject.modId);
        assertEquals("com.example.examplemod", scan.targetProject.basePackage);
        assertEquals(metadata, scan.targetProject.metadataPath);

        String clientSource = new JavaModuleExportService((id, pack) -> null).buildNeoForgeClientSource(scan.targetProject);
        assertTrue(clientSource.contains("import net.neoforged.fml.common.EventBusSubscriber;"));
        assertTrue(clientSource.contains("import net.neoforged.bus.api.SubscribeEvent;"));
        assertTrue(clientSource.contains("public final class GeneratedPonderNeoForgeClient"));
        assertTrue(clientSource.contains("@EventBusSubscriber(modid = \"examplemod\""));
        assertFalse(clientSource.contains("EventBusSubscriber.Bus.MOD"));
        assertTrue(clientSource.contains("PonderIndex.addPlugin(new GeneratedPonderPlugin())"));
        assertFalse(clientSource.contains("net.minecraftforge"));

        String attributionSource = new JavaModuleExportService((id, pack) -> null).buildAttributionSource(scan.targetProject);
        assertTrue(attributionSource.contains("ResourceLocation.fromNamespaceAndPath"));
        assertFalse(attributionSource.contains("new ResourceLocation("));

        String locationExpression = new JavaModuleExportService((id, pack) -> null).resourceLocationExpr("minecraft:stone");
        assertEquals("ResourceLocation.fromNamespaceAndPath(\"minecraft\", \"stone\")", locationExpression);
    }

    @Test
    void supportTemplateUsesMinecraft121ItemAndNbtApis() throws Exception {
        try (InputStream input = JavaModuleExportService.class
            .getResourceAsStream("/ponderer/generated_ponder_support.java.template")) {
            assertNotNull(input);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(source.contains("DataComponents.CUSTOM_DATA"));
            assertTrue(source.contains("CustomData.of("));
            assertTrue(source.contains("NbtAccounter.create("));
            assertFalse(source.contains("stack.setTag("));
            assertFalse(source.contains("getOrCreateTag()"));
            assertFalse(source.contains("new NbtAccounter("));
        }
    }
}
