package com.nododiiiii.ponderer.interfaces;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.interfaces.model.InterfaceDescriptor;
import com.nododiiiii.ponderer.interfaces.model.InterfaceStepSpecs;
import com.nododiiiii.ponderer.interfaces.sandbox.Fidelity;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxCoordinates;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxNetworkFirewall;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxScreenGate;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
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

    private enum Phase { WAIT_WORLD, CASES, SANDBOX_SCREEN, PONDER, DONE }

    private static Phase phase = Phase.WAIT_WORLD;
    private static int ticks;
    private static final List<String> failures = new ArrayList<>();
    @Nullable
    private static SandboxSession shown;

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
                        phase = Phase.CASES;
                        ticks = 0;
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
                        if (PONDER_ITEM.isBlank()) {
                            finish();
                        } else {
                            mc.setScreen(PonderUI.of(ResourceLocation.parse(PONDER_ITEM)));
                            phase = Phase.PONDER;
                        }
                        ticks = 0;
                    }
                }
                case PONDER -> {
                    if (ticks == 80) {
                        Screen embedded = InterfaceHost.activeScreen();
                        LOGGER.info("[PondererSmoke] PonderUI embedded interface: {}",
                            embedded == null ? "none" : embedded.getClass().getName());
                        if (embedded == null) {
                            failures.add("PonderUI scene showed no embedded interface");
                        }
                        screenshot(mc, "ponderer-smoke-ponder.png");
                        InterfaceHost.enterTry();
                        check(InterfaceHost.mode() == InterfaceHost.Mode.TRY, "could not enter try mode");
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

        check(real == mc.player, "Minecraft.player not restored");
        check(real.containerMenu == realMenu && realMenu.getStateId() == realMenuState, "real container menu changed");
        check(real.getInventory().getTimesChanged() == realInventory, "real inventory changed");
        check(real.experienceLevel == realXp, "real experience changed");
        check(mc.screen == null, "host screen changed to " + mc.screen);
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
