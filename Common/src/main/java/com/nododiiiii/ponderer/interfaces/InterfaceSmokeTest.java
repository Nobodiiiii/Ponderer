package com.nododiiiii.ponderer.interfaces;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.interfaces.sandbox.Fidelity;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxCoordinates;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxNetworkFirewall;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxScreenGate;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxScope;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxWorldBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.nio.ByteBuffer;
import java.util.Base64;
import com.nododiiiii.ponderer.interfaces.capture.InterfaceCapture;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptorJson;
import com.google.gson.JsonParser;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import java.util.Map;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.ui.ShowInterfaceScreen;
import com.nododiiiii.ponderer.ui.SceneEditorScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-game self test for the interface sandbox (design doc 20.2), enabled with
 * {@code -Dponderer.smoke.interfaces=true}. After world entry it opens a set of interfaces in
 * sandboxes, performs a real click, checks the isolation invariants, screenshots a sandbox
 * interface and (with {@code -Dponderer.smoke.interfaces.ponder=<item id>}) a PonderUI scene,
 * then logs {@code [PondererSmoke] interfaces PASS} or {@code FAIL <reason>}.
 */
public final class InterfaceSmokeTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("ponderer.smoke.interfaces");
    private static final String PONDER_ITEM = System.getProperty("ponderer.smoke.interfaces.ponder", "");

    private enum Phase { WAIT_WORLD, CAPTURE, WORLD_CAPTURE, FACTORY_CAPTURE, CASES, SANDBOX_SCREEN, MOD_SCREEN, EDITOR, PONDER, DONE }

    private static Phase phase = Phase.WAIT_WORLD;
    private static int ticks;
    private static final List<String> failures = new ArrayList<>();
    @Nullable
    private static SandboxSession shown;
    private static InterfaceDescriptor capturedRoundTrip;
    private static boolean captureReturned;
    private static InterfaceDescriptor modPreview;
    private static com.google.gson.JsonObject capturedJson;
    private static BlockPos worldCapturePos;
    private static String worldCaptureBlock;
    private static boolean ponderTryEnabled;

    private InterfaceSmokeTest() {
    }

    public static void tick() {
        if (!ENABLED || phase == Phase.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            switch (phase) {
                case WAIT_WORLD -> {
                    if (mc.level == null || mc.player == null) {
                        ticks = 0;
                    } else if (ticks > 60 && mc.screen == null) {
                        startCaptureCase(mc);
                        phase = Phase.CAPTURE;
                        ticks = 0;
                    }
                }
                case CAPTURE -> {
                    if (captureReturned) {
                        check(capturedRoundTrip != null, "capture did not return a descriptor");
                        if (capturedRoundTrip != null) {
                            check(capturedRoundTrip.targetKind == InterfaceDescriptor.TargetKind.MENU,
                                "an unrelated aimed-at block was incorrectly recorded as the menu source");
                            check(capturedRoundTrip.slots.stream().anyMatch(slot -> slot.index() == 0 && slot.item().id().equals("minecraft:diamond") && slot.item().count() == 5),
                                "capture did not observe server slot sync");
                            expect("capture round trip", capturedRoundTrip, Fidelity.INTERACTIVE, null, session ->
                                check(session.menu().getSlot(0).getItem().is(Items.DIAMOND), "captured slot was lost on replay"));
                        }
                        phase = startWorldCaptureCase(mc, false) ? Phase.WORLD_CAPTURE : Phase.CASES;
                        ticks = 0;
                    } else if (ticks > 400) {
                        failures.add("capture round trip timed out");
                        finish();
                    }
                }
                case WORLD_CAPTURE, FACTORY_CAPTURE -> {
                    if (captureReturned) {
                        check(capturedRoundTrip != null, "world capture did not return a descriptor");
                        if (capturedRoundTrip != null) {
                            check(capturedRoundTrip.isBlockTarget() && worldCaptureBlock.equals(capturedRoundTrip.block),
                                "world capture kept the old held-item source");
                            check(java.util.Arrays.equals(capturedRoundTrip.lookupOrigin,
                                new int[]{worldCapturePos.getX(), worldCapturePos.getY(), worldCapturePos.getZ()}),
                                "world capture lost the factory's source coordinates");
                            check(capturedRoundTrip.nbt != null, "world capture lost block entity NBT");
                            check(capturedRoundTrip.extraData != null, "world capture lost extended opening bytes");
                            expect(phase == Phase.WORLD_CAPTURE ? "block interaction capture" : "factory lookup capture",
                                capturedRoundTrip, Fidelity.INTERACTIVE, "client_factory", null);
                            modPreview = capturedRoundTrip;
                        }
                        if (phase == Phase.WORLD_CAPTURE) {
                            startWorldCaptureCase(mc, true);
                            phase = Phase.FACTORY_CAPTURE;
                        } else {
                            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("ponderer-smoke-world-capture.json"), capturedJson.toString());
                            phase = Phase.CASES;
                        }
                        ticks = 0;
                    } else if (ticks > 400) {
                        failures.add("world capture timed out in " + phase);
                        finish();
                    }
                }
                case CASES -> {
                    runCases(mc);
                    shown = SandboxSession.open(chest(), null);
                    mc.setScreen(new HostScreen(shown));
                    phase = Phase.SANDBOX_SCREEN;
                    ticks = 0;
                }
                case SANDBOX_SCREEN -> {
                    if (ticks == 20) {
                        screenshot(mc, "ponderer-smoke-sandbox.png");
                    } else if (ticks > 25) {
                        mc.setScreen(null);
                        if (shown != null) {
                            shown.destroy("smoke");
                        }
                        if (modPreview != null) {
                            shown = SandboxSession.open(modPreview, null);
                            mc.setScreen(new HostScreen(shown));
                            phase = Phase.MOD_SCREEN;
                        } else if (PONDER_ITEM.isBlank()) {
                            openEditorCase(mc);
                        } else {
                            mc.setScreen(PonderUI.of(ResourceLocation.parse(PONDER_ITEM)));
                            phase = Phase.PONDER;
                        }
                        ticks = 0;
                    }
                }
                case MOD_SCREEN -> {
                    if (ticks == 20) screenshot(mc, "ponderer-smoke-mod-interface.png");
                    if (ticks > 25) {
                        check(shown.fidelity() == Fidelity.INTERACTIVE, "mod GUI failed during render/tick: " + shown.reports());
                        mc.setScreen(null);
                        shown.destroy("smoke mod preview");
                        if (PONDER_ITEM.isBlank()) openEditorCase(mc);
                        else { mc.setScreen(PonderUI.of(ResourceLocation.parse(PONDER_ITEM))); phase = Phase.PONDER; }
                        ticks = 0;
                    }
                }
                case EDITOR -> {
                    if (ticks == 10) screenshot(mc, "ponderer-smoke-capture-editor.png");
                    if (ticks == 15) {
                        DslScene scene = new DslScene();
                        mc.setScreen(new ShowInterfaceScreen(scene, 0, new SceneEditorScreen(scene, 0)));
                    }
                    if (ticks == 25) screenshot(mc, "ponderer-smoke-capture-button.png");
                    if (ticks > 30) { mc.setScreen(null); finish(); }
                }
                case PONDER -> {
                    if (ticks == 80) {
                        Screen embedded = InterfaceHost.activeScreen();
                        LOGGER.info("[PondererSmoke] PonderUI embedded interface: {}",
                            embedded == null ? "none" : embedded.getClass().getName());
                        if (embedded == null) {
                            failures.add("PonderUI scene showed no embedded interface");
                        }
                        SandboxSession session = mc.screen instanceof PonderUI ui ? InterfaceHost.sessionOf(ui.getActiveScene()) : null;
                        check(session != null && session.fidelity() == Fidelity.INTERACTIVE, "PonderUI interface did not reach interactive fidelity");
                        ponderTryEnabled = session != null && session.canInteract();
                        screenshot(mc, "ponderer-smoke-ponder.png");
                        InterfaceHost.enterTry();
                        check(InterfaceHost.mode() == (ponderTryEnabled ? InterfaceHost.Mode.TRY : InterfaceHost.Mode.WATCH),
                            "try mode did not respect the descriptor's interaction setting");
                    } else if (ticks == 86) {
                        screenshot(mc, "ponderer-smoke-ponder-try.png");
                        InterfaceHost.exitTry();
                        check(InterfaceHost.mode() == InterfaceHost.Mode.WATCH, "could not leave try mode");
                        check(InterfaceHost.activeScreen() != null, "reset on leaving try mode lost the interface");
                    } else if (ticks > 90) {
                        mc.setScreen(null);
                        finish();
                    }
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[PondererSmoke] interfaces self-test threw", t);
            failures.add("threw " + t);
            finish();
        }
    }

    private static void finish() {
        phase = Phase.DONE;
        if (failures.isEmpty()) {
            LOGGER.info("[PondererSmoke] interfaces PASS (intercepted={}, taintDrops={})",
                SandboxNetworkFirewall.interceptedCount(), SandboxNetworkFirewall.taintDropCount());
        } else {
            LOGGER.error("[PondererSmoke] interfaces FAIL {}", String.join(" | ", failures));
        }
    }

    private static void startCaptureCase(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) { captureReturned = true; return; }
        BlockPos source = mc.player.blockPosition();
        InterfaceCapture.start(Map.of(), snapshot -> {
            String raw = snapshot.get(InterfaceCapture.SNAPSHOT_KEY);
            if (raw != null) {
                capturedJson = JsonParser.parseString(raw).getAsJsonObject();
                capturedRoundTrip = InterfaceDescriptorJson.parse(capturedJson);
            }
            captureReturned = true;
        });
        var hit = mc.hitResult;
        try {
            mc.hitResult = new BlockHitResult(Vec3.atCenterOf(source), Direction.UP, source, false);
            InterfaceCapture.beforeUse();
        } finally { mc.hitResult = hit; }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return;
            // A transient container: exercises real open/sync/close packets without changing any blocks or player items.
            SimpleContainer inventory = new SimpleContainer(27);
            inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
            player.openMenu(new SimpleMenuProvider((id, inv, who) -> ChestMenu.threeRows(id, inv, inventory), Component.literal("Capture smoke")));
        });
    }

    /** Optional real-world regression: recapture a machine after selecting a held-item source. */
    private static boolean startWorldCaptureCase(Minecraft mc, boolean serverOnly) {
        if (!serverOnly) {
            BlockPos center = mc.player.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -4, -4), center.offset(4, 4, 4))) {
                String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
                if (id.equals("mekanism:metallurgic_infuser")) {
                    worldCapturePos = pos.immutable();
                    worldCaptureBlock = id;
                    break;
                }
            }
            if (worldCapturePos == null || mc.getSingleplayerServer() == null) {
                LOGGER.info("[PondererSmoke] world capture fixture unavailable; skipped");
                return false;
            }
        }
        captureReturned = false;
        capturedRoundTrip = null;
        // Both the manual selector and a previously misclassified capture used to force item mode.
        String previous = "{\"schema\":2,\"target\":{\"kind\":\"item\",\"mode\":\"menu\",\"item\":{\"id\":\""
            + worldCaptureBlock + "\",\"count\":1}}}";
        InterfaceCapture.start(Map.of("interface_source", "1", InterfaceCapture.SNAPSHOT_KEY, previous), snapshot -> {
            String raw = snapshot.get(InterfaceCapture.SNAPSHOT_KEY);
            check("ponderer.interface.capture.done".equals(snapshot.get(InterfaceCapture.NOTICE_KEY)), "world capture did not complete");
            if (raw != null) {
                capturedJson = JsonParser.parseString(raw).getAsJsonObject();
                capturedRoundTrip = InterfaceDescriptorJson.parse(capturedJson);
            }
            captureReturned = true;
        });
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(worldCapturePos), Direction.UP, worldCapturePos, false);
        if (serverOnly) {
            // No client use/hit/held-item hints: the real opening packet's factory must identify its source.
            var uuid = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                if (player != null) player.level().getBlockState(worldCapturePos).useWithoutItem(player.level(), player, hit);
            });
        } else {
            InterfaceCapture.beforeUse();
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        }
        return true;
    }

    private static void openEditorCase(Minecraft mc) {
        DslScene fixtureScene = new DslScene();
        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "show_interface";
        step.interfaceSpec = capturedJson;
        class EditorProbe extends ShowInterfaceScreen {
            EditorProbe() { super(fixtureScene, 0, new SceneEditorScreen(fixtureScene, 0), 0, step); }
            DslScene.DslStep result() { return buildStep(); }
        }
        EditorProbe editor = new EditorProbe();
        mc.setScreen(editor);
        check(capturedJson != null && capturedJson.equals(editor.result().interfaceSpec), "editor lost captured descriptor");
        editor.setPendingPickRestore(Map.of(InterfaceCapture.SNAPSHOT_KEY, capturedJson.toString()));
        // Re-init also exercises the return-from-capture snapshot route used by StepEditorContext.
        editor.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        check(capturedJson.equals(editor.result().interfaceSpec), "editor lost capture on snapshot restore");
        phase = Phase.EDITOR;
    }

    // ---- Cases --------------------------------------------------------------------------

    private static void runCases(Minecraft mc) {
        LocalPlayer real = mc.player;
        AbstractContainerMenu realMenu = real.containerMenu;
        int realMenuState = realMenu.getStateId();
        int realInventory = real.getInventory().getTimesChanged();
        int realXp = real.experienceLevel;

        check(SandboxNetworkFirewall.isLive(), "network firewall injection not live");
        check(SandboxScreenGate.isLive(), "screen gate injection not live");
        check(SandboxNetworkFirewall.isTaintedContainerPacket(new ServerboundContainerClosePacket(105)), "window 105 not tainted");
        check(!SandboxNetworkFirewall.isTaintedContainerPacket(new ServerboundContainerClosePacket(5)), "window 5 tainted");

        expect("chest", chest(), Fidelity.INTERACTIVE, "provider", session -> {
            AbstractContainerMenu menu = session.menu();
            check(menu != null && menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == 5,
                "chest slot 0 does not hold 5 diamonds from NBT");
            check(mc.level.getBlockEntity(session.targetPos()) == null, "real level has a block entity at the sandbox origin");
            check(!SandboxCoordinates.isLoaded(mc.level, session.targetPos().getX(), session.targetPos().getZ()),
                "sandbox origin chunk is loaded in the real level");
            long before = SandboxNetworkFirewall.interceptedCount();
            clickSlot(session, 0);
            ItemStack carried = menu == null ? ItemStack.EMPTY : menu.getCarried();
            check(carried.is(Items.DIAMOND) && carried.getCount() == 5, "click did not pick up the diamonds in the sandbox");
            check(SandboxNetworkFirewall.interceptedCount() > before, "click packet was not intercepted");
            // Runtime class names differ per loader (intermediary on Fabric), so key by the class itself.
            check(session.outboundCounts().containsKey(ServerboundContainerClickPacket.class.getSimpleName()),
                "click packet not recorded: " + session.outboundCounts());
            check(session.tripwire().violations() == 0, "tripwire fired");
        });
        expect("furnace", block("minecraft:furnace", "{Items:[{Slot:0b,id:\"minecraft:raw_iron\",count:3}],BurnTime:100s,CookTime:50s,CookTimeTotal:200s}"),
            Fidelity.INTERACTIVE, "provider", null);
        expect("crafting_table", block("minecraft:crafting_table", null), Fidelity.INTERACTIVE, "provider", null);
        expect("anvil", block("minecraft:anvil", null), Fidelity.INTERACTIVE, "provider", null);
        expect("hopper", block("minecraft:hopper", null), Fidelity.INTERACTIVE, "provider", null);
        expect("ui_id crafting", menu("minecraft:crafting"), Fidelity.INTERACTIVE, "bare_type", null);
        expect("held writable_book", item("minecraft:writable_book"), Fidelity.INTERACTIVE, "client_screen", null);
        expect("unknown block", block("ponderer:not_a_block", null), Fidelity.SCHEMATIC, null, null);
        runActionCases();
        runWorldBridgeCases(mc);

        check(real == mc.player, "Minecraft.player not restored");
        check(real.containerMenu == realMenu && realMenu.getStateId() == realMenuState, "real container menu changed");
        check(real.getInventory().getTimesChanged() == realInventory, "real inventory changed");
        check(real.experienceLevel == realXp, "real experience changed");
        check(mc.screen == null, "host screen changed to " + mc.screen);
    }

    private static void runWorldBridgeCases(Minecraft mc) {
        BlockPos source = mc.player.blockPosition();
        var original = mc.level.getBlockState(source);
        var originalEntity = mc.level.getBlockEntity(source);
        InterfaceDescriptor alias = chest();
        alias.lookupOrigin = new int[]{source.getX(), source.getY(), source.getZ()};
        expect("world bridge", alias, Fidelity.INTERACTIVE, "provider", session -> {
            session.dispatch("bridge assertions", () -> {
                check(mc.level.getBlockEntity(source) == session.level().getBlockEntity(session.targetPos()), "source alias missed sandbox BE");
                check(mc.level.getBlockState(source).is(Blocks.CHEST), "source alias missed sandbox state");
                var chunk = mc.level.getChunk(source.getX() >> 4, source.getZ() >> 4, ChunkStatus.FULL, false);
                check(chunk != null && chunk.getBlockState(source).is(Blocks.CHEST), "chunk lookup missed sandbox state");
                check(session.player().level().getChunk(source.getX() >> 4, source.getZ() >> 4, ChunkStatus.FULL, false) == chunk,
                    "sandbox player chunk lookup diverged from world bridge");
                check(mc.level.getChunkSource().getChunk(source.getX() >> 4, source.getZ() >> 4, ChunkStatus.FULL, false) == chunk,
                    "direct chunk cache did not use sandbox facade");
                check(mc.level.getBlockState(source.offset(512, 0, 0)).isAir(), "uncaptured world lookup escaped sandbox");
                check(mc.level.getEntity(mc.player.getId()) == null, "real entity lookup escaped sandbox");
                check(java.util.concurrent.CompletableFuture.supplyAsync(() -> SandboxWorldBridge.level(mc.level) == null).join(),
                    "world bridge active on another thread");
                mc.level.setBlock(source, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                check(session.level().getBlockState(session.targetPos()).is(Blocks.GOLD_BLOCK), "write not redirected");
                return null;
            }, null);
            check(mc.level.getBlockState(source) == original && mc.level.getBlockEntity(source) == originalEntity,
                "world bridge modified real world");
            try (SandboxScope ignored = SandboxScope.enter(session)) {
                throw new IllegalArgumentException("smoke scope cleanup");
            } catch (IllegalArgumentException expected) {
                check(SandboxWorldBridge.level(mc.level) == null, "world bridge leaked after exception");
            }
        });
        // Optional dependency fixture, never used by production opening logic.
        for (String name : new String[]{"mekanism:metallurgic_infuser", "mekanism:energized_smelter"}) {
            ResourceLocation id = ResourceLocation.parse(name);
            if (!BuiltInRegistries.BLOCK.containsKey(id) || !BuiltInRegistries.MENU.containsKey(id)) continue;
            InterfaceDescriptor d = block(name, null);
            d.menuType = name;
            d.lookupOrigin = new int[]{source.getX(), source.getY(), source.getZ()};
            d.extraData = Base64.getEncoder().encodeToString(ByteBuffer.allocate(8).putLong(source.asLong()).array());
            if (modPreview == null) modPreview = d;
            expect(name + " client factory", d, Fidelity.INTERACTIVE, "client_factory", session -> {
                check(session.currentScreen() != null && session.menu() != null, name + " did not construct GUI");
                check(mc.level.getBlockState(source) == original, name + " changed real source block");
            });
        }
    }

    /** Stage 2: scripted slot writes, synthesized actions, the crafting adapter and context patches. */
    private static void runActionCases() {
        expect("actions chest", chest(), Fidelity.INTERACTIVE, "provider", session -> {
            AbstractContainerMenu menu = session.menu();
            SessionOps.perform(session, action(InterfaceStepSpecs.ActionKind.QUICK_MOVE, 0, null, 0));
            check(menu.getSlot(0).getItem().isEmpty(), "quick_move left diamonds in chest slot 0");
            check(session.player().getInventory().countItem(Items.DIAMOND) == 5, "quick_move did not move diamonds to the sandbox inventory");

            InterfaceStepSpecs.Action drag = action(InterfaceStepSpecs.ActionKind.DRAG, 13, 1, 0);
            SessionOps.beginDrag(session, drag);
            SessionOps.perform(session, drag);
            check(menu.getSlot(1).getItem().is(Items.OAK_LOG) && menu.getSlot(1).getItem().getCount() == 32
                && menu.getSlot(13).getItem().isEmpty() && menu.getCarried().isEmpty(), "drag 13 -> 1 did not move the logs");

            SessionOps.perform(session, action(InterfaceStepSpecs.ActionKind.SWAP_HOTBAR, 1, null, 0));
            check(menu.getSlot(1).getItem().isEmpty() && session.player().getInventory().getItem(0).is(Items.OAK_LOG),
                "swap_hotbar did not swap slot 1 with hotbar 1");

            SessionOps.writeSlot(session, new InterfaceStepSpecs.SlotWrite(2, new InterfaceDescriptor.ItemSpec("minecraft:iron_ingot", 3, null, null), null, null));
            check(menu.getSlot(2).getItem().is(Items.IRON_INGOT) && menu.getSlot(2).getItem().getCount() == 3, "set_interface_slots did not write iron");
            check(SessionOps.goalMet(session, new InterfaceStepSpecs.Goal(2, "minecraft:iron_ingot", 3)), "goal not met for 3 iron");
            SessionOps.writeSlot(session, new InterfaceStepSpecs.SlotWrite(2, InterfaceDescriptor.ItemSpec.of("minecraft:stone"), 99, 99));
            check(menu.getSlot(2).getItem().is(Items.IRON_INGOT), "expect mismatch did not skip the write");

            int stock = session.player().getInventory().countItem(Items.IRON_INGOT);
            SessionOps.perform(session, action(InterfaceStepSpecs.ActionKind.THROW, 2, null, 0));
            check(menu.getSlot(2).getItem().getCount() == 2, "throw did not drop one iron");
            check(session.player().getInventory().countItem(Items.IRON_INGOT) == stock + 1, "thrown iron did not return to the sandbox inventory");
            check(session.tripwire().violations() == 0, "tripwire fired during actions");
        });
        expect("actions crafting", block("minecraft:crafting_table", null), Fidelity.INTERACTIVE, "provider", session -> {
            AbstractContainerMenu menu = session.menu();
            for (int slot : new int[]{1, 2, 4, 5}) {
                SessionOps.writeSlot(session, new InterfaceStepSpecs.SlotWrite(slot, InterfaceDescriptor.ItemSpec.of("minecraft:oak_planks"), null, null));
            }
            check(menu.getSlot(0).getItem().is(Items.CRAFTING_TABLE), "crafting adapter did not produce a crafting table: " + menu.getSlot(0).getItem());
            SessionOps.perform(session, action(InterfaceStepSpecs.ActionKind.CLICK, 0, null, 0));
            check(menu.getCarried().is(Items.CRAFTING_TABLE), "taking the result did not carry a crafting table");
            check(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(0).getItem().isEmpty(), "taking the result did not consume the grid");
        });
        expect("context xp", block("minecraft:enchanting_table", null), Fidelity.INTERACTIVE, "provider", session -> {
            SessionOps.patchContext(session, new InterfaceStepSpecs.ContextPatch("creative", 30, null, 2, null));
            check(session.player().experienceLevel == 30 && session.player().getInventory().selected == 2
                && session.player().getAbilities().instabuild, "set_interface_context did not apply");
        });
    }

    private static InterfaceStepSpecs.Action action(InterfaceStepSpecs.ActionKind kind, int slot, @Nullable Integer to, int button) {
        return new InterfaceStepSpecs.Action(kind, new InterfaceStepSpecs.Target(slot, null, null),
            to == null ? null : new InterfaceStepSpecs.Target(to, null, null), button, 0, false, null, null, 0, 0);
    }

    private static void expect(String name, InterfaceDescriptor descriptor, Fidelity fidelity, @Nullable String strategy,
                               @Nullable Consumer<SandboxSession> extra) {
        SandboxSession session = SandboxSession.open(descriptor, null);
        try {
            String got = session.fidelity().tier() + "/" + session.strategyId()
                + (session.fidelityReason() == null ? "" : " (" + session.fidelityReason() + ")")
                + (session.currentScreen() == null ? "" : " screen=" + session.currentScreen().getClass().getSimpleName());
            LOGGER.info("[PondererSmoke] case {}: {}", name, got);
            check(session.fidelity() == fidelity, name + ": expected " + fidelity.tier() + " got " + got);
            if (strategy != null) {
                check(strategy.equals(session.strategyId()), name + ": expected strategy " + strategy + " got " + got);
            }
            if (extra != null && session.fidelity() == fidelity) {
                extra.accept(session);
            }
        } finally {
            session.destroy("smoke");
        }
    }

    private static void clickSlot(SandboxSession session, int slotIndex) {
        int[] rect = session.guiRect();
        AbstractContainerMenu menu = session.menu();
        if (rect == null || menu == null) {
            check(false, "no rect/menu to click");
            return;
        }
        Slot slot = menu.getSlot(slotIndex);
        double x = rect[0] + slot.x + 8;
        double y = rect[1] + slot.y + 8;
        session.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        session.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            failures.add(failure);
            LOGGER.warn("[PondererSmoke] check failed: {}", failure);
        }
    }

    private static void screenshot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
            message -> LOGGER.info("[PondererSmoke] screenshot: {}", message.getString()));
    }

    // ---- Descriptors --------------------------------------------------------------------

    private static InterfaceDescriptor chest() {
        return block("minecraft:chest", "{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5},{Slot:13b,id:\"minecraft:oak_log\",count:32}]}");
    }

    private static InterfaceDescriptor block(String id, @Nullable String nbt) {
        InterfaceDescriptor d = new InterfaceDescriptor();
        d.origin = "smoke";
        d.targetKind = InterfaceDescriptor.TargetKind.BLOCK;
        d.block = id;
        d.nbt = nbt;
        return d;
    }

    private static InterfaceDescriptor menu(String menuType) {
        InterfaceDescriptor d = new InterfaceDescriptor();
        d.origin = "smoke";
        d.targetKind = InterfaceDescriptor.TargetKind.MENU;
        d.menuType = menuType;
        return d;
    }

    private static InterfaceDescriptor item(String id) {
        InterfaceDescriptor d = new InterfaceDescriptor();
        d.origin = "smoke";
        d.targetKind = InterfaceDescriptor.TargetKind.ITEM;
        d.item = InterfaceDescriptor.ItemSpec.of(id);
        return d;
    }

    /** Minimal host that draws one sandbox session full screen, as the Ponder overlay would. */
    private static final class HostScreen extends Screen {
        private final SandboxSession session;

        HostScreen(SandboxSession session) {
            super(Component.literal("Ponderer interface smoke"));
            this.session = session;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF2B3A4A);
            graphics.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            int[] rect = session.guiRect();
            // Hover the first slot so the screenshot shows highlight and tooltip rendering.
            int hoverX = rect == null ? mouseX : rect[0] + 16;
            int hoverY = rect == null ? mouseY : rect[1] + 26;
            session.render(graphics, hoverX, hoverY, partialTick);
        }

        @Override
        public void tick() {
            session.tick();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
