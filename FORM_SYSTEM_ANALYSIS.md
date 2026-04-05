# Form System Analysis

## Overall Judgment

The current form system already has a solid "declarative entry + screen base class" foundation, but it is not yet one unified framework.

Right now the codebase effectively has four parallel ways to describe forms:

- Generic form entries: `ui/catnip/FormEntries`
- Step-editor-specific entries: `ui/StepEditorEntries`
- Config form entries: `ui/catnip/ConfigEntries`
- Command parameter DSL: `ui/CommandParamScreen.FieldDef`

So the biggest optimization space is not the visual shell, but the consolidation of:

- form description
- state management
- field capabilities
- validation and parsing
- dynamic visibility and collection behavior

## Current Strengths

- `AbstractDeclarativeListScreen` and `AbstractDeclarativeFormScreen` already separate screen chrome from form content.
- `StepTextFieldHandle` and `StepXyzFieldHandle` are a good intermediate abstraction for widget binding and snapshot restore.
- The config screen branch is relatively clean and stable, and should be treated as an adapter rather than rebuilt first.
- The step editor branch already has a working snapshot and rebuild-preserving-state mechanism, which is useful as a prototype for a generalized form state layer.

## Main Repetition and Coupling

### 1. Duplicate Form Description Layers

There is clear overlap between:

- `Common/src/main/java/com/nododiiiii/ponderer/ui/catnip/FormEntries.java`
- `Common/src/main/java/com/nododiiiii/ponderer/ui/StepEditorEntries.java`
- `Common/src/main/java/com/nododiiiii/ponderer/ui/StepEditorEntry.java`
- `Common/src/main/java/com/nododiiiii/ponderer/ui/CommandParamScreen.java`

These all describe the same essential things:

- text fields
- choice buttons
- toggles
- field-local actions
- dynamic sections

The difference is mostly in how state is bound, not in what is being described.

### 2. State Management Is Split Across Screens

There are multiple separate implementations of:

- baseline capture
- dirty detection
- snapshot and restore
- rebuild after mode changes

Examples:

- `AbstractStepEditorScreen` has `snapshotForm()`, `restoreFromSnapshot()`, `rebuildFormPreservingState()`
- `CommandParamScreen` has `snapshotState()` and `restoreSnapshot()`
- `AiGenerateScreen` manually tracks baseline fields and static caches
- `SceneDescEditorScreen` and `ExportPackScreen` manually compute dirty counts

This means the framework does not yet provide a single reusable state model.

### 3. Validation and Parsing Still Live in Screens

`AbstractStepEditorScreen` provides primitive parsers like:

- `parseDouble()`
- `parseFloat()`
- `parseInt()`

But higher-level validation is still screen-local:

- required field checks
- optional XYZ parsing
- partial range validation
- NBT parsing
- resource ID validation

This causes repeated logic across many step screens.

### 4. Field Capabilities Are Not Unified

There are multiple field action systems:

- `FormTextButtonSpec`
- `StepTextButtonSpec`
- `StepXyzButtonSpec`

Most of the step layer wrappers are thin adapters over the generic layer, plus a few step-specific actions such as:

- JEI browse
- point pick
- NBT pick
- held item import

This indicates the framework wants a unified "field capability" abstraction, but does not have one yet.

### 5. Dynamic Forms Depend on Full Rebuilds

Many screens use:

- mode toggles
- conditional fields
- add/remove list items
- rebuild and restore snapshot

This pattern works, but it is framework debt:

- `SetBlockScreen`
- `CreateEntityScreen`
- `TriggerEditorScreen`
- `CommandParamScreen`
- dynamic block property rows in `AbstractStepEditorScreen`

The framework currently lacks first-class concepts for:

- conditional visibility
- enabled/disabled state
- dependent fields
- collection fields

## Highest-Value Refactors

## 1. Unify Form Description Into One Model

### What to refactor

Consolidate the parallel description layers into one model, for example:

- `FieldSpec`
- `FieldBinding<T>`
- `SectionSpec`
- `ActionSpec`

Then let step screens, config screens, and command screens each provide different bindings or save adapters.

### Why it matters

This removes the highest structural duplication in the codebase and makes every later cleanup easier.

### Benefit

- Large reduction in framework surface area
- Easier to add new field types once
- Step and non-step forms can share the same composition model

### Cost

- Medium to high

### Risk

- Moderate, because this touches many screens indirectly

### Recommended approach

Do not rewrite all screens first. Introduce the unified spec layer, then adapt existing helpers:

- reimplement `FormEntries` on top of it
- reimplement `StepEditorEntries` on top of it
- later migrate `CommandParamScreen`

## 2. Introduce a Shared FormState Layer

### What to refactor

Create a reusable state object responsible for:

- baseline capture
- snapshot and restore
- dirty diff
- temporary rebuild-safe state
- status message state

### Why it matters

Right now every advanced form reinvents part of this.

### Benefit

- Eliminates repeated dirty tracking logic
- Makes dynamic forms easier to reason about
- Reduces screen-level bookkeeping

### Cost

- Medium

### Risk

- Low to moderate if introduced incrementally

### Recommended approach

Start by extracting the snapshot/diff model from `AbstractStepEditorScreen`, because that implementation is already the most complete.

Then migrate:

- `CommandParamScreen`
- `AiGenerateScreen`
- `SceneDescEditorScreen`
- `ExportPackScreen`

## 3. Move Validation and Parsing Down to Field-Level Validators

### What to refactor

Introduce reusable parser and validator components such as:

- `requiredText`
- `resourceId(kind)`
- `requiredInt3`
- `optionalDouble3`
- `partialRange(from, to)`
- `nbtTag`

### Why it matters

Many `buildStep()` methods are dominated by validation boilerplate instead of business mapping.

### Benefit

- Smaller and clearer `buildStep()` implementations
- Consistent error messages
- Easier to test parsing independently

### Cost

- Medium

### Risk

- Low if validators are added alongside current code first

### Recommended approach

Extract validators for the most repeated patterns first:

- block ID
- entity ID
- NBT
- int XYZ
- double XYZ
- optional second coordinate range

## 4. Replace Button-Spec Families With Unified Field Capabilities

### What to refactor

Unify:

- `FormTextButtonSpec`
- `StepTextButtonSpec`
- `StepXyzButtonSpec`

into one capability/decorator system.

Candidate capabilities:

- `JeiCapability`
- `PickPointCapability`
- `NbtPickCapability`
- `HeldItemCapability`
- `SceneSelectorCapability`
- `LangToggleCapability`

### Why it matters

Today capabilities are partly attached at entry level, partly hardcoded in special entry subclasses, and partly routed through screen interfaces.

### Benefit

- More composable fields
- Less step-only wrapper code
- Removes ad-hoc exceptions like dedicated trailing render logic

### Cost

- Medium

### Risk

- Moderate, because trailing button rendering is currently partially specialized

### Recommended approach

First unify behavior for text fields. Leave XYZ fields for a second pass if needed.

## 5. Add First-Class Support for Dynamic Visibility and Collection Fields

### What to refactor

Add framework concepts for:

- visible when
- enabled when
- collection field
- dependent field
- rebuild-safe dynamic sections

### Why it matters

The code currently relies on "toggle state -> rebuild whole form -> restore snapshot" as the default dynamic behavior.

### Benefit

- Cleaner mode-based forms
- Fewer custom snapshot keys
- Block properties and URL lists can become normal framework features

### Cost

- Medium to high

### Risk

- Moderate

### Recommended approach

Start with collection fields and conditional visibility, because those are the two most repeated dynamic patterns.

## Concrete Repetition Hotspots

### `SetBlockScreen` and `SelectionOperationScreen`

These share highly similar logic for:

- direction options
- entrance animation options
- normalization of old values
- label generation
- custom snapshot state

Relevant files:

- `Common/src/main/java/com/nododiiiii/ponderer/ui/SetBlockScreen.java`
- `Common/src/main/java/com/nododiiiii/ponderer/ui/SelectionOperationScreen.java`

This is a strong candidate for extracting a reusable step feature or sub-spec.

### Block Property Snapshot Logic

`AbstractStepEditorScreen` already contains block property snapshot helpers:

- `snapshotBlockProps()`
- `restoreBlockProps()`
- `applyBlockPropsSnapshot()`

But `ShowInterfaceScreen` still duplicates property snapshot logic with:

- `snapshotProps()`
- `parseProps()`

This is a clear sign that collection-field support is missing from the framework.

### Status Message Duplication

`AbstractDeclarativeListScreen` has internal status fields, while `AbstractStepEditorScreen` also carries its own `errorMessage` and `infoMessage` and manually syncs them.

That means screen-local workflow status and framework-level UI status have not yet been unified.

### Custom Parsing Outside Base Helpers

`ClickInterfaceScreen` defines its own permissive `parseDouble(String raw)` for optional coordinates rather than using the base parser model.

This suggests the current parser helpers are too primitive for real use cases.

### `StepEditorEntry.rows()` Appears Unused

`StepEditorEntry` defines `rows()`, and every `StepEditorEntries` factory implements it, but the current build path in `AbstractDeclarativeFormScreen.collectEntries()` does not consume it.

This looks like either:

- a planned extension that never landed
- or a legacy concept that can be removed

## What Should Stay in Screens

These responsibilities should stay screen-local:

- mapping validated form values into `DslScene.DslStep`
- scene-specific persistence
- navigation to other screens
- screen-specific side effects such as saving, reloading, or opening pick workflows

## What Should Move Out of Screens

These should move into the framework:

- generic field construction
- form state tracking
- dirty detection
- parsing and validation
- conditional visibility
- collection item add/remove behavior
- field-local capabilities like JEI or pick buttons

## Small-Step Refactors vs Framework-Level Changes

### Good Small-Step Refactors

- Remove or confirm the role of `StepEditorEntry.rows()`
- Extract repeated direction and entrance animation helpers
- Reuse one shared block-property snapshot utility
- Introduce reusable validators without changing screen structure yet
- Unify JEI activation helpers between `AiGenerateScreen` and `CommandParamScreen`

### Framework-Level Changes

- Unifying the form description model
- Introducing shared `FormState`
- Replacing button-spec families with capabilities
- Adding collection-field and conditional-visibility primitives

## Recommended Migration Order

1. Extract a shared `FormState` abstraction from `AbstractStepEditorScreen`
2. Introduce validator/parser components for the repeated step-editor cases
3. Consolidate block-property and dynamic-list handling into reusable collection support
4. Unify text-field button actions into one capability system
5. Rebuild `FormEntries` and `StepEditorEntries` on top of one common field-spec layer
6. Migrate the most repetitive step screens first:
   - `SetBlockScreen`
   - `SelectionOperationScreen`
   - `ReplaceBlocksScreen`
   - `ModifyBlockEntityNbtScreen`
7. Migrate special forms with their own mini-frameworks:
   - `CommandParamScreen`
   - `AiGenerateScreen`
   - `TriggerEditorScreen`

## Suggested Target Layering

Recommended target layering:

- `ui/framework/core`
  - form state
  - dirty tracking
  - snapshot restore
- `ui/framework/spec`
  - field specs
  - section specs
  - visibility and dependency rules
- `ui/framework/binding`
  - text binding
  - numeric binding
  - enum binding
  - collection binding
- `ui/framework/validation`
  - parsers
  - validators
  - validation results
- `ui/framework/capability`
  - JEI
  - point pick
  - NBT pick
  - held item
  - language toggle
  - scene selector
- `ui/framework/render`
  - actual entry widgets and layout
- `ui/framework/adapter`
  - step adapter
  - config adapter
  - command parameter adapter

## Suggested Target API Shape

One possible target API could look like this:

```java
FormSpec.create()
    .section("basic", section -> section
        .text("block", bind.string(step::getBlock, step::setBlock))
            .label("ponderer.ui.set_block")
            .hint("ponderer.ui.set_block.hint")
            .validate(validators.blockId())
            .capability(capabilities.jei(IdFieldMode.BLOCK))
            .capability(capabilities.nbtPick("nbt"))
        .xyz("pos", bind.int3(...))
            .label("ponderer.ui.set_block.pos_from")
            .capability(capabilities.pick(PickState.TargetField.POS1))
        .toggle("smartDisplay", bind.bool(...))
            .label("ponderer.ui.smart_display"))
    .visibleWhen("animated", state -> state.enumValue("entranceMode") == ANIMATED);
```

The key point is not the exact syntax. The key point is:

- one description model
- one state model
- one capability model
- adapters for different business domains

## Final Recommendation

If only one framework change is made first, it should be this:

Introduce a shared `FormState + FieldSpec + FieldBinding` foundation and make both `FormEntries` and `StepEditorEntries` become thin facades over it.

That single move will make the later cleanup of validation, dynamic behavior, and field capabilities much easier, while avoiding a full rewrite up front.
