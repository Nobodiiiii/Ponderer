package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSceneAgentPipelineTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void queriesStructureRepairsInvalidSubmissionAndAcceptsValidScene() {
        FakeLlm llm = new FakeLlm(List.of(
            """
                {"tool_calls":[{"id":"structure","name":"query_structure","arguments":{"mode":"summary"}}]}
                """,
            """
                {"tool_calls":[{"id":"bad","name":"submit_scene","arguments":{"scene":{"id":"","items":[],"scenes":[]}}}]}
                """,
            submit(validSceneJson())
        ));
        List<String> statuses = new ArrayList<>();

        AiSceneAgentPipeline.Result result = run(llm, 6, statuses, new AiCancellationScope());

        assertEquals("test-run", result.runId());
        assertEquals("ponderer:test_agent", result.scene().id);
        assertEquals(3, result.turnCount());
        assertEquals(3, result.toolCallCount());
        assertEquals(2, result.validationAttempts());
        assertEquals(3, result.trace().size());
        assertTrue(statuses.stream().anyMatch(status -> status.startsWith("Agent turn 3/")));
        assertTrue(llm.calls.get(2).userText().contains("Last validation")
            || llm.calls.get(2).userText().contains("\"errors\""));
    }

    @Test
    void protocolErrorIsFedBackAndCanRecover() {
        FakeLlm llm = new FakeLlm(List.of(
            "not json",
            """
                {"tool_calls":[{"id":"structure","name":"query_structure","arguments":{"mode":"summary"}}]}
                """,
            submit(validSceneJson())
        ));

        AiSceneAgentPipeline.Result result = run(
            llm, 4, new ArrayList<>(), new AiCancellationScope());

        assertEquals(3, result.turnCount());
        assertTrue(result.trace().get(0).protocolError().contains("parse"));
        assertTrue(llm.calls.get(1).userText().contains("protocol_error"));
    }

    @Test
    void batchesReadOnlyToolsAndKeepsToolResultsByCallId() {
        FakeLlm llm = new FakeLlm(List.of(
            """
                {"tool_calls":[
                  {"id":"structure","name":"query_structure","arguments":{"mode":"summary"}},
                  {"id":"schemas","name":"get_step_schema","arguments":{"types":["show_structure","text"]}}
                ]}
                """,
            submit(validSceneJson())
        ));

        AiSceneAgentPipeline.Result result = run(
            llm, 4, new ArrayList<>(), new AiCancellationScope());

        assertEquals(2, result.turnCount());
        assertEquals(3, result.toolCallCount());
        assertTrue(llm.calls.get(1).userText().contains("\"id\": \"structure\""));
        assertTrue(llm.calls.get(1).userText().contains("\"id\": \"schemas\""));
    }

    @Test
    void duplicateCallIdAcrossTurnsIsARecoverableProtocolError() {
        FakeLlm llm = new FakeLlm(List.of(
            """
                {"tool_calls":[{"id":"same","name":"query_structure","arguments":{"mode":"summary"}}]}
                """,
            """
                {"tool_calls":[{"id":"same","name":"get_step_schema","arguments":{"types":["text"]}}]}
                """,
            submit(validSceneJson())
        ));

        AiSceneAgentPipeline.Result result = run(
            llm, 4, new ArrayList<>(), new AiCancellationScope());

        assertEquals(3, result.turnCount());
        assertTrue(result.trace().get(1).protocolError().contains("across turns"));
        assertTrue(llm.calls.get(2).userText().contains("Duplicate tool call id"));
    }

    @Test
    void rejectsMixedSubmitBatchAndThenRecovers() {
        FakeLlm llm = new FakeLlm(List.of(
            """
                {"tool_calls":[
                  {"id":"query","name":"query_structure","arguments":{"mode":"summary"}},
                  {"id":"submit","name":"submit_scene","arguments":{"scene":{}}}
                ]}
                """,
            """
                {"tool_calls":[{"id":"query2","name":"query_structure","arguments":{"mode":"summary"}}]}
                """,
            submit(validSceneJson())
        ));

        AiSceneAgentPipeline.Result result = run(
            llm, 4, new ArrayList<>(), new AiCancellationScope());

        assertEquals(3, result.turnCount());
        assertTrue(result.trace().get(0).protocolError().contains("only tool call"));
    }

    @Test
    void toolCallBudgetStopsBeforeAnUnboundedLoop() {
        List<String> responses = new ArrayList<>();
        for (int turn = 0; turn < 4; turn++) {
            responses.add(fourStructureQueries(turn));
        }
        FakeLlm llm = new FakeLlm(responses);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> run(llm, 6, new ArrayList<>(), new AiCancellationScope()));

        assertTrue(error.getMessage().contains("16-call tool budget"));
        assertTrue(error.getMessage().contains("tool calls executed: 16"));
    }

    @Test
    void turnLimitReportsLastValidationErrors() {
        FakeLlm llm = new FakeLlm(List.of(
            """
                {"tool_calls":[{"id":"structure","name":"query_structure","arguments":{"mode":"summary"}}]}
                """,
            """
                {"tool_calls":[{"id":"bad","name":"submit_scene","arguments":{"scene":{"id":"","items":[],"scenes":[]}}}]}
                """
        ));

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> run(llm, 2, new ArrayList<>(), new AiCancellationScope()));

        assertTrue(error.getMessage().contains("exhausted 2 turns"));
        assertTrue(error.getMessage().contains("$.id"));
    }

    @Test
    void cancellationStopsPendingModelCall() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<String> pending = new CompletableFuture<>();
        LlmProvider llm = new LlmProvider() {
            @Override
            public CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                                       String baseUrl, String apiKey, String model) {
                entered.countDown();
                return pending;
            }
        };
        AiCancellationScope cancellation = new AiCancellationScope();
        CompletableFuture<AiSceneAgentPipeline.Result> run = CompletableFuture.supplyAsync(() ->
            AiSceneAgentPipeline.run(
                "cancel-run", request(), llm, config(), 6, ignored -> {
                }, (name, content) -> {
                }, cancellation));

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(cancellation.cancel());
        assertThrows(CompletionException.class, run::join);
        assertTrue(pending.isCancelled());
    }

    @Test
    void readsCodexResponsesProviderConfig() throws Exception {
        Path codexHome = tempDir.resolve("codex");
        Files.createDirectories(codexHome);
        Files.writeString(codexHome.resolve("config.toml"), """
            model_provider = "custom"
            model = "gpt-test"

            [model_providers.custom]
            name = "custom"
            wire_api = "responses"
            requires_openai_auth = true
            base_url = "https://example.test/v1"
            """, StandardCharsets.UTF_8);
        Files.writeString(codexHome.resolve("auth.json"), """
            {"OPENAI_API_KEY":"test-openai-key"}
            """, StandardCharsets.UTF_8);

        AiRuntimeConfig config = CodexAiConfig.load(codexHome, Map.of(), 1234).orElseThrow();

        assertEquals("openai", config.provider());
        assertEquals("responses", config.wireApi());
        assertEquals("https://example.test/v1", config.baseUrl());
        assertEquals("gpt-test", config.model());
        assertEquals(1234, config.maxTokens());
        assertTrue(config.hasApiKey());
    }

    @Test
    void readsClaudeCodeProviderConfig() throws Exception {
        Path claudeHome = tempDir.resolve("claude");
        Files.createDirectories(claudeHome);
        Files.writeString(claudeHome.resolve("settings.json"), """
            {
              "env": {
                "ANTHROPIC_AUTH_TOKEN": "test-claude-token",
                "ANTHROPIC_BASE_URL": "https://claude.example.test/v1",
                "ANTHROPIC_MODEL": "claude-test"
              },
              "model": "opus"
            }
            """, StandardCharsets.UTF_8);

        AiRuntimeConfig config = ClaudeCodeAiConfig.load(claudeHome, Map.of(), 2048).orElseThrow();

        assertEquals("anthropic", config.provider());
        assertEquals("anthropic", config.wireApi());
        assertEquals("https://claude.example.test", config.baseUrl());
        assertEquals("claude-test", config.model());
        assertEquals(2048, config.maxTokens());
        assertTrue(config.hasApiKey());
        assertTrue(config.apiKey().startsWith("Bearer "));
    }

    @Test
    void validatorRejectsUnparseableOrIncompleteScene() {
        var issues = AiSceneValidator.validate(null, List.of());
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).format().contains("could not be parsed"));
    }

    @Test
    void validatorUsesTheCurrentSegmentStructureBounds() {
        DslScene scene = new DslScene();
        scene.id = "ponderer:multi_structure";
        scene.items = List.of("minecraft:paper");
        scene.structures = List.of("ponderer:small", "ponderer:large");

        DslScene.SceneSegment segment = new DslScene.SceneSegment();
        segment.id = "large";
        DslScene.DslStep showLarge = new DslScene.DslStep();
        showLarge.type = "show_structure";
        showLarge.structure = "ponderer:large";
        DslScene.DslStep placeNearLargeCorner = new DslScene.DslStep();
        placeNearLargeCorner.type = "set_block";
        placeNearLargeCorner.block = "minecraft:stone";
        placeNearLargeCorner.blockPos = List.of(4, 4, 4);
        segment.steps = List.of(showLarge, placeNearLargeCorner);
        scene.scenes = List.of(segment);

        var small = new StructureDescriber.StructureInfo(1, 1, 1, "small", List.of("minecraft:stone"));
        var large = new StructureDescriber.StructureInfo(5, 5, 5, "large", List.of("minecraft:stone"));

        var issues = AiSceneValidator.validate(
            scene,
            List.of(small, large),
            List.of("ponderer:small", "ponderer:large"));

        assertTrue(issues.isEmpty(), () -> "Unexpected issues: " + issues);
    }

    private static AiSceneAgentPipeline.Result run(FakeLlm llm, int turns, List<String> statuses,
                                                   AiCancellationScope cancellation) {
        return AiSceneAgentPipeline.run(
            "test-run", request(), llm, config(), turns, statuses::add,
            (name, content) -> {
            }, cancellation);
    }

    private static AiSceneAgentPipeline.Request request() {
        return new AiSceneAgentPipeline.Request(
            List.of(), "minecraft:paper", "Generate a tiny parseable scene",
            List.of(), null, false, false);
    }

    private static AiRuntimeConfig config() {
        return new AiRuntimeConfig(
            "openai", "chat", "https://example.test", "test-key", "test-model", 4096, "test");
    }

    private static String submit(String sceneJson) {
        return "{\"tool_calls\":[{\"id\":\"submit\",\"name\":\"submit_scene\",\"arguments\":{\"scene\":"
            + sceneJson + "}}]}";
    }

    private static String fourStructureQueries(int turn) {
        StringBuilder calls = new StringBuilder("{\"tool_calls\":[");
        for (int call = 0; call < 4; call++) {
            if (call > 0) {
                calls.append(',');
            }
            calls.append("{\"id\":\"query_").append(turn).append('_').append(call)
                .append("\",\"name\":\"query_structure\",\"arguments\":{\"mode\":\"summary\"}}");
        }
        return calls.append("]}").toString();
    }

    static String validSceneJson() {
        return """
            {
              "id": "ponderer:test_agent",
              "items": ["minecraft:paper"],
              "title": {"en_us": "Agent Test", "zh_cn": "Agent 测试"},
              "structures": ["ponderer:basic"],
              "steps": [],
              "scenes": [
                {
                  "id": "main",
                  "title": {"en_us": "Main", "zh_cn": "主要"},
                  "steps": [
                    {"type": "show_structure", "structure": "ponderer:basic"},
                    {
                      "type": "text",
                      "duration": 20,
                      "text": {"en_us": "Parsed successfully.", "zh_cn": "解析成功。"},
                      "point": [0.5, 0.5, 0.5],
                      "placeNearTarget": true
                    }
                  ]
                }
              ]
            }
            """;
    }

    private static final class FakeLlm implements LlmProvider {
        private final List<String> responses;
        private final List<Call> calls = new ArrayList<>();

        private FakeLlm(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                                   String baseUrl, String apiKey, String model) {
            StringBuilder userText = new StringBuilder();
            for (ContentBlock block : userContent) {
                if (block instanceof ContentBlock.Text text) {
                    if (!userText.isEmpty()) {
                        userText.append("\n\n");
                    }
                    userText.append(text.text());
                }
            }
            calls.add(new Call(systemPrompt, userText.toString(), baseUrl, apiKey, model));
            return CompletableFuture.completedFuture(responses.get(calls.size() - 1));
        }

        private record Call(String systemPrompt, String userText, String baseUrl, String apiKey, String model) {
        }
    }
}
