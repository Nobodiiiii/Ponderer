package com.nododiiiii.ponderer.interfaces.sandbox;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.interfaces.model.InterfaceDescriptor;
import com.nododiiiii.ponderer.interfaces.platform.InterfacePlatform;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One interface shown from a scene: its sandbox level, sandbox player, menu and screen stack.
 * Every call into menu or screen code goes through {@link #dispatch}, which enters the
 * {@link SandboxScope}, isolates exceptions and degrades the session instead of propagating.
 */
public final class SandboxSession {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NOTICE_MILLIS = 3000;

    private final InterfaceDescriptor descriptor;
    private final Minecraft mc;
    private final SandboxTripwire tripwire = new SandboxTripwire();
    private final List<String> reports = new ArrayList<>();
    private final Map<String, Integer> outbound = new LinkedHashMap<>();
    private final List<Screen> screens = new ArrayList<>();
    private final List<ItemStack> overflow = new ArrayList<>();

    @Nullable
    private SandboxLevel level;
    @Nullable
    private SandboxPlayer player;
    @Nullable
    private MultiPlayerGameMode gameMode;
    @Nullable
    private RegistryAccess registries;
    private BlockPos targetPos = BlockPos.ZERO;
    private GameType gameType = GameType.SURVIVAL;
    @Nullable
    private AbstractContainerMenu menu;
    @Nullable
    private OpenPlanner.Strategy strategy;

    private Fidelity fidelity = Fidelity.SCHEMATIC;
    @Nullable
    private String fidelityReason;
    private int windowCounter;
    private boolean capturing;
    @Nullable
    private Screen captured;
    private boolean destroyed;
    private int closeRequests;
    @Nullable
    private Component notice;
    private long noticeUntil;

    private SandboxSession(InterfaceDescriptor descriptor) {
        this.descriptor = descriptor;
        this.mc = Minecraft.getInstance();
    }

    // ---- Construction -------------------------------------------------------------------

    /**
     * Builds a session for {@code descriptor}. Never throws: whatever cannot be built ends up
     * as a schematic-tier session that explains why.
     *
     * @param sceneLevel the Ponder scene level, used only to copy {@code scene_block} targets
     */
    public static SandboxSession open(InterfaceDescriptor descriptor, @Nullable PonderLevel sceneLevel) {
        SandboxSession session = new SandboxSession(descriptor);
        long start = Util.getNanos();
        try {
            session.build(sceneLevel);
        } catch (Throwable t) {
            LOGGER.warn("[Ponderer] Interface sandbox failed to build {}", descriptor, t);
            session.fail("build failed: " + t);
        }
        long micros = (Util.getNanos() - start) / 1000L;
        LOGGER.info("[Ponderer] Interface sandbox {} -> {} via {} in {} us{}", descriptor,
            session.fidelity.tier(), session.strategy == null ? "none" : session.strategy.id, micros,
            session.fidelityReason == null ? "" : " (" + session.fidelityReason + ")");
        return session;
    }

    private void build(@Nullable PonderLevel sceneLevel) {
        ClientLevel realLevel = mc.level;
        ClientPacketListener listener = mc.getConnection();
        if (realLevel == null || listener == null || mc.player == null) {
            fail("no client world");
            return;
        }
        BlockPos origin = SandboxCoordinates.pickOrigin(realLevel);
        if (origin == null) {
            fail("no unloaded far chunk available for the sandbox origin");
            return;
        }
        targetPos = origin;
        registries = realLevel.registryAccess();
        gameType = ContextApplier.gameType(descriptor);
        level = new SandboxLevel(realLevel);
        level.setLookupOrigin(descriptor.lookupOrigin, origin);
        player = new SandboxPlayer(mc, realLevel, listener, this, level);
        gameMode = new MultiPlayerGameMode(mc, listener);

        OpenPlanner.Opened opened = dispatch("open", () -> {
            ContextApplier.applyPlayer(this, descriptor, origin);
            if (!placeTarget(sceneLevel, origin)) {
                return null;
            }
            ContextApplier.placeContextBlocks(this, descriptor, origin);
            return OpenPlanner.open(this, descriptor);
        }, null);

        if (destroyed) {
            return;
        }
        if (opened == null) {
            fail(reports.isEmpty() ? "no strategy could open this interface" : reports.get(reports.size() - 1));
            return;
        }
        strategy = opened.strategy();
        menu = opened.menu();
        if (menu != null) {
            player.containerMenu = menu;
            dispatch("state", () -> {
                StateApplier.apply(this, descriptor, menu);
                return null;
            }, null);
        }
        if (fidelity == Fidelity.SCHEMATIC) {
            fidelity = Fidelity.INTERACTIVE;
        }
        if (!SandboxNetworkFirewall.isLive() || !SandboxScreenGate.isLive()) {
            degradeToReadOnly("sandbox guards not active (firewall=" + SandboxNetworkFirewall.isLive()
                + ", screen gate=" + SandboxScreenGate.isLive() + ")");
        }
    }

    private boolean placeTarget(@Nullable PonderLevel sceneLevel, BlockPos origin) {
        switch (descriptor.targetKind) {
            case BLOCK -> {
                BlockState state = ContextApplier.resolveState(descriptor.block, descriptor.blockProperties);
                if (state == null) {
                    report("unknown block " + descriptor.block);
                    return false;
                }
                CompoundTag tag = ContextApplier.blockEntityTag(this, descriptor.nbt, descriptor);
                ContextApplier.placeBlock(this, origin, state, tag);
                applyUpdateTag(origin);
                return true;
            }
            case SCENE_BLOCK -> {
                if (sceneLevel == null || descriptor.scenePos == null) {
                    report("scene_block target without a scene position");
                    return false;
                }
                BlockPos scenePos = new BlockPos(descriptor.scenePos[0], descriptor.scenePos[1], descriptor.scenePos[2]);
                BlockState state = sceneLevel.getBlockState(scenePos);
                BlockEntity sceneBlockEntity = sceneLevel.getBlockEntity(scenePos);
                CompoundTag tag = sceneBlockEntity == null ? null : sceneBlockEntity.saveWithFullMetadata();
                ContextApplier.placeBlock(this, origin, state, tag);
                applyUpdateTag(origin);
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private void applyUpdateTag(BlockPos pos) {
        if (descriptor.updateTag == null) {
            return;
        }
        BlockEntity blockEntity = level().getBlockEntity(pos);
        CompoundTag tag = ContextApplier.blockEntityTag(this, descriptor.updateTag, descriptor);
        if (blockEntity != null && tag != null) {
            blockEntity.load(tag);
        }
    }

    /** Called by {@link OpenPlanner} inside the scope. Returns a rejection reason or null. */
    @Nullable
    String initScreen(Screen screen) {
        try {
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();
            screen.added();
            screen.init(mc, width, height);
            screens.clear();
            screens.add(screen);
            return null;
        } catch (Throwable t) {
            return "screen init threw " + t;
        }
    }

    // ---- Dispatch -----------------------------------------------------------------------

    /** Runs {@code call} inside the sandbox scope; exceptions degrade the session and yield {@code fallback}. */
    public <T> T dispatch(String phase, Supplier<T> call, T fallback) {
        if (destroyed || player == null) {
            return fallback;
        }
        try (SandboxScope ignored = SandboxScope.enter(this)) {
            return call.get();
        } catch (Throwable t) {
            LOGGER.warn("[Ponderer] Interface sandbox '{}' threw during {}", describe(), phase, t);
            if ("render".equals(phase) || "open".equals(phase)) {
                fail(phase + " threw " + t);
            } else {
                degradeToReadOnly(phase + " threw " + t);
            }
            return fallback;
        }
    }

    @Nullable
    Screen captureScreen(Runnable action) {
        capturing = true;
        captured = null;
        try {
            action.run();
        } finally {
            capturing = false;
        }
        Screen result = captured;
        captured = null;
        return result;
    }

    // ---- Host-facing API ----------------------------------------------------------------

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Screen screen = currentScreen();
        if (screen == null || fidelity == Fidelity.SCHEMATIC) {
            renderPlaceholder(graphics);
            return;
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        dispatch("render", () -> {
            if (screen.width != width || screen.height != height) {
                screen.resize(mc, width, height);
            }
            screen.render(graphics, mouseX, mouseY, partialTicks);
            return null;
        }, null);
        if (fidelity == Fidelity.SCHEMATIC) {
            return;
        }
        renderNotice(graphics);
    }

    public void tick() {
        Screen screen = currentScreen();
        if (screen != null && fidelity != Fidelity.SCHEMATIC) {
            dispatch("tick", () -> {
                screen.tick();
                return null;
            }, null);
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        Screen screen = currentScreen();
        return screen != null && canInteract() && afterInput(dispatch("mouseClicked", () -> screen.mouseClicked(x, y, button), false));
    }

    public boolean mouseReleased(double x, double y, int button) {
        Screen screen = currentScreen();
        return screen != null && canInteract() && afterInput(dispatch("mouseReleased", () -> screen.mouseReleased(x, y, button), false));
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        Screen screen = currentScreen();
        return screen != null && canInteract() && afterInput(dispatch("mouseDragged", () -> screen.mouseDragged(x, y, button, dx, dy), false));
    }

    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        Screen screen = currentScreen();
        return screen != null && canInteract() && dispatch("mouseScrolled", () -> screen.mouseScrolled(x, y, scrollY), false);
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        Screen screen = currentScreen();
        return screen != null && canInteract() && afterInput(dispatch("keyPressed", () -> screen.keyPressed(key, scanCode, modifiers), false));
    }

    public boolean charTyped(char c, int modifiers) {
        Screen screen = currentScreen();
        return screen != null && canInteract() && dispatch("charTyped", () -> screen.charTyped(c, modifiers), false);
    }

    private boolean afterInput(boolean handled) {
        SessionOps.updateCrafting(this);
        return handled;
    }

    /** Pops a nested sandbox screen (e.g. a mod's client-side sub page). Returns false at the root. */
    public boolean popScreen() {
        if (screens.size() <= 1) {
            return false;
        }
        Screen top = screens.remove(screens.size() - 1);
        dispatch("removed", () -> {
            top.removed();
            return null;
        }, null);
        return true;
    }

    public void destroy(String reason) {
        if (destroyed) {
            return;
        }
        List<Screen> toRemove = new ArrayList<>(screens);
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            Screen screen = toRemove.get(i);
            dispatch("removed", () -> {
                screen.removed();
                return null;
            }, null);
        }
        destroyed = true;
        screens.clear();
        menu = null;
        level = null;
        player = null;
        gameMode = null;
        LOGGER.debug("[Ponderer] Interface sandbox {} destroyed: {}", descriptor, reason);
    }

    // ---- Callbacks from sandbox objects and guards ---------------------------------------

    void requestClose(String source) {
        closeRequests++;
        if (!capturing) {
            // Nested client screens close back to their parent; the root stays until the scene hides it.
            popScreen();
        }
    }

    void offerScreen(Screen screen) {
        if (capturing) {
            captured = screen;
            return;
        }
        String rejection = initNested(screen);
        if (rejection != null) {
            report("nested screen rejected: " + rejection);
        }
    }

    @Nullable
    private String initNested(Screen screen) {
        try {
            screen.added();
            screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            screens.add(screen);
            if (screen instanceof MenuAccess<?> access && player != null) {
                player.containerMenu = access.getMenu();
            }
            return null;
        } catch (Throwable t) {
            return t.toString();
        }
    }

    void returnToInventory(ItemStack stack) {
        if (stack.isEmpty() || player == null) {
            return;
        }
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            overflow.add(stack.copy());
        }
    }

    void notice(Component message) {
        notice = message;
        noticeUntil = Util.getMillis() + NOTICE_MILLIS;
    }

    void onOutbound(Packet<?> packet) {
        outbound.merge(packet.getClass().getSimpleName(), 1, Integer::sum);
    }

    public void degradeToReadOnly(String reason) {
        if (fidelity == Fidelity.INTERACTIVE) {
            fidelity = Fidelity.READ_ONLY;
            fidelityReason = reason;
            LOGGER.warn("[Ponderer] Interface sandbox {} is now read-only: {}", descriptor, reason);
        }
    }

    private void fail(String reason) {
        fidelity = Fidelity.SCHEMATIC;
        fidelityReason = reason;
    }

    void report(String message) {
        reports.add(message);
        LOGGER.debug("[Ponderer] Interface sandbox {}: {}", descriptor, message);
    }

    int nextWindowId() {
        int id = SandboxNetworkFirewall.WINDOW_ID_MIN + windowCounter;
        windowCounter = (windowCounter + 1) % (SandboxNetworkFirewall.WINDOW_ID_MAX - SandboxNetworkFirewall.WINDOW_ID_MIN + 1);
        return id;
    }

    // ---- Accessors ----------------------------------------------------------------------

    public boolean canInteract() {
        return !destroyed
            && fidelity == Fidelity.INTERACTIVE
            && descriptor.interaction == InterfaceDescriptor.Interaction.TRY
            && SandboxNetworkFirewall.isLive()
            && SandboxScreenGate.isLive();
    }

    @Nullable
    public Screen currentScreen() {
        return destroyed || screens.isEmpty() ? null : screens.get(screens.size() - 1);
    }

    /** GUI rectangle {left, top, width, height} of the current screen; whole screen for non-container screens. */
    @Nullable
    public int[] guiRect() {
        Screen screen = currentScreen();
        if (screen == null || fidelity == Fidelity.SCHEMATIC) {
            return placeholderRect();
        }
        int[] rect = InterfacePlatform.get().containerRect(screen);
        return rect != null ? rect : new int[]{0, 0, screen.width, screen.height};
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public Fidelity fidelity() {
        return fidelity;
    }

    @Nullable
    public String fidelityReason() {
        return fidelityReason;
    }

    @Nullable
    public String strategyId() {
        return strategy == null ? null : strategy.id;
    }

    @Nullable
    public AbstractContainerMenu menu() {
        return menu;
    }

    public InterfaceDescriptor descriptor() {
        return descriptor;
    }

    public List<String> reports() {
        return List.copyOf(reports);
    }

    public Map<String, Integer> outboundCounts() {
        return Map.copyOf(outbound);
    }

    public int closeRequests() {
        return closeRequests;
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    public GameType gameType() {
        return gameType;
    }

    public SandboxTripwire tripwire() {
        return tripwire;
    }

    public SandboxLevel level() {
        if (level == null) {
            throw new IllegalStateException("sandbox session has no level");
        }
        return level;
    }

    public SandboxPlayer player() {
        if (player == null) {
            throw new IllegalStateException("sandbox session has no player");
        }
        return player;
    }

    MultiPlayerGameMode gameMode() {
        if (gameMode == null) {
            throw new IllegalStateException("sandbox session has no game mode");
        }
        return gameMode;
    }

    public RegistryAccess registries() {
        if (registries == null) {
            throw new IllegalStateException("sandbox session has no registries");
        }
        return registries;
    }

    public String describe() {
        return descriptor + " " + fidelity.tier();
    }

    // ---- Drawing helpers ----------------------------------------------------------------

    private int[] placeholderRect() {
        int width = 200;
        int height = 64;
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        return new int[]{(guiW - width) / 2, (guiH - height) / 2, width, height};
    }

    private void renderPlaceholder(GuiGraphics graphics) {
        int[] rect = placeholderRect();
        Font font = mc.font;
        graphics.fill(rect[0] - 1, rect[1] - 1, rect[0] + rect[2] + 1, rect[1] + rect[3] + 1, 0xFF5A5A5A);
        graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], 0xF0101018);
        graphics.drawString(font, Component.translatable("ponderer.ui.show_interface.unavailable")
            .withStyle(ChatFormatting.GOLD), rect[0] + 6, rect[1] + 6, 0xFFFFFF, false);
        String reason = fidelityReason == null ? descriptor.toString() : fidelityReason;
        List<FormattedCharSequence> lines = font.split(Component.literal(reason), rect[2] - 12);
        int y = rect[1] + 20;
        for (int i = 0; i < lines.size() && y + font.lineHeight <= rect[1] + rect[3] - 2; i++) {
            graphics.drawString(font, lines.get(i), rect[0] + 6, y, 0xB0B0B0, false);
            y += font.lineHeight + 1;
        }
    }

    private void renderNotice(GuiGraphics graphics) {
        if (notice == null || Util.getMillis() > noticeUntil) {
            return;
        }
        int[] rect = guiRect();
        if (rect == null) {
            return;
        }
        Font font = mc.font;
        int width = font.width(notice) + 8;
        int x = rect[0] + (rect[2] - width) / 2;
        int y = rect[1] + rect[3] + 4;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(x, y, x + width, y + font.lineHeight + 4, 0xE0101018);
        graphics.drawString(font, notice, x + 4, y + 2, 0xFFFFFF, false);
        graphics.pose().popPose();
    }
}
