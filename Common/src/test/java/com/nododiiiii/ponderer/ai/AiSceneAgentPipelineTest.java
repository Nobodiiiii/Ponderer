package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.ponder.DslScene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSceneAgentPipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void repairsInvalidDraftUntilSceneParsesAndValidates() {
        FakeLlm llm = new FakeLlm(List.of(
            """
                Segment 1: introduce the block.
                REQUIRED_ELEMENTS: Paper
                """,
            """
                {"id":"","items":[],"scenes":[{"steps":[{"type":"text","text":"broken","point":[99,99,99]}]}]}
                """,
            validSceneJson()
        ));

        AiRuntimeConfig config = new AiRuntimeConfig(
            "openai", "chat", "https://example.test", "test-key", "test-model", 4096, "test");
        AiSceneAgentPipeline.Request request = new AiSceneAgentPipeline.Request(
            List.of(),
            "minecraft:paper",
            "Generate a tiny parseable scene",
            List.of(),
            null,
            false,
            false
        );

        List<String> statuses = new ArrayList<>();
        AiSceneAgentPipeline.Result result = AiSceneAgentPipeline.run(
            request,
            llm,
            config,
            2,
            statuses::add,
            (requiredElements, structureBlockIds) -> "Display Name -> Registry ID mapping:\n  Paper -> minecraft:paper [item]\n");

        assertEquals("ponderer:test_agent", result.scene().id);
        assertEquals(1, result.repairAttempts());
        assertEquals(3, llm.calls.size());
        assertTrue(result.validationIssues().isEmpty());
        assertTrue(result.finalJson().contains("\"ponderer:test_agent\""));
        assertTrue(statuses.stream().anyMatch(status -> status.startsWith("Repairing scene JSON")));
        assertTrue(llm.calls.get(2).userText().contains("VALIDATOR REPAIR REQUEST"));
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

    private static String validSceneJson() {
        return """
            {
              "id": "ponderer:test_agent",
              "items": ["minecraft:paper"],
              "title": {"en_us": "Agent Test", "zh_cn": "Agent Test"},
              "structures": ["ponderer:basic"],
              "steps": [],
              "scenes": [
                {
                  "id": "main",
                  "title": {"en_us": "Main", "zh_cn": "Main"},
                  "steps": [
                    {"type": "show_structure", "structure": "ponderer:basic"},
                    {
                      "type": "text",
                      "duration": 20,
                      "text": {"en_us": "Parsed successfully.", "zh_cn": "Parsed successfully."},
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
