# Generic interface capture (1.21.1)

In the `show_interface` editor, choose **Capture from world**. Open that interface
normally; its source is detected automatically, independently of the manual source
selector or a previous capture. After one second the editor
returns with a captured descriptor; save the step as usual. Escape or a 60-second
timeout cancels without replacing the previous descriptor. Disconnect clears the
capture. **Use manual settings** discards the captured descriptor.

Capture observes ordinary menu construction on the client. Vanilla menu types are
recorded directly. NeoForge copies the extra-data buffer without moving its reader
index; Fabric re-encodes the decoded opening object with the registered menu codec.
Only the current opt-in capture is observed; the server thread and sandbox replay
are excluded. Opening data is limited to 1 MiB. There is no mod-name dispatch or
Mekanism dependency in this path.

The block's consumed interaction identifies ordinary block menus. During synchronous
menu factory construction, the first successful real-level block entity lookup takes
precedence, including openings with no client click hint or a proxy block. Factory
scope is balanced across nested vanilla/loader calls and cleared on the next tick if
a constructor throws. A held item is recorded only from an actual use-hand callback;
without a block or held-item source, the descriptor is a menu target. Merely aiming
at a block does not identify it as the source.

The descriptor contains the menu type, optional Base64 opening bytes, source block
coordinates (`lookupOrigin`), client-visible block entity NBT, adjacent block states
(radius 1), non-player slot contents, and basic player context. This is a snapshot
of what the client knows, not a server-side NBT export. The player's inventory is
not copied. Captured target NBT uses the existing identity stripping/remapping.
Opaque mod opening bytes cannot be semantically scrubbed or made version-neutral;
use compatible mod versions when sharing a capture.
The opening codec's registry IDs must also be compatible with the replay environment.

Captured extended menus use the loader's client factory first. A render-thread
sandbox scope redirects `Level` block/entity/fluid/chunk lookups and direct
`ClientChunkCache` chunk lookups. Chunk facades read the same isolated block map.
Block writes are redirected, foreign block entities are not attached, and real
entity lookup returns no entity. The actual `Minecraft.level` reference is never
replaced and no new `ClientLevel` or network listener is constructed. Outside the
scope, including other threads, normal game behavior is unchanged.

Opening bytes remain opaque: source-coordinate queries within 128 blocks of the
captured origin map to the far sandbox origin. This handles codecs that encode
positions as raw longs or custom objects without guessing byte offsets. Only
copied context exists there; uncaptured queries do not fall through to real blocks.
This is a world API bridge, not an isolation boundary for arbitrary mod code that
holds real chunk references, modifies globals, or defers work outside the scope.

Captured interfaces default to viewing. Menu creation does not reproduce custom
server sync, machinery simulation, remote networks, or server-created submenus.
Those remain separate capabilities. Entity targets and client-only screens are
not part of this capture flow. Existing manual sources remain available.

Validation:

- `:Common:test`, `:NeoForge:build`, `:Fabric:build`.
- `quickPlaySmoke -PskipBuild -PsmokeInterfaces=true -PsmokeTimeout=180` exercises
  both clients with automatic timeout and process cleanup.
- Runtime cases cover world/chunk aliases, isolated writes, thread/exception
  scoping, real vanilla menu open/sync/capture/replay, and optional installed
  Mekanism metallurgic infuser / energized smelter client factories. If an infuser is
  within four blocks of the player in singleplayer, additional cases recapture it
  with a stale held-item descriptor, then open it from the server without client
  interaction hints. Both must preserve source coordinates/NBT/opening bytes and
  replay through the client factory. Mekanism IDs
  appear only in these optional test fixtures.

The initial 2026-10-01 smoke passed but missed a real capture regression: the editor's
held-item preset caused a placed metallurgic infuser to be saved as an item menu,
without `lookupOrigin` or block NBT. The real failure was a lookup at `(82,34,286)`
in the Nether. The additional capture cases above cover this path, rather than only
constructing a correct descriptor by hand.

Verified for the fix on 2026-10-01: both loader builds passed; Common JUnit reported
60 passed and one opt-in real-LLM test skipped. Both `quickPlaySmoke` clients passed.
NeoForge exercised both real capture cases on the original Nether machine, and the
captured descriptor replayed as `GuiMetallurgicInfuser` inside the original failed
Ponder scene. The separate PonderUI run passed with
`python test.py neoforge --skip-build --smoke --smoke-interfaces --smoke-ponder-item minecraft:oak_log --smoke-timeout 180`.
That check honors the captured descriptor's default view-only interaction setting.
Fabric has no installed Mekanism fixture; it passed the vanilla capture/replay and
isolation cases. All smoke processes were stopped by the test harness.

The failed descriptor's opening bytes were preserved; a real recapture supplied its
missing block context. The repaired scene is saved in the local
`build/interface-capture-regression/oak_log.repaired.json`. Both current client and
world copies of `oak_log.json` were restored after temporarily verifying that scene;
later user edits were not replaced. Existing incomplete captures need recapturing.

Fabric's generated refmap contains the expected intermediary method and descriptor
names for all new hooks. This NeoForge 1.21.1 toolchain emits no refmap and uses
official runtime names; the packaged injections were verified by the NeoForge run.

Local API references used:

- `ref/1.21.1/Mekanism/src/main/java/mekanism/common/inventory/container/type/MekanismContainerType.java`
- `ref/1.21.1/Mekanism/src/main/java/mekanism/common/util/WorldUtils.java`
- `ref/1.21.1/Ponder/Common/src/main/java/net/createmod/catnip/levelWrappers/SchematicLevel.java`
- `NeoForge/build/moddev/artifacts/neoforge-21.1.219-sources.jar` (`MenuType`, `Level`, `ClientChunkCache`, `BlockBehaviour.BlockStateBase`).
