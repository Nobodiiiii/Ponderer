package com.nododiiiii.ponderer.ponder;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.network.DownloadStructurePayload;
import com.nododiiiii.ponderer.network.UploadScenePayload;
import com.nododiiiii.ponderer.network.SyncRequestPayload;
import com.nododiiiii.ponderer.projector.client.ProjectorClientCaches;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import com.mojang.brigadier.CommandDispatcher;
import com.nododiiiii.ponderer.platform.PondererServices;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;

public final class PondererClientCommands {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
            .create();

    private PondererClientCommands() {
    }

    private record SourcePackScene(DslScene scene) {
    }

    private record SourcePackContents(Map<String, SourcePackScene> scenes, Map<String, byte[]> structures) {
    }

    public static void register(CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(
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
                                                .executes(ctx -> push(ResourceLocationArgument.getId(ctx, "id"),
                                                        "force"))))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(ctx -> push(ResourceLocationArgument.getId(ctx, "id"), "check"))))
                        .then(Commands.literal("convert")
                                .then(Commands.literal("to_ponderjs")
                                        .then(Commands.literal("all")
                                                .executes(ctx -> convertAllToPonderJs()))
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .executes(ctx -> convertToPonderJs(
                                                        ResourceLocationArgument.getId(ctx, "id")))))
                                .then(Commands.literal("from_ponderjs")
                                        .then(Commands.literal("all")
                                                .executes(ctx -> convertAllFromPonderJs()))
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .executes(ctx -> convertFromPonderJs(
                                                        ResourceLocationArgument.getId(ctx, "id"))))))
                        .then(Commands.literal("new")
                                .then(Commands.literal("hand")
                                        .executes(ctx -> newSceneFromHand(null))
                                        .then(Commands.literal("use_held_nbt")
                                                .executes(ctx -> newSceneFromHandWithHeldNbt()))
                                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                .executes(ctx -> newSceneFromHand(
                                                        CompoundTagArgument.getCompoundTag(ctx, "nbt")))))
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> newSceneForItem(ResourceLocationArgument.getId(ctx, "item"),
                                                null))
                                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                .executes(ctx -> newSceneForItem(
                                                        ResourceLocationArgument.getId(ctx, "item"),
                                                        CompoundTagArgument.getCompoundTag(ctx, "nbt"))))))
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
                                                .executes(ctx -> deleteScenesForItem(
                                                        ResourceLocationArgument.getId(ctx, "item_id"))))))
                        .then(Commands.literal("list")
                                .executes(ctx -> openItemList()))
                        .then(Commands.literal("export")
                                .executes(ctx -> openExportScreen()))
                        .then(Commands.literal("import")
                                .executes(ctx -> openImportScreen()))
                        .then(Commands.literal("unregister_pack")
                                .then(Commands.argument("pack_name", StringArgumentType.greedyString())
                                        .executes(ctx -> unregisterPack(
                                                StringArgumentType.getString(ctx, "pack_name"))))));
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
        PondererServices.NETWORK.sendToServer(new SyncRequestPayload());
        notifyClient(Component.translatable("ponderer.cmd.pull.requesting", mode));
        return 1;
    }

    public static String consumePullMode() {
        String mode = pendingPullMode;
        pendingPullMode = "check";
        return mode;
    }

    public static int reloadLocal() {
        SceneStore.AutoLoadResult autoLoadResult = SceneStore.autoLoadPonderPacks();
        int count = SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);
        for (SceneStore.PackUpdateInfo info : autoLoadResult.updatedPacks) {
            notifyClient(Component.literal("[Ponderer] ")
                .append(Component.translatable("ponderer.pack.update.readonly_newer_source",
                    info.packName, info.newVersion, info.oldVersion)));
        }
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
        PondererServices.NETWORK.sendToServer(new DownloadStructurePayload(sourceId.toString()));
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

        return pushScene(scene.get(), mode);
    }

    /**
     * Push a scene identified by its scene key.
     * Key format: "ponderer:example" or "[my_pack] ponderer:example"
     */
    public static int pushByKey(String sceneKey, String mode) {
        DslScene scene = SceneRuntime.findByKey(sceneKey);
        if (scene == null) {
            notifyClient(Component.translatable("ponderer.cmd.scene_not_found", sceneKey));
            return 0;
        }
        return pushScene(scene, mode);
    }

    public static int pushSourcePack(String packName, String mode) {
        String targetPack = packName == null ? "" : packName.trim();
        if (targetPack.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.push.pack_not_found", ""));
            return 0;
        }

        PonderPackInfo info = findSourcePack(targetPack);
        if (info == null || info.sourcePath == null || !Files.exists(info.sourcePath)) {
            notifyClient(Component.translatable("ponderer.cmd.push.pack_not_found", targetPack));
            return 0;
        }

        SourcePackContents contents;
        try {
            contents = readSourcePackForUpload(info);
        } catch (IOException e) {
            notifyClient(Component.translatable("ponderer.cmd.push.pack_read_failed", info.name, e.getMessage()));
            return 0;
        }

        if (contents.scenes().isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.push.pack_no_scenes", info.name));
            return 0;
        }

        String pushMode = mode == null || mode.isBlank() ? "check" : mode;
        int count = 0;
        int structureCount = 0;
        for (SourcePackScene sourceScene : contents.scenes().values()) {
            DslScene scene = sourceScene.scene();
            List<UploadScenePayload.StructureEntry> structures = sourcePackStructureEntries(
                info.name, scene, contents.structures());
            structureCount += structures.size();
            PondererServices.NETWORK.sendToServer(
                new UploadScenePayload(scene.id, info.name, GSON.toJson(scene), structures, pushMode, ""));
            notifyClient(Component.translatable("ponderer.cmd.push.uploading",
                SceneStore.displaySceneKey(scene.id, info.name), pushMode));
            count++;
        }

        notifyClient(Component.translatable("ponderer.cmd.push.pack_done",
            count, structureCount, info.name, pushMode));
        return count;
    }

    @Nullable
    private static PonderPackInfo findSourcePack(String packName) {
        for (PonderPackInfo info : SceneStore.scanAvailableSourcePacks()) {
            if (info != null && packName.equals(info.name)) {
                return info;
            }
        }
        return null;
    }

    private static SourcePackContents readSourcePackForUpload(PonderPackInfo info) throws IOException {
        Map<String, SourcePackScene> scenes = new LinkedHashMap<>();
        Map<String, byte[]> structures = new LinkedHashMap<>();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(info.sourcePath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = normalizeZipEntryName(entry.getName());
                if (name.startsWith("data/ponderer/scripts/") && name.endsWith(".json")) {
                    DslScene scene = readSourcePackScene(zis, info.name);
                    if (scene != null && scene.id != null && !scene.id.isBlank()) {
                        scenes.put(scene.id, new SourcePackScene(scene));
                    }
                    continue;
                }

                if (name.startsWith("data/ponderer/structures/") && name.endsWith(".nbt")) {
                    String fileName = name.substring("data/ponderer/structures/".length());
                    ResourceLocation id = sourcePackStructureId(fileName);
                    if (id != null) {
                        structures.putIfAbsent(id.toString(), zis.readAllBytes());
                    }
                }
            }
        }

        return new SourcePackContents(scenes, structures);
    }

    @Nullable
    private static DslScene readSourcePackScene(InputStream input, String packName) throws IOException {
        try {
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            DslScene scene = GSON.fromJson(json, DslScene.class);
            if (scene == null) {
                return null;
            }
            scene.pack = packName;
            return scene;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<UploadScenePayload.StructureEntry> sourcePackStructureEntries(String packName,
                                                                                     DslScene scene,
                                                                                     Map<String, byte[]> structures) {
        List<UploadScenePayload.StructureEntry> entries = new ArrayList<>();
        Set<String> refs = new HashSet<>();
        SceneStore.collectStructureReferences(scene, refs);

        for (String ref : refs) {
            ResourceLocation id = sourcePackStructureRefId(ref);
            if (id == null) {
                continue;
            }
            String key = id.toString();
            byte[] bytes = findSourcePackStructureBytes(structures, id);
            if (bytes == null) {
                notifyClient(Component.translatable("ponderer.cmd.push.structure_not_found", key));
                continue;
            }
            entries.add(new UploadScenePayload.StructureEntry(key, packName, bytes));
        }
        return entries;
    }

    @Nullable
    private static byte[] findSourcePackStructureBytes(Map<String, byte[]> structures, ResourceLocation id) {
        byte[] bytes = structures.get(id.toString());
        if (bytes != null) {
            return bytes;
        }
        String fallbackKey = Ponderer.MODID + ":" + id.getPath();
        bytes = structures.get(fallbackKey);
        if (bytes != null) {
            return bytes;
        }
        for (Map.Entry<String, byte[]> entry : structures.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.endsWith(":" + id.getPath())) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Nullable
    private static ResourceLocation sourcePackStructureRefId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.matches("\\d+")) {
            return null;
        }
        if (trimmed.endsWith(".nbt")) {
            trimmed = trimmed.substring(0, trimmed.length() - ".nbt".length());
        }
        if (trimmed.contains(":")) {
            return ResourceLocation.tryParse(trimmed);
        }
        return ResourceLocation.tryParse(Ponderer.MODID + ":" + trimmed);
    }

    @Nullable
    private static ResourceLocation sourcePackStructureId(String fileName) {
        String cleanName = normalizeZipEntryName(fileName);
        if (cleanName.isBlank() || cleanName.endsWith("/") || !cleanName.endsWith(".nbt")) {
            return null;
        }
        if (cleanName.equals("..") || cleanName.startsWith("../") || cleanName.contains("/../")) {
            return null;
        }

        String idPath = cleanName.substring(0, cleanName.length() - ".nbt".length());
        String namespace = Ponderer.MODID;
        String path = idPath;
        int slash = idPath.indexOf('/');
        if (slash > 0) {
            namespace = idPath.substring(0, slash);
            path = idPath.substring(slash + 1);
        }
        if (namespace.isBlank() || path.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(namespace + ":" + path);
    }

    private static String normalizeZipEntryName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static int pushScene(DslScene scene, String mode) {
        List<UploadScenePayload.StructureEntry> structures = new ArrayList<>();
        String sourcePack = scene.pack;
        String uploadPack = null;
        DslScene uploadScene = GSON.fromJson(GSON.toJson(scene), DslScene.class);
        uploadScene.pack = uploadPack;
        remapStructuresForUpload(uploadScene, sourcePack, uploadPack, structures);
        String json = GSON.toJson(uploadScene);

        // Compute lastSyncHash for conflict detection
        String metaKey = SyncMeta.metaKey("scripts", scene.id, uploadPack);
        Map<String, String> meta = SyncMeta.load();
        String lastSyncHash = meta.getOrDefault(metaKey, "");

        PondererServices.NETWORK
                .sendToServer(new UploadScenePayload(scene.id, uploadPack, json, structures, mode, lastSyncHash));
        notifyClient(Component.translatable("ponderer.cmd.push.uploading",
                SceneStore.displaySceneKey(scene.id, uploadPack), mode));
        return 1;
    }

    private static void remapStructuresForUpload(DslScene scene, @Nullable String sourcePack,
            @Nullable String uploadPack,
            List<UploadScenePayload.StructureEntry> uploadEntries) {
        Map<String, String> remapped = new HashMap<>();

        if (scene.structures != null && !scene.structures.isEmpty()) {
            List<String> mapped = new ArrayList<>();
            for (String ref : scene.structures) {
                String updated = remapStructureRef(ref, sourcePack, uploadPack, uploadEntries, remapped);
                mapped.add(updated == null ? ref : updated);
            }
            scene.structures = mapped;
        } else if (scene.structure != null && !scene.structure.isBlank()) {
            String updated = remapStructureRef(scene.structure, sourcePack, uploadPack, uploadEntries, remapped);
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
                    String updated = remapStructureRef(step.structure, sourcePack, uploadPack, uploadEntries, remapped);
                    if (updated != null) {
                        step.structure = updated;
                    }
                }
            }
        }
    }

    private static String remapStructureRef(String ref, @Nullable String sourcePack, @Nullable String uploadPack,
            List<UploadScenePayload.StructureEntry> uploadEntries,
            Map<String, String> remapped) {
        if (ref == null || ref.isBlank())
            return null;

        String key = ref.trim();
        if (remapped.containsKey(key)) {
            return remapped.get(key);
        }

        ResourceLocation source = parseStructureLocation(key);
        if (source == null) {
            return null;
        }

        ResourceLocation target = ResourceLocation.fromNamespaceAndPath("ponderer", source.getPath());
        Path sourcePath = findStructureSourcePath(source, sourcePack);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            notifyClient(Component.translatable("ponderer.cmd.push.structure_not_found", source.toString()));
            return source.toString();
        }

        Path targetPath = SceneStore.resolveLocalSyncStructurePath(target, uploadPack);
        if (targetPath == null) {
            notifyClient(Component.translatable("ponderer.cmd.push.copy_failed", source.toString(), target.toString()));
            return source.toString();
        }
        try {
            byte[] bytes = Files.readAllBytes(sourcePath);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);

            if (uploadEntries.stream().noneMatch(e -> e.id().equals(target.toString())
                    && java.util.Objects.equals(e.pack(), uploadPack))) {
                uploadEntries.add(new UploadScenePayload.StructureEntry(target.toString(), uploadPack, bytes));
            }

            remapped.put(key, target.toString());
            return target.toString();
        } catch (Exception e) {
            notifyClient(Component.translatable("ponderer.cmd.push.copy_failed", source.toString(), target.toString()));
            return source.toString();
        }
    }

    private static Path findStructureSourcePath(ResourceLocation id, @Nullable String pack) {
        if ("ponderer".equals(id.getNamespace())) {
            if (pack != null && !pack.isBlank()) {
                Path packPath = SceneStore.resolveLocalSyncStructurePath(id, pack);
                if (packPath != null && Files.exists(packPath)) {
                    return packPath;
                }
            }
            return SceneStore.getStructurePath(id);
        }

        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return null;
        }

        return SafePaths.resolveRelativePath(
                server.getWorldPath(LevelResource.ROOT).resolve("generated"),
                id.getNamespace() + "/structures/" + id.getPath() + ".nbt");
    }

    private static ResourceLocation parseStructureLocation(String raw) {
        if (raw.contains(":")) {
            return ResourceLocation.tryParse(raw);
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", raw);
    }

    private static boolean isNumeric(String raw) {
        if (raw == null || raw.isBlank())
            return false;
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
            if (scene == null || scene.id == null || scene.id.isBlank())
                continue;
            pushScene(scene, mode);
            count++;
        }
        notifyClient(Component.translatable("ponderer.cmd.push.done", count, mode));
        return count;
    }

    // ---- /ponderer new ----

    public static int newSceneFromHand(@Nullable CompoundTag nbt) {
        var player = Minecraft.getInstance().player;
        if (player == null)
            return 0;
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
        if (player == null)
            return 0;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.new.no_item"));
            return 0;
        }
        CompoundTag tag = extractStackNbtFilter(held);
        if (tag == null || tag.isEmpty()) {
            notifyClient(Component.translatable("ponderer.cmd.new.no_nbt"));
            return 0;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        return newSceneForItem(itemId, tag);
    }

    /**
     * Build a robust nbtFilter from a stack for the 1.21.1 components model.
     */
    @Nullable
    public static CompoundTag extractStackNbtFilter(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!custom.isEmpty()) {
            return custom;
        }

        var level = Minecraft.getInstance().level;
        if (level != null) {
            Tag saved = stack.saveOptional(level.registryAccess());
            if (saved instanceof CompoundTag ct && ct.contains("components", Tag.TAG_COMPOUND)) {
                CompoundTag components = ct.getCompound("components");
                if (!components.isEmpty()) {
                    CompoundTag filter = new CompoundTag();
                    filter.put("components", components.copy());
                    return filter;
                }
            }
        }

        return null;
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
        scene.title = LocalizedText.ofMap(Map.of(
                "en_us", "New Scene - " + itemId.getPath(),
                "zh_cn", "新场景 - " + itemId.getPath()));
        scene.structures = List.of("ponderer:basic");
        scene.tags = List.of();
        if (nbt != null) {
            scene.nbtFilter = nbt.toString();
        }

        DslScene.SceneSegment seg = new DslScene.SceneSegment();
        seg.id = "scene_1";
        seg.title = LocalizedText.ofMap(Map.of(
                "en_us", "Scene 1",
                "zh_cn", "场景 1"));

        DslScene.DslStep showStep = new DslScene.DslStep();
        showStep.type = "show_structure";
        showStep.attachKeyFrame = true;

        DslScene.DslStep idleStep = new DslScene.DslStep();
        idleStep.type = "idle";
        idleStep.duration = 20;

        DslScene.DslStep textStep = new DslScene.DslStep();
        textStep.type = "text";
        textStep.duration = 60;
        textStep.text = LocalizedText.ofMap(Map.of(
                "en_us", "Edit this ponder scene!",
                "zh_cn", "编辑这个思索场景!"));
        textStep.point = List.of(2.5, 2.0, 2.5);
        textStep.placeNearTarget = true;
        textStep.attachKeyFrame = true;

        seg.steps = List.of(showStep, idleStep, textStep);
        scene.scenes = List.of(seg);

        if (SceneStore.saveSceneToLocal(scene)) {
            SceneStore.reloadFromDisk();
            Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);
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
        return doCopyScene(source.get(), targetItem);
    }

    /**
     * Copy a scene identified by its scene key to a new target item.
     */
    public static int copySceneByKey(String sceneKey, ResourceLocation targetItem) {
        DslScene source = SceneRuntime.findByKey(sceneKey);
        if (source == null) {
            notifyClient(Component.translatable("ponderer.cmd.scene_not_found", sceneKey));
            return 0;
        }
        return doCopyScene(source, targetItem);
    }

    private static int doCopyScene(DslScene original, ResourceLocation targetItem) {
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
            Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);
            notifyClient(
                    Component.translatable("ponderer.cmd.copy.done", original.id, newId, targetItem.toString()));
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
            Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);
            notifyClient(Component.translatable("ponderer.cmd.delete.done", id));
            return 1;
        } else {
            notifyClient(Component.translatable("ponderer.cmd.delete.failed", id));
            return 0;
        }
    }

    /**
     * Delete a scene identified by its scene key.
     * Key format: "ponderer:example" or "[my_pack] ponderer:example"
     */
    public static int deleteSceneByKey(String sceneKey) {
        DslScene target = SceneRuntime.findByKey(sceneKey);
        if (target == null) {
            notifyClient(Component.translatable("ponderer.cmd.scene_not_found", sceneKey));
            return 0;
        }

        if (SceneStore.deleteSceneByKey(sceneKey)) {
            SceneStore.reloadFromDisk();
            Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);
            notifyClient(Component.translatable("ponderer.cmd.delete.done", sceneKey));
            return 1;
        } else {
            notifyClient(Component.translatable("ponderer.cmd.delete.failed", sceneKey));
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
        Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);
        notifyClient(Component.translatable("ponderer.cmd.delete.item_done", count, itemStr));
        return count;
    }

    // ---- /ponderer list ----

    public static int openItemList() {
        Minecraft.getInstance().execute(
                () -> Minecraft.getInstance().setScreen(new com.nododiiiii.ponderer.ui.PonderItemGridScreen()));
        return 1;
    }

    @Nullable
    private static Consumer<Component> messageSink = null;

    public static void runWithMessageSink(Consumer<Component> sink, Runnable action) {
        Consumer<Component> previous = messageSink;
        messageSink = sink;
        try {
            action.run();
        } finally {
            messageSink = previous;
        }
    }

    public static void runReportingTo(Consumer<String> infoSink, Consumer<String> errorSink, Runnable action) {
        List<Component> messages = new ArrayList<>();
        runWithMessageSink(messages::add, action);

        if (messages.isEmpty()) {
            infoSink.accept(Component.translatable("ponderer.ui.command.no_output").getString());
            return;
        }

        boolean anyError = false;
        StringBuilder combined = new StringBuilder();
        for (Component message : messages) {
            if (combined.length() > 0) {
                combined.append("  ");
            }
            combined.append(message.getString());
            anyError |= isErrorResult(message);
        }

        (anyError ? errorSink : infoSink).accept(combined.toString());
    }

    private static boolean isErrorResult(Component message) {
        if (message.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            return key.contains("failed")
                || key.contains("not_found")
                || key.contains("no_item")
                || key.contains("no_nbt")
                || key.contains("no_scenes")
                || key.contains(".error");
        }
        return false;
    }

    static void notifyClient(Component message) {
        if (messageSink != null) {
            messageSink.accept(message);
            return;
        }
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }

    private static int openExportScreen() {
        Minecraft.getInstance().setScreen(new com.nododiiiii.ponderer.ui.ExportPackScreen());
        return 1;
    }

    private static int openImportScreen() {
        Minecraft.getInstance().setScreen(new com.nododiiiii.ponderer.ui.ImportPackScreen());
        return 1;
    }

    private static int unregisterPack(String packName) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;

        PackStateStore.load();
        boolean hasLocalCopy = PackStateStore.isImported(packName)
            || (SceneStore.getPackDir(packName) != null && java.nio.file.Files.exists(SceneStore.getPackDir(packName)));
        if (!hasLocalCopy && !PackStateStore.hasImportedState(packName)) {
            player.displayClientMessage(Component.translatable("ponderer.pack.unregister.not_found", packName), false);
            return 0;
        }

        Path packDir = SceneStore.getPackDir(packName);
        if (packDir != null) {
            deleteDirectoryRecursive(packDir);
        }

        PackStateStore.removeImportedPack(packName);

        SceneStore.autoLoadPonderPacks();
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);

        player.displayClientMessage(Component.translatable("ponderer.pack.unregister.done", packName), false);
        return 1;
    }

    private static void deleteDirectoryRecursive(Path dir) {
        if (!java.nio.file.Files.exists(dir)) return;
        try (var paths = java.nio.file.Files.walk(dir)) {
            // Delete files first (reverse order so directories come after their contents)
            for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
