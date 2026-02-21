package com.nododiiiii.ponderer.ponder;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.network.DownloadStructurePayload;
import com.nododiiiii.ponderer.network.UploadScenePayload;
import com.nododiiiii.ponderer.network.SyncRequestPayload;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;

public final class PondererClientCommands {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    private PondererClientCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ponderer")
                .then(Commands.literal("pull")
                    .executes(ctx -> pull("check"))
                    .then(Commands.literal("force")
                        .executes(ctx -> pull("force")))
                    .then(Commands.literal("keep_local")
                        .executes(ctx -> pull("keep_local"))))
                .then(Commands.literal("reload")
                    .executes(ctx -> reloadLocal()))
                .then(Commands.literal("download")
                    .then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(ctx -> download(ResourceLocationArgument.getId(ctx, "id")))))
                .then(Commands.literal("push")
                    .executes(ctx -> pushAll("check"))
                    .then(Commands.literal("force")
                        .executes(ctx -> pushAll("force"))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                            .executes(ctx -> push(ResourceLocationArgument.getId(ctx, "id"), "force"))))
                    .then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(ctx -> push(ResourceLocationArgument.getId(ctx, "id"), "check"))))
                .then(Commands.literal("convert")
                    .then(Commands.literal("to_ponderjs")
                        .then(Commands.literal("all")
                            .executes(ctx -> convertAllToPonderJs()))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                            .executes(ctx -> convertToPonderJs(ResourceLocationArgument.getId(ctx, "id")))))
                    .then(Commands.literal("from_ponderjs")
                        .then(Commands.literal("all")
                            .executes(ctx -> convertAllFromPonderJs()))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                            .executes(ctx -> convertFromPonderJs(ResourceLocationArgument.getId(ctx, "id")))))
                )
                .then(Commands.literal("new")
                    .then(Commands.literal("hand")
                        .executes(ctx -> newSceneFromHand(null))
                        .then(Commands.literal("use_held_nbt")
                            .executes(ctx -> newSceneFromHandWithHeldNbt()))
                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                            .executes(ctx -> newSceneFromHand(CompoundTagArgument.getCompoundTag(ctx, "nbt")))))
                    .then(Commands.argument("item", ResourceLocationArgument.id())
                        .executes(ctx -> newSceneForItem(ResourceLocationArgument.getId(ctx, "item"), null))
                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                            .executes(ctx -> newSceneForItem(ResourceLocationArgument.getId(ctx, "item"), CompoundTagArgument.getCompoundTag(ctx, "nbt"))))))
                .then(Commands.literal("copy")
                    .then(Commands.argument("id", ResourceLocationArgument.id())
                        .then(Commands.argument("target_item", ResourceLocationArgument.id())
                            .executes(ctx -> copyScene(
                                ResourceLocationArgument.getId(ctx, "id"),
                                ResourceLocationArgument.getId(ctx, "target_item"))))))
                .then(Commands.literal("delete")
                    .then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(ctx -> deleteScene(ResourceLocationArgument.getId(ctx, "id"))))
                    .then(Commands.literal("item")
                        .then(Commands.argument("item_id", ResourceLocationArgument.id())
                            .executes(ctx -> deleteScenesForItem(ResourceLocationArgument.getId(ctx, "item_id"))))))
                .then(Commands.literal("list")
                    .executes(ctx -> openItemList()))
                .then(Commands.literal("export")
                    .executes(ctx -> exportPack(null))
                    .then(Commands.argument("filename", StringArgumentType.word())
                        .executes(ctx -> exportPack(StringArgumentType.getString(ctx, "filename")))))
                .then(Commands.literal("import")
                    .then(Commands.argument("filename", StringArgumentType.word())
                        .executes(ctx -> importPack(StringArgumentType.getString(ctx, "filename")))))
        );
    }

    private static int convertToPonderJs(ResourceLocation id) {
        return PonderJsConversionService.convertToPonderJs(id);
    }

    public static int convertAllToPonderJs() {
        return PonderJsConversionService.convertAllToPonderJs();
    }

    private static int convertFromPonderJs(ResourceLocation id) {
        return PonderJsConversionService.convertFromPonderJs(id);
    }

    public static int convertAllFromPonderJs() {
        return PonderJsConversionService.convertAllFromPonderJs();
    }

    private static String pendingPullMode = "check";

    public static int pull(String mode) {
        pendingPullMode = mode;
        PacketDistributor.sendToServer(new SyncRequestPayload());
        notifyClient(Component.translatable("ponderer.cmd.pull.requesting", mode));
        return 1;
    }

    public static String consumePullMode() {
        String mode = pendingPullMode;
        pendingPullMode = "check";
        return mode;
    }

    public static int reloadLocal() {
        int count = SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);
        notifyClient(Component.translatable("ponderer.cmd.reload.done", count));
        return count;
    }

    private static int download(ResourceLocation sourceId) {
        requestStructureDownload(sourceId);
        return 1;
    }

    public static void requestStructureDownload(ResourceLocation sourceId) {
        if (sourceId == null) {
            return;
        }
        PacketDistributor.sendToServer(new DownloadStructurePayload(sourceId.toString()));
        notifyClient(Component.translatable("ponderer.cmd.download.requesting", sourceId.toString()));
    }

    public static int push(ResourceLocation id, String mode) {
        Optional<DslScene> scene = SceneRuntime.getScenes().stream()
            .filter(s -> id.toString().equals(s.id))
            .findFirst();

        if (scene.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.scene_not_found", id.toString()));
            return 0;
        }

        List<UploadScenePayload.StructureEntry> structures = new ArrayList<>();
        DslScene uploadScene = GSON.fromJson(GSON.toJson(scene.get()), DslScene.class);
        remapStructuresForUpload(uploadScene, structures);
        String json = GSON.toJson(uploadScene);

        // Compute lastSyncHash for conflict detection
        String metaKey = "scripts/" + id;
        Map<String, String> meta = SyncMeta.load();
        String lastSyncHash = meta.getOrDefault(metaKey, "");

        PacketDistributor.sendToServer(new UploadScenePayload(id.toString(), json, structures, mode, lastSyncHash));
        notifyClient(Component.translatable("ponderer.cmd.push.uploading", id.toString(), mode));
        return 1;
    }

    private static void remapStructuresForUpload(DslScene scene, List<UploadScenePayload.StructureEntry> uploadEntries) {
        Map<String, String> remapped = new HashMap<>();

        if (scene.structures != null && !scene.structures.isEmpty()) {
            List<String> mapped = new ArrayList<>();
            for (String ref : scene.structures) {
                String updated = remapStructureRef(ref, uploadEntries, remapped);
                mapped.add(updated == null ? ref : updated);
            }
            scene.structures = mapped;
        } else if (scene.structure != null && !scene.structure.isBlank()) {
            String updated = remapStructureRef(scene.structure, uploadEntries, remapped);
            if (updated != null) {
                scene.structure = updated;
            }
        }

        if (scene.scenes != null) {
            for (DslScene.SceneSegment seg : scene.scenes) {
                if (seg == null || seg.steps == null) {
                    continue;
                }
                for (DslScene.DslStep step : seg.steps) {
                    if (step == null || step.structure == null || step.structure.isBlank()) {
                        continue;
                    }
                    if (isNumeric(step.structure.trim())) {
                        continue;
                    }
                    String updated = remapStructureRef(step.structure, uploadEntries, remapped);
                    if (updated != null) {
                        step.structure = updated;
                    }
                }
            }
        }
    }

    private static String remapStructureRef(String ref, List<UploadScenePayload.StructureEntry> uploadEntries,
                                            Map<String, String> remapped) {
        if (ref == null || ref.isBlank()) return null;

        String key = ref.trim();
        if (remapped.containsKey(key)) {
            return remapped.get(key);
        }

        ResourceLocation source = parseStructureLocation(key);
        if (source == null) {
            return null;
        }

        ResourceLocation target = ResourceLocation.fromNamespaceAndPath("ponderer", source.getPath());
        Path sourcePath = findStructureSourcePath(source);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            notifyClient(Component.translatable("ponderer.cmd.push.structure_not_found", source.toString()));
            return source.toString();
        }

        Path targetPath = SceneStore.getStructurePath(target.getPath());
        try {
            byte[] bytes = Files.readAllBytes(sourcePath);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);

            if (uploadEntries.stream().noneMatch(e -> e.id().equals(target.toString()))) {
                uploadEntries.add(new UploadScenePayload.StructureEntry(target.toString(), bytes));
            }

            remapped.put(key, target.toString());
            return target.toString();
        } catch (Exception e) {
            notifyClient(Component.translatable("ponderer.cmd.push.copy_failed", source.toString(), target.toString()));
            return source.toString();
        }
    }

    private static Path findStructureSourcePath(ResourceLocation id) {
        if ("ponderer".equals(id.getNamespace())) {
            return SceneStore.getStructurePath(id.getPath());
        }

        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return null;
        }

        return server.getWorldPath(LevelResource.ROOT)
            .resolve("generated")
            .resolve(id.getNamespace())
            .resolve("structures")
            .resolve(id.getPath() + ".nbt");
    }

    private static ResourceLocation parseStructureLocation(String raw) {
        if (raw.contains(":")) {
            return ResourceLocation.tryParse(raw);
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", raw);
    }

    private static boolean isNumeric(String raw) {
        if (raw == null || raw.isBlank()) return false;
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static int pushAll(String mode) {
        List<DslScene> scenes = SceneRuntime.getScenes();
        if (scenes.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.push.no_scenes"));
            return 0;
        }
        int count = 0;
        for (DslScene scene : scenes) {
            if (scene == null || scene.id == null || scene.id.isBlank()) continue;
            ResourceLocation id = ResourceLocation.tryParse(scene.id);
            if (id == null) continue;
            push(id, mode);
            count++;
        }
        notifyClient(Component.translatable("ponderer.cmd.push.done", count, mode));
        return count;
    }

    // ---- /ponderer new ----

    public static int newSceneFromHand(@Nullable CompoundTag nbt) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.new.no_item"));
            return 0;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        return newSceneForItem(itemId, nbt);
    }

    private static int newSceneFromHandWithHeldNbt() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.new.no_item"));
            return 0;
        }
        net.minecraft.core.RegistryAccess registryAccess = player.registryAccess();
        net.minecraft.nbt.Tag saved = held.save(registryAccess);
        if (!(saved instanceof CompoundTag fullTag)) {
            notifyClient(Component.translatable("ponderer.cmd.new.no_nbt"));
            return 0;
        }
        // Keep only custom components for NBT filter (remove id and count)
        CompoundTag filterTag = fullTag.copy();
        filterTag.remove("count");
        filterTag.remove("id");
        if (filterTag.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.new.no_nbt"));
            return 0;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        return newSceneForItem(itemId, filterTag);
    }

    public static int newSceneForItem(ResourceLocation itemId, @Nullable CompoundTag nbt) {
        String basePath = itemId.getPath();
        String baseId = "ponderer:" + basePath;

        // Find a unique scene id
        String sceneId = baseId;
        int suffix = 0;
        while (sceneExists(sceneId)) {
            suffix++;
            sceneId = baseId + "_" + suffix;
        }

        DslScene scene = new DslScene();
        scene.id = sceneId;
        scene.items = List.of(itemId.toString());
        scene.title = LocalizedText.of("New Scene - " + itemId.getPath());
        scene.structures = List.of("ponderer:basic");
        scene.tags = List.of();
        scene.steps = List.of();
        if (nbt != null) {
            scene.nbtFilter = nbt.toString();
        }

        DslScene.SceneSegment seg = new DslScene.SceneSegment();
        seg.id = "scene_1";
        seg.title = LocalizedText.of("Scene 1");

        DslScene.DslStep showStep = new DslScene.DslStep();
        showStep.type = "show_structure";
        showStep.attachKeyFrame = true;

        DslScene.DslStep idleStep = new DslScene.DslStep();
        idleStep.type = "idle";
        idleStep.duration = 20;

        DslScene.DslStep textStep = new DslScene.DslStep();
        textStep.type = "text";
        textStep.duration = 60;
        textStep.text = LocalizedText.of("Edit this ponder scene!");
        textStep.point = List.of(2.5, 2.0, 2.5);
        textStep.placeNearTarget = true;
        textStep.attachKeyFrame = true;

        seg.steps = List.of(showStep, idleStep, textStep);
        scene.scenes = List.of(seg);

        if (SceneStore.saveSceneToLocal(scene)) {
            SceneStore.reloadFromDisk();
            Minecraft.getInstance().execute(PonderIndex::reload);
            notifyClient(Component.translatable("ponderer.cmd.new.created", sceneId, itemId.toString()));
            return 1;
        } else {
            notifyClient(Component.translatable("ponderer.cmd.new.failed", itemId.toString()));
            return 0;
        }
    }

    private static boolean sceneExists(String id) {
        return SceneRuntime.getScenes().stream().anyMatch(s -> id.equals(s.id));
    }

    // ---- /ponderer copy ----

    public static int copyScene(ResourceLocation sceneId, ResourceLocation targetItem) {
        Optional<DslScene> source = SceneRuntime.getScenes().stream()
            .filter(s -> sceneId.toString().equals(s.id))
            .findFirst();
        if (source.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.scene_not_found", sceneId.toString()));
            return 0;
        }

        DslScene original = source.get();
        String json = GSON.toJson(original);
        DslScene copy = GSON.fromJson(json, DslScene.class);

        // Derive a new scene id from the target item
        String baseId = "ponderer:" + targetItem.getPath();
        String newId = baseId;
        int suffix = 0;
        while (sceneExists(newId)) {
            suffix++;
            newId = baseId + "_" + suffix;
        }

        copy.id = newId;
        copy.items = List.of(targetItem.toString());

        if (SceneStore.saveSceneToLocal(copy)) {
            SceneStore.reloadFromDisk();
            Minecraft.getInstance().execute(PonderIndex::reload);
            notifyClient(Component.translatable("ponderer.cmd.copy.done", sceneId.toString(), newId, targetItem.toString()));
            return 1;
        } else {
            notifyClient(Component.translatable("ponderer.cmd.copy.failed"));
            return 0;
        }
    }

    // ---- /ponderer delete ----

    public static int deleteScene(ResourceLocation sceneId) {
        String id = sceneId.toString();
        Optional<DslScene> target = SceneRuntime.getScenes().stream()
            .filter(s -> id.equals(s.id))
            .findFirst();
        if (target.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.scene_not_found", id));
            return 0;
        }

        if (SceneStore.deleteSceneLocal(id)) {
            SceneStore.reloadFromDisk();
            Minecraft.getInstance().execute(PonderIndex::reload);
            notifyClient(Component.translatable("ponderer.cmd.delete.done", id));
            return 1;
        } else {
            notifyClient(Component.translatable("ponderer.cmd.delete.failed", id));
            return 0;
        }
    }

    public static int deleteScenesForItem(ResourceLocation itemId) {
        String itemStr = itemId.toString();
        List<DslScene> matching = SceneRuntime.getScenes().stream()
            .filter(s -> s.items != null && s.items.contains(itemStr))
            .toList();
        if (matching.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.delete.no_scenes", itemStr));
            return 0;
        }
        int count = 0;
        for (DslScene scene : matching) {
            if (scene.id != null && SceneStore.deleteSceneLocal(scene.id)) {
                count++;
            }
        }
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);
        notifyClient(Component.translatable("ponderer.cmd.delete.item_done", count, itemStr));
        return count;
    }

    // ---- /ponderer list ----

    public static int openItemList() {
        Minecraft.getInstance().execute(() ->
            net.createmod.catnip.gui.ScreenOpener.transitionTo(new com.nododiiiii.ponderer.ui.PonderItemListScreen()));
        return 1;
    }

    // ---- /ponderer export / import ----

    public static int exportPack(@Nullable String filename) {
        if (filename == null || filename.isBlank()) {
            filename = "ponderer_export_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        }
        if (!filename.endsWith(".zip")) filename += ".zip";

        Path baseDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ponderer");
        Path scriptsDir = SceneStore.getSceneDir();
        Path structuresDir = SceneStore.getStructureDir();
        Path outputFile = baseDir.resolve(filename);

        try {
            Files.createDirectories(baseDir);
            int count = 0;
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputFile))) {
                if (Files.exists(scriptsDir)) {
                    try (Stream<Path> paths = Files.walk(scriptsDir)) {
                        for (Path p : paths.filter(Files::isRegularFile).toList()) {
                            String entryName = "scripts/" + scriptsDir.relativize(p).toString().replace("\\", "/");
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                            count++;
                        }
                    }
                }
                if (Files.exists(structuresDir)) {
                    try (Stream<Path> paths = Files.walk(structuresDir)) {
                        for (Path p : paths.filter(Files::isRegularFile).toList()) {
                            String entryName = "structures/" + structuresDir.relativize(p).toString().replace("\\", "/");
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                            count++;
                        }
                    }
                }
            }
            notifyClient(Component.translatable("ponderer.cmd.export.done", count, outputFile.getFileName().toString()));
            return 1;
        } catch (IOException e) {
            notifyClient(Component.translatable("ponderer.cmd.export.failed", e.getMessage()));
            return 0;
        }
    }

    public static int importPack(String filename) {
        if (!filename.endsWith(".zip")) filename += ".zip";

        Path baseDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ponderer");
        Path zipFile = baseDir.resolve(filename);

        if (!Files.exists(zipFile)) {
            notifyClient(Component.translatable("ponderer.cmd.import.not_found", filename));
            return 0;
        }

        Path scriptsDir = SceneStore.getSceneDir();
        Path structuresDir = SceneStore.getStructureDir();

        try {
            int count = 0;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName().replace("\\", "/");
                    Path target;
                    if (name.startsWith("scripts/")) {
                        target = scriptsDir.resolve(name.substring("scripts/".length()));
                    } else if (name.startsWith("structures/")) {
                        target = structuresDir.resolve(name.substring("structures/".length()));
                    } else {
                        continue;
                    }
                    // Security: prevent path traversal
                    if (!target.normalize().startsWith(baseDir.normalize())) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
            SceneStore.reloadFromDisk();
            Minecraft.getInstance().execute(PonderIndex::reload);
            notifyClient(Component.translatable("ponderer.cmd.import.done", count, filename));
            return 1;
        } catch (IOException e) {
            notifyClient(Component.translatable("ponderer.cmd.import.failed", e.getMessage()));
            return 0;
        }
    }

    private static void notifyClient(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }
}