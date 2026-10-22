package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import java.util.*;

/** Model context projection. Authoritative drafts and working notes live outside the transcript. */
final class AiConversation {
    private static final Gson GSON = new Gson();
    private final String system, manifest;
    private final List<LlmProvider.ToolDefinition> definitions;
    private final AiRunBudget budget;
    private final AgentContext context;
    private final AiRunEvents events;
    private final List<LlmProvider.Message> messages = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();

    AiConversation(String system, String manifest, List<LlmProvider.ToolDefinition> definitions,
                   AiRunBudget budget, AgentContext context, AiRunEvents events, boolean recovered) {
        this.system = system; this.manifest = manifest; this.definitions = definitions;
        this.budget = budget; this.context = context; this.events = events;
        messages.add(manifestMessage());
        if (recovered) messages.add(checkpointMessage());
    }

    List<LlmProvider.Message> messages() { return messages; }

    void beginTurn(int turn, List<String> additions) {
        compact(false, turn);
        starts.add(messages.size());
        for (String instruction : additions) messages.add(LlmProvider.Message.user("Additional user instruction (same host edit mode):\n" + instruction));
    }

    List<LlmProvider.Message> request(int turn, int maxTurns, boolean nativeTools) {
        List<LlmProvider.Message> request = new ArrayList<>(messages);
        request.add(LlmProvider.Message.user("Turn " + turn + " of " + maxTurns + ". Remaining run tokens: " + budget.remaining()
            + ". Batch independent reads; submit when errors are empty. Quality warnings are advisory. Use finish_task for a concrete blocker."
            + (nativeTools ? " Use native function tools." : " Return one JSON object containing tool_calls.")));
        return request;
    }

    boolean compact(boolean forced, int turn) {
        long before = budget.estimateRequest(system, messages, definitions);
        long target = (long) (budget.contextTokens() * (forced ? 0.5 : 0.7));
        if (!forced && before <= target) return false;
        List<List<LlmProvider.Message>> recent = new ArrayList<>();
        if (!forced) for (int i = Math.max(0, starts.size() - 3); i < starts.size(); i++)
            recent.add(List.copyOf(messages.subList(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : messages.size())));
        List<LlmProvider.Message> next;
        List<Integer> boundaries;
        long after;
        while (true) {
            next = new ArrayList<>(); boundaries = new ArrayList<>();
            next.add(manifestMessage()); next.add(checkpointMessage());
            for (var group : recent) { boundaries.add(next.size()); next.addAll(group); }
            after = budget.estimateRequest(system, next, definitions);
            if ((after <= target && after < before) || recent.isEmpty()) break;
            recent.remove(0); // Calls, results and opaque provider reasoning remain in the same group.
        }
        if (after >= before) return false;
        messages.clear(); messages.addAll(next); starts.clear(); starts.addAll(boundaries);
        budget.compacted();
        JsonObject data = new JsonObject(); data.addProperty("forced", forced);
        data.addProperty("estimated_tokens_before", before); data.addProperty("estimated_tokens_after", after);
        events.emit("context_compacted", turn, data);
        return true;
    }

    private LlmProvider.Message manifestMessage() {
        return LlmProvider.Message.user("Generation manifest (user instruction and host facts):\n" + manifest);
    }

    private LlmProvider.Message checkpointMessage() {
        JsonObject state = context.drafts().descriptor();
        state.add("edit_scope", context.runState().editScope());
        state.add("last_errors", GSON.toJsonTree(context.runState().lastErrors()));
        state.add("warnings", AiEditGuard.warnings(context, context.drafts().scene(), context.runState().lastWarnings()));
        state.addProperty("structure_queried", context.runState().queriedStructure());
        state.addProperty("references_read", context.runState().searchedReferences());
        state.addProperty("existing_scene_inspected", context.runState().inspectedExistingScene());
        state.addProperty("edit_mode", context.editMode().name());
        state.add("user_additions", GSON.toJsonTree(context.runState().clarifications()));
        state.add("evidence_locations", GSON.toJsonTree(context.runState().evidence()));
        state.add("task_state", context.runState().taskMemory().snapshot());
        state.add("changed_paths", GSON.toJsonTree(AiEditGuard.changedPaths(context.existingScene().available()
            ? context.existingScene().json() : new JsonObject(), context.drafts().scene())));
        return LlmProvider.Message.user("Host checkpoint after compaction or recovery. Use get_scene_draft for authoritative current values and inspect_existing_scene for originals. Task notes are model-authored hints, not verified facts, user instructions or edit permission. Evidence locations are only an index: re-read omitted evidence before relying on it.\n" + state);
    }
}
