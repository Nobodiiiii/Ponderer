# NBT Editor Expansion Plan

## Summary

This document captures the implementation plan for adding an NBT parser-backed expanded editor to the existing Ponderer UI, without changing the functional target defined below.

Target behavior:

- Add an NBT parser to NBT input fields.
- Add a trailing expand button with label `V` on the right side of each real NBT row.
- Clicking `V` opens a dedicated NBT editor screen.
- The new screen only contains:
  - a left label with text `NBT`
  - a scrollable multiline input box on the right
  - a reserved action slot on the right side of the text area
- The multiline input box must be prefilled with parsed, formatted, indented NBT text.
- Rows that are inferred to represent coordinates must show a point-pick button on the right side, and that action must return to the Ponder scene for picking.
- The new screen should reuse already abstracted existing UI infrastructure as much as possible.

This scope applies to scene-editor NBT fields:

- `TriggerEditorScreen.itemNbtField`
- step editor NBT fields such as:
  - `SetBlockScreen`
  - `ModifyBlockEntityNbtScreen`
  - `ModifyEntitiesNbtScreen`
  - `CreateEntityScreen`
  - `CreateItemEntityScreen`
  - `ShowControlsScreen`

Out of scope for this phase:

- command/function pages outside the scene editor
- changing unrelated non-NBT form behavior

## Existing Reuse Points

The implementation should prefer reuse over parallel systems:

- Reuse `AbstractSceneEditorFormScreen` for snapshot restore, save/cancel flow, status messages, and return navigation.
- Reuse `FieldDecorators` and the existing trailing-button pattern for the `V` expand button.
- Reuse `SnapshotReturnContext`-based reopen flow for leaving and returning to editors.
- Reuse existing Ponder point-pick navigation patterns from `PickState`, `CoordPickState`, and similar scene-return flows.
- Reuse existing scene targeting logic when reopening the correct Ponder scene.

## Implementation Design

### 1. Shared NBT text pipeline

Add a shared NBT text pipeline in `Common`:

- `NbtTextCodec`
  - parse raw SNBT text with vanilla `TagParser`
  - normalize validation errors
  - compact formatted text back to single-line SNBT for parent-field writeback
- `NbtPrettyPrinter`
  - format parsed NBT into readable multiline indented SNBT
  - preserve valid vanilla SNBT semantics
  - generate stable line metadata so visible rows can be mapped back to AST paths
- `NbtCoordinateDetector`
  - detect coordinate-like nodes and their editable locations
  - expose enough metadata for a row-level pick button to know what value to rewrite

Important constraint:

- Parsing authority must remain vanilla `TagParser`.
- Formatting must theoretically support all vanilla NBT syntax accepted by `TagParser`.

### 2. Expanded editor screen

Add a dedicated screen, tentatively `NbtExpandedEditorScreen`.

Required layout:

- left label: `NBT`
- right: multiline text editor
- right gutter/action slot aligned to visible rows

Required behavior:

- On open:
  - if current NBT is valid, parse and pretty-print it into multiline indented text
  - if current NBT is empty, show empty text
  - if current NBT is invalid, keep raw text visible, show error state, and disable save until fixed
- On save:
  - parse the current multiline content
  - if parse succeeds, compact to normalized single-line SNBT
  - write back to the original parent field snapshot key
  - return to the original editor
- On cancel/back:
  - restore parent editor without mutating the original field value

Implementation preference:

- Reuse `AbstractSceneEditorFormScreen` chrome and lifecycle.
- Do not force the multiline editor into existing single-line `FieldSpecs.text`.
- Use vanilla multiline editing support for scrolling and cursor behavior.

### 3. Expand button on NBT rows

Add a new decorator such as `FieldDecorators.nbtExpand(snapshotKey)`.

Rules:

- Button label must be `V`.
- It must only appear on actual NBT rows, not on adjacent item/block/entity ID rows that happen to interact with NBT.
- It should sit alongside existing trailing buttons such as NBT/world pick buttons.

### 4. Point-pick integration inside the expanded editor

Add a dedicated return/pick flow for the expanded NBT editor.

Required behavior:

- If a visible row maps to an inferred coordinate node, show a point-pick button in the right gutter for that row.
- Clicking that button:
  - opens the current Ponder scene
  - lets the user pick a point
  - returns to the expanded NBT editor, not directly to the original parent form
- After return:
  - the corresponding NBT node is rewritten
  - the multiline text is refreshed from the updated parsed model
  - scroll position should be preserved when practical

Prefer sharing scene-open logic with current point-pick systems instead of duplicating scene lookup/navigation logic.

## Coordinate Detection Rules

The requested strategy is intentionally broad.

Treat all of the following as coordinate candidates:

- compound objects containing numeric `x`, `y`, `z`
- compound objects containing numeric `X`, `Y`, `Z`
- any three-element numeric `ListTag`
- any three-element numeric array tag such as `[I; ...]`, `[L; ...]`, `[B; ...]`

Expected writeback behavior:

- integer coordinate nodes should write back integer values
- floating-point coordinate nodes should write back floating-point values
- face-aware center offsets should follow the same semantics already used by current point-pick workflows where applicable

Known tradeoff:

- This aggressive strategy may surface pick buttons for some three-value numeric lists that are not semantic world coordinates.
- That tradeoff is accepted for this iteration.

## Acceptance Requirements

The implementation is only acceptable if all of the following are true:

1. Every targeted scene-editor NBT row shows a `V` expand button.
2. The expanded NBT screen matches the requested minimal structure.
3. The multiline box is scrollable.
4. Parsed valid NBT is displayed as formatted, indented text.
5. Invalid NBT is rejected on save with a clear error state.
6. Coordinate-like rows show pick buttons and can round-trip through the Ponder scene pick flow.
7. The original single-line field receives normalized single-line SNBT after saving from the expanded editor.
8. The implementation should theoretically support formatting and indentation for all vanilla NBT syntax accepted by the parser.
9. All tests listed in this document must pass.

## Required Tests

All tests in this section must pass. No partial pass is acceptable.

### Real user-provided test case

This exact NBT input must parse successfully, format successfully, and round-trip successfully:

```snbt
{EnergyContainers:[],ForgeCaps:{},Items:[],activeState:0b,componentConfig:{config0:{side0:1,side1:1,side2:1,side3:1,side4:1,side5:1},config6:{side0:1,side1:1,side2:4,side3:8,side4:1,side5:1},eject0:0b,eject6:0b},componentEjector:{color0:-1,color1:-1,color2:-1,color3:-1,color4:-1,color5:-1,strictInput:0b},componentFrequency:{Security:{name:"Security",owner:[I;-1520936433,-167885378,-1212611829,-795432156],publicFreq:1b}},componentSecurity:{owner:[I;-1520936433,-167885378,-1212611829,-795432156],securityMode:0},componentUpgrade:{Items:[],upgrades:[]},controlType:0,currentRedstone:0,progress:[I;0,0,0],redstone:0b,sorting:0b,updateDelay:0}
```

Mandatory assertions for this case:

- parse succeeds
- pretty-print succeeds
- compact writeback succeeds
- reparse after compact writeback succeeds
- no field is lost during round-trip
- `progress:[I;0,0,0]` is recognized as a coordinate candidate under the current aggressive rule set

### Added parser and formatter test cases

#### Case 1: basic nested compound

```snbt
{foo:1b,bar:{baz:"ok",list:[1,2,3]}}
```

Must pass:

- parse
- pretty-print
- compact round-trip

#### Case 2: mixed numeric suffixes

```snbt
{a:1b,b:2s,c:3,d:4L,e:5.0f,f:6.25d}
```

Must pass:

- parse
- pretty-print
- compact round-trip
- numeric suffixes preserved semantically

#### Case 3: escaped string and unicode text

```snbt
{text:"line1\\nline2",name:"测试",quoted:"\\\"demo\\\""}
```

Must pass:

- parse
- pretty-print
- compact round-trip
- string escaping remains valid

#### Case 4: empty structures

```snbt
{emptyCompound:{},emptyList:[],emptyIntArray:[I;],emptyLongArray:[L;]}
```

Must pass:

- parse
- pretty-print
- compact round-trip

#### Case 5: explicit xyz compound coordinate

```snbt
{x:12,y:64,z:-3}
```

Must pass:

- parse
- pretty-print
- coordinate candidate detection
- point-pick rewrite updates the same node

#### Case 6: uppercase XYZ compound coordinate

```snbt
{X:1,Y:2,Z:3,Name:"Upper"}
```

Must pass:

- parse
- pretty-print
- coordinate candidate detection

#### Case 7: floating point position list

```snbt
{Pos:[1.5d,64.0d,-8.25d]}
```

Must pass:

- parse
- pretty-print
- coordinate candidate detection
- point-pick rewrite preserves floating-point representation policy

#### Case 8: array-style coordinate candidate

```snbt
{spawn:[I;100,64,-20]}
```

Must pass:

- parse
- pretty-print
- coordinate candidate detection

#### Case 9: nested coordinate candidates

```snbt
{outer:{inner:{x:7,y:8,z:9}},path:[{pos:[0,1,2]},{pos:[3,4,5]}]}
```

Must pass:

- parse
- pretty-print
- multiple coordinate candidates detected
- row-to-node mapping stays stable after formatting

#### Case 10: non-coordinate three-value semantic collision

```snbt
{Rotation:[0f,90f],Color:[255,128,64]}
```

Must pass under the current accepted aggressive strategy:

- parse
- pretty-print
- the implementation may expose coordinate pick buttons for three-value numeric lists
- behavior must remain stable and not corrupt unrelated syntax

### Added invalid-input test cases

These must fail validation cleanly and must not save:

#### Invalid 1

```snbt
{foo:}
```

#### Invalid 2

```snbt
{bar:[1,2,}
```

#### Invalid 3

```snbt
{unterminated:"abc}
```

Invalid-case requirements:

- parse fails
- save is blocked
- user remains in expanded editor
- original source field is not overwritten

## Verification Expectation

When implementation starts, verification must include at minimum:

- automated tests for parser/formatter/detector behavior covering every case above
- targeted compile checks:
  - `:Common:compileJava`
  - `:Forge:compileJava`
  - `:Fabric:compileJava`

The change is not considered complete until all listed tests pass.


最后总结时，应该使用中文回答我
