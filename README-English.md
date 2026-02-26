## English

Ponderer is a NeoForge 1.21.1 mod that provides data-driven Ponder scene authoring, in-game editing, hot-reload, and client/server sync.

### Requirements
- Minecraft 1.21.1
- NeoForge 21.1.219+
- Ponder 1.0.60
- Flywheel 1.0.4
- Java 21

### Key Features
- JSON DSL scene definition in `config/ponderer/scripts/`
- In-game scene editor (add/edit/delete/reorder/copy-paste steps, insert at any position, Ctrl+Z/Y undo/redo, pick coordinates directly from the scene)
- Custom structure loading from `config/ponderer/structures/`
- The default blueprint carrier item is `paper`, with a built-in matching guide scene; hold a `writable_book` to view the demo scene directly
- NBT-based scene filtering via `nbtFilter`
- Bidirectional PonderJS conversion (import/export)
- Client-server pull/push with conflict handling
- Scene pack export/import (ZIP format for easy sharing)
- Item list UI for all registered ponder items
- JEI integration: click or drag-drop items from JEI to fill in ID fields (optional dependency)
- Block state properties: specify BlockState properties (e.g. facing, half) when placing/replacing blocks
- Extended entity resolution: boats, minecarts, armor stands can be dragged into entity fields via JEI

### Commands
- `/ponderer reload`: Reload local scene files and refresh the ponder index.
- `/ponderer pull`: Pull server changes in conflict-check mode.
- `/ponderer pull force`: Force server version to overwrite local data.
- `/ponderer pull keep_local`: Pull while preferring to keep local changes.
- `/ponderer push`: Push local scenes to server in conflict-check mode.
- `/ponderer push force`: Force overwrite scenes on the server.
- `/ponderer push <id>`: Push only the specified scene ID.
- `/ponderer push force <id>`: Force-push and overwrite only the specified scene ID.
- `/ponderer download <id>`: Import the specified structure into Ponderer structures.
- `/ponderer new hand`: Create a new scene from the main-hand item.
- `/ponderer new hand use_held_nbt`: Create from main-hand item with current held NBT.
- `/ponderer new hand <nbt>`: Create from main-hand item with explicit NBT.
- `/ponderer new <item>`: Create a new scene for the specified item.
- `/ponderer new <item> <nbt>`: Create a new scene for item + explicit NBT.
- `/ponderer copy <id> <target_item>`: Copy a scene and retarget it to another item.
- `/ponderer delete <id>`: Delete the specified scene.
- `/ponderer delete item <item_id>`: Delete all scenes under one item.
- `/ponderer list`: Open the ponder item list UI.
- `/ponderer convert to_ponderjs all`: Convert all scenes to PonderJS.
- `/ponderer convert to_ponderjs <id>`: Convert one scene to PonderJS.
- `/ponderer convert from_ponderjs all`: Import all scenes back from PonderJS.
- `/ponderer convert from_ponderjs <id>`: Import one scene back from PonderJS.
- `/ponderer export [filename]`: Export all scripts and structures as a ZIP file to `config/ponderer/`.
- `/ponderer import <filename>`: Import scripts and structures from a ZIP file in `config/ponderer/`.

### Build
```bash
./gradlew build
./gradlew runClient
```

### Q&A
**Q: Why not use PonderJS directly?**

**A:** PonderJS does not provide hot-reload in this workflow, which makes iteration slower. Directly transmitting JS scripts also introduces additional security risks. Ponderer uses a safer data transfer approach and still provides bidirectional conversion with PonderJS, so you can switch workflows when needed (with a few APIs that are not natively supported by PonderJS).

### License
MIT
