package com.nododiiiii.ponderer.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolRegistryTest {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registersAllEightToolsAndProvidesSchemas() {
        AgentToolRegistry registry = new AgentToolRegistry();

        assertEquals(8, registry.all().size());
        assertNotNull(registry.get("query_structure"));
        assertNotNull(registry.get("submit_scene"));
        assertTrue(registry.promptSpecification().contains("\"input_schema\""));
        assertEquals(32, SceneStepCatalog.types().size());
    }

    @Test
    void queriesStructuredBlocksAndStepSchemas() {
        AgentToolRegistry registry = new AgentToolRegistry();
        AgentContext context = context(false);

        JsonObject query = JsonParser.parseString("""
            {"structure":"ponderer:basic","mode":"find","block":"lever","include_nbt":true}
            """).getAsJsonObject();
        JsonObject queryResult = registry.get("query_structure")
            .execute(context, query, new AiCancellationScope()).result();

        assertEquals(1, queryResult.get("total").getAsInt());
        assertEquals("minecraft:lever",
            queryResult.getAsJsonArray("blocks").get(0).getAsJsonObject().get("id").getAsString());
        assertTrue(context.runState().queriedStructure());

        JsonObject layer = registry.get("query_structure")
            .execute(context, JsonParser.parseString("""
                {"mode":"layer","y":1,"limit":1}
                """).getAsJsonObject(), new AiCancellationScope()).result();
        assertEquals(1, layer.get("total").getAsInt());

        JsonObject at = registry.get("query_structure")
            .execute(context, JsonParser.parseString("""
                {"mode":"at","pos":[0,0,0]}
                """).getAsJsonObject(), new AiCancellationScope()).result();
        assertEquals("minecraft:stone",
            at.getAsJsonArray("blocks").get(0).getAsJsonObject().get("id").getAsString());

        JsonObject truncated = registry.get("query_structure")
            .execute(context, JsonParser.parseString("""
                {"mode":"region","from":[0,0,0],"to":[4,4,4],"limit":1}
                """).getAsJsonObject(), new AiCancellationScope()).result();
        assertEquals(2, truncated.get("total").getAsInt());
        assertTrue(truncated.get("truncated").getAsBoolean());

        JsonObject schemas = registry.get("get_step_schema")
            .execute(context, JsonParser.parseString("""
                {"types":["show_controls","create_item_entity","does_not_exist"]}
                """).getAsJsonObject(), new AiCancellationScope()).result();
        assertEquals(2, schemas.getAsJsonArray("schemas").size());
        assertEquals(1, schemas.getAsJsonArray("unknown_types").size());
    }

    @Test
    void inspectsExactRegistryEntriesAndResolvesDisplayNames() {
        AgentToolRegistry registry = new AgentToolRegistry();
        AgentContext context = context(false);

        JsonObject inspect = registry.get("inspect_registry_entries")
            .execute(context, JsonParser.parseString("""
                {"ids":["minecraft:lever","not valid"]}
                """).getAsJsonObject(), new AiCancellationScope()).result();
        JsonObject lever = inspect.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertTrue(lever.get("exists").getAsBoolean());
        assertTrue(lever.getAsJsonArray("kinds").toString().contains("block"));
        assertTrue(lever.getAsJsonObject("block_properties").has("powered"));
        assertFalse(inspect.getAsJsonArray("entries").get(1).getAsJsonObject()
            .get("exists").getAsBoolean());

        JsonObject lookup = registry.get("lookup_registry")
            .execute(context, JsonParser.parseString("""
                {"names":["minecraft:paper"]}
                """).getAsJsonObject(), new AiCancellationScope()).result();
        assertTrue(lookup.getAsJsonArray("lookups").get(0).getAsJsonObject()
            .get("found").getAsBoolean());
        assertFalse(lookup.getAsJsonArray("lookups").get(0).getAsJsonObject()
            .get("ambiguous").getAsBoolean());
    }

    @Test
    void searchesOnlyPreparedReferencesAndReturnsBoundedImages() {
        AgentToolRegistry registry = new AgentToolRegistry();
        AgentContext context = context(true);

        AgentTool.ToolExecution execution = registry.get("search_references")
            .execute(context, JsonParser.parseString("""
                {"query":"steam output","max_results":2,"include_images":true}
                """).getAsJsonObject(), new AiCancellationScope());

        assertEquals(1, execution.result().getAsJsonArray("hits").size());
        assertEquals(1, execution.images().size());
        assertTrue(context.runState().searchedReferences());
    }

    @Test
    void inspectsExistingSceneAndSubmissionEnforcesPrerequisites() {
        AgentToolRegistry registry = new AgentToolRegistry();
        AgentContext context = context(true);
        AiCancellationScope cancellation = new AiCancellationScope();
        JsonObject arguments = new JsonObject();
        arguments.add("scene", JsonParser.parseString(AiSceneAgentPipelineTest.validSceneJson()));

        AgentTool.ToolExecution rejected = registry.get("submit_scene")
            .execute(context, arguments, cancellation);
        assertFalse(rejected.result().get("ok").getAsBoolean());
        String preconditionErrors = rejected.result().getAsJsonArray("errors").toString();
        assertTrue(preconditionErrors.contains("query_structure"));
        assertTrue(preconditionErrors.contains("search_references"));
        assertTrue(preconditionErrors.contains("inspect_existing_scene"));

        JsonObject existing = registry.get("inspect_existing_scene")
            .execute(context, JsonParser.parseString("""
                {"segment_ids":["main"],"include_steps":true}
                """).getAsJsonObject(), cancellation).result();
        assertTrue(existing.get("available").getAsBoolean());
        assertEquals(1, existing.getAsJsonArray("selected_segments").size());

        registry.get("query_structure").execute(context,
            JsonParser.parseString("""
                {"mode":"summary"}
                """).getAsJsonObject(), cancellation);
        registry.get("search_references").execute(context,
            JsonParser.parseString("""
                {"query":"steam"}
                """).getAsJsonObject(), cancellation);

        AgentTool.ToolExecution reviewed = registry.get("review_scene")
            .execute(context, arguments, cancellation);
        assertTrue(reviewed.result().get("ok").getAsBoolean());
        assertFalse(reviewed.result().getAsJsonArray("warnings").isEmpty());

        AgentTool.ToolExecution submitted = registry.get("submit_scene")
            .execute(context, arguments, cancellation);
        assertTrue(submitted.result().get("accepted").getAsBoolean());
        assertNotNull(submitted.submittedScene());
    }

    @Test
    void validatorRejectsKnownMissingIdsButOnlyWarnsForUnverifiableIngredients() {
        JsonObject sceneJson = JsonParser.parseString(
            AiSceneAgentPipelineTest.validSceneJson()).getAsJsonObject();
        sceneJson.getAsJsonArray("items").set(0, JsonParser.parseString("\"missing:no_item\""));
        DslScene missingCarrier = GSON.fromJson(sceneJson, DslScene.class);
        var info = context(false).structures().get(0).snapshot().info();
        var errors = AiSceneValidator.validate(
            missingCarrier, List.of(info), List.of("ponderer:basic"));
        assertTrue(errors.stream().anyMatch(issue -> issue.message().contains("not registered")));

        DslScene interactionScene = GSON.fromJson(
            AiSceneAgentPipelineTest.validSceneJson(), DslScene.class);
        DslScene.DslStep controls = new DslScene.DslStep();
        controls.type = "show_controls";
        controls.item = "missing:possibly_extended";
        controls.point = List.of(0.5, 0.5, 0.5);
        DslScene.DslStep instruction = new DslScene.DslStep();
        instruction.type = "text";
        instruction.text = LocalizedText.ofMap(Map.of(
            "en_us", "Right-click the port",
            "zh_cn", "右键点击端口"));
        interactionScene.scenes.get(0).steps = List.of(
            interactionScene.scenes.get(0).steps.get(0), controls, instruction);

        var warnings = AiSceneQualityLinter.lint(interactionScene);
        assertTrue(warnings.stream().anyMatch(
            warning -> warning.code().equals("INGREDIENT_UNVERIFIED")));
        assertTrue(warnings.stream().anyMatch(
            warning -> warning.code().equals("INTERACTION_ACTION_MISSING")));
    }

    private static AgentContext context(boolean withReferenceAndExisting) {
        StructureDescriber.StructureInfo info = new StructureDescriber.StructureInfo(
            5, 5, 5, "test", List.of("minecraft:stone", "minecraft:lever"));
        StructureDescriber.StructureSnapshot snapshot = new StructureDescriber.StructureSnapshot(
            info,
            List.of(
                new StructureDescriber.BlockInfo(0, 0, 0, "minecraft:stone", Map.of(), null),
                new StructureDescriber.BlockInfo(
                    1, 1, 1, "minecraft:lever", Map.of("powered", "false"), "{Custom:1b}")
            )
        );
        List<AgentContext.ReferenceDocument> references = withReferenceAndExisting
            ? List.of(new AgentContext.ReferenceDocument(
                "ref_1",
                "https://example.test/wiki",
                "The machine accepts water.\n\nSteam is the output.",
                List.of(new WebPageFetcher.ImageData("ZmFrZQ==", "image/png")),
                null))
            : List.of();
        AgentContext.ExistingSceneDocument existing;
        if (withReferenceAndExisting) {
            JsonObject json = JsonParser.parseString(
                AiSceneAgentPipelineTest.validSceneJson()).getAsJsonObject();
            DslScene scene = GSON.fromJson(json, DslScene.class);
            existing = new AgentContext.ExistingSceneDocument(json, scene, null);
        } else {
            existing = new AgentContext.ExistingSceneDocument(null, null, null);
        }
        return new AgentContext(
            List.of(new AgentContext.StructureDocument("ponderer:basic", snapshot)),
            references,
            existing,
            "minecraft:paper",
            "test",
            false,
            withReferenceAndExisting
        );
    }
}
