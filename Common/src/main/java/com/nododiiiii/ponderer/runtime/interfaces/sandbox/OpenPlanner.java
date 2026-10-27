package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import com.nododiiiii.ponderer.runtime.interfaces.platform.InterfacePlatform;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Open pipeline (design doc 7): tries the strategies of the target's chain in order, validates
 * each product and releases it before falling through. Runs inside the sandbox scope.
 *
 * <ul>
 *   <li>P – provider: the sandbox block entity's own {@link MenuProvider} (server-style construction,
 *   all sandbox objects), else {@code BlockState#getMenuProvider}.</li>
 *   <li>A – AE2's registered host-aware menu factories, without a server-side open request.</li>
 *   <li>F – loader client factory with captured extra data.</li>
 *   <li>B – bare {@code MenuType#create(id, inventory)}.</li>
 *   <li>S – client screen from {@code ItemStack#use}, captured by the screen gate.</li>
 * </ul>
 * Anything left over becomes the schematic tier (T0).
 */
final class OpenPlanner {

    enum Strategy {
        PROVIDER("provider"), AE2("ae2"), CLIENT_FACTORY("client_factory"), BARE_TYPE("bare_type"), CLIENT_SCREEN("client_screen");

        final String id;

        Strategy(String id) {
            this.id = id;
        }
    }

    record Opened(Strategy strategy, @Nullable AbstractContainerMenu menu, Screen screen) {
    }

    private OpenPlanner() {
    }

    static List<Strategy> chainFor(InterfaceDescriptor d) {
        List<Strategy> chain = new ArrayList<>();
        switch (d.targetKind) {
            case BLOCK, SCENE_BLOCK -> {
                if (d.extraData != null) {
                    chain.add(Strategy.CLIENT_FACTORY);
                }
                chain.add(Strategy.PROVIDER);
                chain.add(Strategy.AE2);
                if (d.extraData == null && d.menuType != null) {
                    chain.add(Strategy.BARE_TYPE);
                }
            }
            case MENU -> {
                chain.add(Strategy.BARE_TYPE);
                chain.add(Strategy.CLIENT_FACTORY);
            }
            case ITEM -> {
                if (d.itemMode != InterfaceDescriptor.ItemMode.MENU) {
                    chain.add(Strategy.CLIENT_SCREEN);
                }
                if (d.itemMode != InterfaceDescriptor.ItemMode.SCREEN) {
                    chain.add(Strategy.CLIENT_FACTORY);
                }
            }
            case ENTITY -> {
                // Entity targets need adapters (phase 4).
            }
        }
        return chain;
    }

    @Nullable
    static Opened open(SandboxSession session, InterfaceDescriptor d) {
        for (Strategy strategy : chainFor(d)) {
            Opened opened;
            try {
                opened = attempt(session, d, strategy);
            } catch (Throwable t) {
                session.report(strategy.id + " failed: " + t);
                continue;
            }
            if (opened == null) {
                continue;
            }
            String rejection = validate(d, opened);
            if (rejection == null) {
                rejection = session.initScreen(opened.screen());
            }
            if (rejection == null) {
                return opened;
            }
            session.report(strategy.id + " rejected: " + rejection);
            release(session, opened);
        }
        return null;
    }

    @Nullable
    private static Opened attempt(SandboxSession session, InterfaceDescriptor d, Strategy strategy) throws ReflectiveOperationException {
        return switch (strategy) {
            case PROVIDER -> provider(session);
            case AE2 -> ae2(session, d);
            case CLIENT_FACTORY -> clientFactory(session, d);
            case BARE_TYPE -> bareType(session, d);
            case CLIENT_SCREEN -> clientScreen(session, d);
        };
    }

    @Nullable
    private static Opened provider(SandboxSession session) {
        BlockPos pos = session.targetPos();
        BlockState state = session.level().getBlockState(pos);
        BlockEntity blockEntity = session.level().getBlockEntity(pos);
        MenuProvider provider = blockEntity instanceof MenuProvider menuProvider
            ? menuProvider
            : state.getMenuProvider(session.level(), pos);
        if (provider == null) {
            session.report("provider: " + state.getBlock() + " has no menu provider");
            return null;
        }
        int windowId = session.nextWindowId();
        AbstractContainerMenu menu = provider.createMenu(windowId, session.player().getInventory(), session.player());
        if (menu == null) {
            session.report("provider: createMenu returned null");
            return null;
        }
        return withScreen(session, Strategy.PROVIDER, menu, provider.getDisplayName());
    }

    @Nullable
    private static Opened ae2(SandboxSession session, InterfaceDescriptor descriptor) throws ReflectiveOperationException {
        var opened = InterfaceExtensions.menus().open(session, descriptor, session::nextWindowId, session::report);
        return opened == null ? null : withScreen(session, Strategy.AE2, opened.menu(), opened.title());
    }

    @Nullable
    private static Opened clientFactory(SandboxSession session, InterfaceDescriptor d) {
        MenuType<?> type = menuType(d.menuType);
        if (type == null || d.extraData == null) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(d.extraData);
        } catch (IllegalArgumentException ex) {
            session.report("client_factory: extraData is not base64");
            return null;
        }
        if (d.isItemTarget()) {
            ContextApplier.equipTargetItem(session, d);
        }
        int windowId = session.nextWindowId();
        AbstractContainerMenu menu = InterfacePlatform.get().createMenuWithExtraData(type, windowId,
            session.player().getInventory(), bytes, session.registries());
        if (menu == null) {
            return null;
        }
        return withScreen(session, Strategy.CLIENT_FACTORY, menu, fallbackTitle(session, d));
    }

    @Nullable
    private static Opened bareType(SandboxSession session, InterfaceDescriptor d) {
        MenuType<?> type = menuType(d.menuType);
        if (type == null) {
            session.report("bare_type: unknown menu type " + d.menuType);
            return null;
        }
        AbstractContainerMenu menu = type.create(session.nextWindowId(), session.player().getInventory());
        return withScreen(session, Strategy.BARE_TYPE, menu, fallbackTitle(session, d));
    }

    @Nullable
    private static Opened clientScreen(SandboxSession session, InterfaceDescriptor d) {
        ItemStack stack = ContextApplier.equipTargetItem(session, d);
        if (stack.isEmpty()) {
            return null;
        }
        InteractionHand hand = "off".equals(d.hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Screen captured = session.captureScreen(() -> stack.use(session.level(), session.player(), hand));
        if (captured == null) {
            session.report("client_screen: " + stack.getItem() + " opened no client screen");
            return null;
        }
        AbstractContainerMenu menu = captured instanceof MenuAccess<?> access ? access.getMenu() : null;
        return new Opened(Strategy.CLIENT_SCREEN, menu, captured);
    }

    @Nullable
    private static Opened withScreen(SandboxSession session, Strategy strategy, AbstractContainerMenu menu, Component title) {
        session.player().containerMenu = menu;
        Screen screen = InterfacePlatform.get().createMenuScreen(menu, session.player().getInventory(), title);
        if (screen == null) {
            session.report(strategy.id + ": no screen registered for " + menuTypeName(menu));
            menu.removed(session.player());
            return null;
        }
        return new Opened(strategy, menu, screen);
    }

    /** Returns a rejection reason, or null when the product matches what the descriptor recorded. */
    @Nullable
    private static String validate(InterfaceDescriptor d, Opened opened) {
        if (d.menuType == null || opened.menu() == null || d.targetKind == InterfaceDescriptor.TargetKind.MENU) {
            return null;
        }
        String actual = menuTypeName(opened.menu());
        ResourceLocation expected = ResourceLocation.tryParse(d.menuType);
        if (expected != null && actual != null && !expected.toString().equals(actual)) {
            return "menu type " + actual + " != recorded " + expected;
        }
        return null;
    }

    private static void release(SandboxSession session, Opened opened) {
        try {
            opened.screen().removed();
        } catch (Throwable ignored) {
        }
        if (opened.menu() != null && !(opened.screen() instanceof MenuAccess<?>)) {
            try {
                opened.menu().removed(session.player());
            } catch (Throwable ignored) {
            }
        }
        session.player().containerMenu = session.player().inventoryMenu;
    }

    @Nullable
    static String menuTypeName(AbstractContainerMenu menu) {
        try {
            ResourceLocation key = BuiltInRegistries.MENU.getKey(menu.getType());
            return key == null ? null : key.toString();
        } catch (UnsupportedOperationException ex) {
            // Menus without a type (the player inventory) throw here.
            return null;
        }
    }

    @Nullable
    private static MenuType<?> menuType(@Nullable String raw) {
        ResourceLocation id = raw == null ? null : ResourceLocation.tryParse(raw.trim());
        if (id == null || !BuiltInRegistries.MENU.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.MENU.get(id);
    }

    private static Component fallbackTitle(SandboxSession session, InterfaceDescriptor d) {
        if (d.isItemTarget() && d.item != null) {
            ItemStack stack = ContextApplier.decodeItem(session, d.item);
            if (!stack.isEmpty()) {
                return stack.getHoverName();
            }
        }
        ResourceLocation id = d.menuType == null ? null : ResourceLocation.tryParse(d.menuType);
        if (id != null) {
            for (String key : new String[]{"container." + id.getPath(), "container." + id.getNamespace() + "." + id.getPath()}) {
                if (Language.getInstance().has(key)) {
                    return Component.translatable(key);
                }
            }
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                return BuiltInRegistries.BLOCK.get(id).getName();
            }
        }
        return Component.empty();
    }
}
