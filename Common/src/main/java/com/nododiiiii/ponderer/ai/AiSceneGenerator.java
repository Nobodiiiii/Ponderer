package com.nododiiiii.ponderer.ai;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.UIText;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.client.Minecraft;
import com.nododiiiii.ponderer.platform.PondererServices;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Orchestrator for the bounded scene-authoring agent, local save, and reload.
 */
public class AiSceneGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Generate a ponder scene from structure + user prompt using the bounded agent loop.
     *
     * @param structurePaths  list of NBT file paths for the structures
     * @param carrierItemId   the item ID this scene is for (e.g. "minecraft:piston")
     * @param userPrompt      user's natural language prompt
     * @param referenceUrls   optional reference web page URLs
     * @param existingJson    existing JSON for adjustment mode (null for new generation)
     * @param onSuccess       callback on main thread with the saved file path
     * @param onError         callback on main thread with error message
     * @param onStatus        callback on main thread with intermediate status messages
     */
    public static AiGenerationTask generate(List<Path> structurePaths, String carrierItemId, String userPrompt,
                                            List<String> referenceUrls, String existingJson,
                                            boolean buildTutorial, boolean includeImages,
                                            Consumer<String> onSuccess, Consumer<String> onError,
                                            Consumer<String> onStatus) {
        return start(
            structurePaths, carrierItemId, userPrompt, referenceUrls, existingJson,
            buildTutorial, includeImages, onSuccess, onError, () -> {
            }, onStatus);
    }

    public static AiGenerationTask start(List<Path> structurePaths, String carrierItemId, String userPrompt,
                                         List<String> referenceUrls, String existingJson,
                                         boolean buildTutorial, boolean includeImages,
                                         Consumer<String> onSuccess, Consumer<String> onError,
                                         Runnable onCancelled, Consumer<String> onStatus) {
        String runId = UUID.randomUUID().toString();
        AiCancellationScope cancellation = new AiCancellationScope();
        writeLog("last_agent_trace.log", "run=" + runId + "\nstatus=started\n");
        CompletableFuture<String> worker = CompletableFuture.supplyAsync(() -> {
            try {
                cancellation.throwIfCancelled();
                AiRuntimeConfig config = AiRuntimeConfigResolver.resolve();
                if (!config.hasApiKey()) {
                    throw new RuntimeException(
                        "API key not configured. Fill custom AI settings or choose Codex/Claude Code in Settings > AI Configuration.");
                }

                Path editedPath = null;
                String savedBaseline = null;
                if (existingJson != null && !existingJson.isBlank()) {
                    DslScene original = new com.google.gson.GsonBuilder()
                        .registerTypeAdapter(com.nododiiiii.ponderer.ponder.LocalizedText.class,
                            new com.nododiiiii.ponderer.ponder.LocalizedText.GsonAdapter()).create()
                        .fromJson(existingJson, DslScene.class);
                    editedPath = SceneStore.resolveLocalScenePath(original);
                    if (editedPath != null && Files.exists(editedPath)) savedBaseline = Files.readString(editedPath);
                }
                AiSceneAgentPipeline.Request request = new AiSceneAgentPipeline.Request(
                    structurePaths == null ? List.of() : List.copyOf(structurePaths),
                    carrierItemId,
                    userPrompt,
                    referenceUrls == null ? List.of() : List.copyOf(referenceUrls),
                    existingJson,
                    buildTutorial,
                    includeImages
                );
                AiSceneAgentPipeline.Result result = AiSceneAgentPipeline.run(
                    runId,
                    request,
                    AiProviderFactory.create(config),
                    config,
                    Config.AI_MAX_AGENT_TURNS.get(),
                    status -> notifyStatus(onStatus, status),
                    AiSceneGenerator::writeLog,
                    cancellation
                );

                cancellation.throwIfCancelled();
                if (editedPath != null) {
                    String current = Files.exists(editedPath) ? Files.readString(editedPath) : null;
                    if (!java.util.Objects.equals(savedBaseline, current)) {
                        throw new IllegalStateException("SCENE_CHANGED: the scene changed during generation; the AI result was not saved. Retry from the current scene.");
                    }
                }
                SceneStore.LocalSaveResult saveResult = SceneStore.saveSceneToLocalDetailed(result.scene());
                if (!saveResult.isSuccess() || saveResult.path() == null) {
                    throw new RuntimeException(saveResult.englishMessage());
                }

                return saveResult.path().toString();
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
        CompletableFuture<Void> completion = worker.handleAsync((filePath, error) -> {
            if (error == null) {
                PondererClientCommands.reloadLocal();
                onSuccess.accept(filePath);
                return null;
            }
            Throwable cause = unwrap(error);
            if (cancellation.isCancellationRequested() || cause instanceof CancellationException) {
                appendLog("last_agent_trace.log",
                    System.lineSeparator() + "run=" + runId + " cancelled" + System.lineSeparator());
                onCancelled.run();
                return null;
            }
            LOGGER.error("AI scene generation failed", cause);
            onError.accept(safeMessage(cause));
            return null;
        }, Minecraft.getInstance());
        return new AiGenerationTask(runId, cancellation, completion);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    /** Schedule a status notification on the main thread. */
    private static void notifyStatus(Consumer<String> onStatus, String message) {
        Minecraft.getInstance().execute(() -> onStatus.accept(localizeAgentStatus(message)));
    }

    private static String localizeAgentStatus(String message) {
        if (message != null && message.startsWith("Agent turn ")) {
            String value = message.substring("Agent turn ".length());
            String[] parts = value.split("/", 2);
            if (parts.length == 2) {
                return UIText.of("ponderer.ui.ai_generate.status.agent_turn", parts[0], parts[1]);
            }
        }
        String key = switch (message == null ? "" : message) {
            case "Fetching reference pages" -> "ponderer.ui.ai_generate.status.fetching_web";
            case "Querying structure" -> "ponderer.ui.ai_generate.status.query_structure";
            case "Resolving registry entries" -> "ponderer.ui.ai_generate.status.registry";
            case "Loading scene step schema" -> "ponderer.ui.ai_generate.status.schema";
            case "Searching reference material" -> "ponderer.ui.ai_generate.status.search_references";
            case "Inspecting existing scene" -> "ponderer.ui.ai_generate.status.inspect_existing";
            case "Reviewing scene draft" -> "ponderer.ui.ai_generate.status.review";
            case "Validating scene submission" -> "ponderer.ui.ai_generate.status.submit";
            case "Fixing scene draft" -> "ponderer.ui.ai_generate.status.fix";
            default -> null;
        };
        return key == null ? message : UIText.of(key);
    }

    /** Write content to a log file under config/ponderer/logs/ for debugging. */
    private static void writeLog(String fileName, String content) {
        try {
            Path file = SafePaths.resolveFileName(getLogsDir(), fileName);
            if (file == null) {
                LOGGER.warn("Rejected unsafe log filename {}", fileName);
                return;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Failed to write log file {}: {}", fileName, e.getMessage());
        }
    }

    private static void appendLog(String fileName, String content) {
        try {
            Path file = SafePaths.resolveFileName(getLogsDir(), fileName);
            if (file == null) {
                LOGGER.warn("Rejected unsafe log filename {}", fileName);
                return;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            LOGGER.warn("Failed to append log file {}: {}", fileName, e.getMessage());
        }
    }

    /**
     * Post-process: auto-add attachKeyFrame to the first non-text/idle step
     * in each consecutive run. Resets when a text or idle step is encountered.
     */
    static void autoAddKeyFrames(DslScene scene) {
        if (scene.scenes != null) {
            for (DslScene.SceneSegment seg : scene.scenes) {
                if (seg.steps != null) addKeyFramesToSteps(seg.steps);
            }
        }
    }

    private static void addKeyFramesToSteps(List<DslScene.DslStep> steps) {
        boolean needKeyFrame = true; // start true so first action step gets a keyframe
        for (DslScene.DslStep step : steps) {
            if (step.type == null) continue;
            String t = step.type.toLowerCase(java.util.Locale.ROOT);
            if ("text".equals(t) || "shared_text".equals(t) || "idle".equals(t)) {
                needKeyFrame = true;
            } else {
                if (needKeyFrame) {
                    step.attachKeyFrame = true;
                    needKeyFrame = false;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // System prompts — hot-reloadable from config/ponderer/prompts/
    // -------------------------------------------------------------------------

    static String buildAgentSystemPrompt(boolean building, boolean adjusting, String tools) {
        return AiPromptAssembler.build(getConfigDirFallback().resolve("ponderer/prompts"), building, adjusting, tools);
    }

    private static Path getLogsDir() { return getConfigDirFallback().resolve("ponderer/logs"); }

    private static Path getConfigDirFallback() {
        try { return PondererServices.PLATFORM.getConfigDir(); }
        catch (Throwable ignored) { return Path.of(System.getProperty("java.io.tmpdir"), "ponderer-test-config"); }
    }
}
