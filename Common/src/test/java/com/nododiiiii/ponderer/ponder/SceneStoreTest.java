package com.nododiiiii.ponderer.ponder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SceneStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void collectStructureReferencesIncludesPoolAndNumericRefs() {
        DslScene scene = new DslScene();
        scene.structures = List.of("ponderer:base", "ponderer:alt");

        DslScene.DslStep numericRef = new DslScene.DslStep();
        numericRef.structure = "2";
        DslScene.DslStep directRef = new DslScene.DslStep();
        directRef.structure = "ponderer:override";

        DslScene.SceneSegment segment = new DslScene.SceneSegment();
        segment.steps = List.of(numericRef, directRef);
        scene.scenes = List.of(segment);

        Set<String> refs = new LinkedHashSet<>();
        SceneStore.collectStructureReferences(scene, refs);

        assertEquals(Set.of("ponderer:base", "ponderer:alt", "ponderer:override"), refs);
    }

    @Test
    void parsesExplicitNamespacePathsWithoutDroppingDefaultNamespace() throws Exception {
        Path root = tempDir.resolve("structures");
        Path file = root.resolve("ponderer").resolve("machines").resolve("press.nbt");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1});

        SceneStore.SyncFileRef ref = SceneStore.toServerStructureRef(root, file, ".nbt", null);

        assertNotNull(ref);
        assertEquals("ponderer:machines/press", ref.id());
        assertEquals(null, ref.pack());
    }

    @Test
    void parsesPackScopedStructureRefsWithPackDimension() throws Exception {
        Path root = tempDir.resolve("structures");
        Path file = root.resolve("_packs").resolve("PackA").resolve("[PackA] ponderer").resolve("machines").resolve("press.nbt");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1});

        SceneStore.SyncFileRef ref = SceneStore.toServerStructureRef(root, file, ".nbt", null);

        assertNotNull(ref);
        assertEquals("ponderer:machines/press", ref.id());
        assertEquals("PackA", ref.pack());
    }

    @Test
    void syncMetaKeyKeepsPackDimensionDistinct() {
        assertEquals("scripts/ponderer:foo", SyncMeta.metaKey("scripts", "ponderer:foo", null));
        assertEquals("scripts/[PackA] ponderer:foo", SyncMeta.metaKey("scripts", "ponderer:foo", "PackA"));
    }
}
