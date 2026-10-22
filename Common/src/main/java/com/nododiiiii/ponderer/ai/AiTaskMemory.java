package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;

/** Small model-authored working notes. Source locations are never a substitute for evidence. */
final class AiTaskMemory {
    private JsonObject value = new JsonObject();

    static JsonObject schema() {
        return com.google.gson.JsonParser.parseString("""
            {"type":"object","required":["facts","decisions","next_steps"],"properties":{
              "facts":{"type":"array","maxItems":12,"items":{"type":"object","required":["note","tool","arguments"],"properties":{
                "note":{"type":"string","minLength":1,"maxLength":512},
                "tool":{"type":"string","enum":["query_structure","lookup_registry","inspect_registry_entries","get_step_schema","search_references","read_reference","inspect_existing_scene","get_scene_draft"]},
                "arguments":{"type":"object"}}}},
              "decisions":{"type":"array","maxItems":8,"items":{"type":"string","minLength":1,"maxLength":512}},
              "next_steps":{"type":"array","maxItems":8,"items":{"type":"string","minLength":1,"maxLength":256}}
            }}
            """).getAsJsonObject();
    }

    JsonObject snapshot() { return value.deepCopy(); }

    void restore(JsonObject saved) {
        validate(saved);
        value = saved.deepCopy();
    }

    void update(JsonObject next, AgentContext.RunState state) {
        validate(next);
        for (var fact : next.getAsJsonArray("facts")) {
            JsonObject source = fact.getAsJsonObject();
            if (!state.hasEvidence(source.get("tool").getAsString(), source.getAsJsonObject("arguments")))
                throw new IllegalArgumentException("UNREAD_EVIDENCE: fact notes must point to a successful read in this run");
        }
        value = next.deepCopy();
    }

    private static void validate(JsonObject notes) {
        if (notes.toString().length() > 16384) throw new IllegalArgumentException("Task notes exceed 16384 characters");
        AiJsonSchema.validate(schema(), notes, "$task_state");
    }
}
