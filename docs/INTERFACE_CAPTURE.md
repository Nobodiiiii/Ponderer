# Generic interface capture (1.20.1)

In the `show_interface` editor, choose **Capture from world**. Open that interface
normally; its source is detected automatically, independently of the manual source
selector or a previous capture. After one second the editor
returns with a captured descriptor; save the step as usual. Escape or a 60-second
timeout cancels without replacing the previous descriptor. Disconnect clears the
capture. **Use manual settings** discards the captured descriptor.

Capture observes ordinary menu construction on the client. Vanilla menu types are
recorded directly. Forge and Fabric copy their extra-data buffers without moving
the reader index; both 1.20.1 factories accept a FriendlyByteBuf directly.
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
not copied. Items retain counts and native 1.20.1 NBT; 1.21 data components are
not available. Captured target NBT uses the existing identity stripping/remapping.
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

Manual AE2 block interfaces also have a mod-level fallback after the normal
`MenuProvider` strategy. It discovers the host types retained by AE2's
`MenuTypeBuilder` registrations, generates opening data with AE2's own locators
and initial-data serializers, then calls the existing loader client factory.
There is no block-to-menu id table. More specific host types take precedence;
terminal/bus `getMenuType` overrides select their primary menu. Cable-bus targets
use the saved hit position to select a part from the isolated block entity.
Factories must bind that exact sandbox host; automatic selection excludes
submenus and rejects ambiguous main menus instead of opening an arbitrary one.

AE2 remains optional. The bridge reflects AE2's private registry/builder metadata
and uses class literals for Minecraft parameter types. Its source reference is
AE2 `forge/v15.4.10` (`MenuLocator`, `MenuLocators`, and `MenuTypeBuilder`),
checked against matching upstream source because `ref/1.20.1` has no AE2 checkout;
upstream API changes can require updating the bridge. This constructs interfaces from local state; live
ME network contents, server-driven submenus and custom synchronization are not
simulated. Captured opening data keeps precedence over this fallback.
This is a world API bridge, not an isolation boundary for arbitrary mod code that
holds real chunk references, modifies globals, or defers work outside the scope.

Captured interfaces default to viewing. Menu creation does not reproduce custom
server sync, machinery simulation, remote networks, or server-created submenus.
Those remain separate capabilities. Entity targets and client-only screens are
not part of this capture flow. Existing manual sources remain available.

Validation on 2026-10-22:

- `:Common:build :Forge:build :Fabric:build --offline --console=plain` passed.
- Existing Common JUnit: 52 passed, two opt-in real-LLM cases skipped.
- `quickPlaySmoke -PskipBuild -PsmokeTimeout=90 -PpythonExecutable=C:/Python313/python.exe --offline --console=plain`
  passed world entry for both clients and stopped their processes automatically.
- Both packaged refmaps were inspected: Forge emits mapped `m_*` / `f_*` names;
  Fabric emits intermediary `class_*` / `method_*` names. Third-party methods with
  mapped Minecraft descriptors remain remapped, using supplemental Forge mappings.
- This verifies builds, startup and world entry. Capture/replay, AE2 menus, JEI
  interaction and individual UI actions have not been exercised on this port.
  The 1.21.1 runtime test helper was not added, following this repository's rule
  against adding tests or test-only scripts. Upstream 1.21.1 runtime results do
  not constitute runtime validation of this port.

Local API references used:

- `ref/1.20.1/Ponder/Common/src/main/java/net/createmod/catnip/levelWrappers/SchematicLevel.java`
- `ref/1.20.1/Ponder/Common/src/main/java/net/createmod/catnip/config/ui/ConfigScreenList.java`
- `ref/1.20.1/JustEnoughItems/CommonApi/src/main/java/mezz/jei/api/gui/handlers/IGuiProperties.java`
- Matching Minecraft / Forge 1.20.1 source jars from the local Gradle cache.
- AE2 upstream source at `forge/v15.4.10`: `MenuTypeBuilder`, `MenuOpener`,
  `MenuLocator`, `MenuLocators`, and `AEBaseMenu` (downloaded under ignored `build/`).

See [the port report](PORT_1.11_TO_1.20.1.md) for scope and generation differences.
