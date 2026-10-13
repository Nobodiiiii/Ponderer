package com.nododiiiii.ponderer.compat.ae2;

import com.nododiiiii.ponderer.interfaces.model.InterfaceDescriptor;
import com.nododiiiii.ponderer.interfaces.platform.InterfacePlatform;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.platform.PondererServices;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Optional AE2 bridge for block interfaces. AE2 creates its MenuProvider inside a server-only
 * opener, rather than exposing it from the block. Read the builders retained by that opener
 * and reuse their client factories with a locator for the sandbox host.
 *
 * <p>Only AE2-owned, unmapped API names are reflected; Minecraft types use class literals so
 * the bridge also works after Fabric remapping. No block/menu id table or AE2 dependency is
 * needed. See ref/1.21.1/AppliedEnergistics2's MenuOpener and MenuTypeBuilder.
 */
public final class Ae2InterfaceCompat {
    private static Bridge bridge;

    public record Opened(AbstractContainerMenu menu, Component title) {}

    private record Binding(MenuType<?> type, Class<?> hostType, Object builder) {}

    private Ae2InterfaceCompat() {}

    @Nullable
    public static Opened open(SandboxSession session, InterfaceDescriptor descriptor, IntSupplier nextWindowId,
                              Consumer<String> report)
        throws ReflectiveOperationException {
        if (!PondererServices.PLATFORM.isModLoaded("ae2")) return null;
        BlockEntity blockEntity = session.level().getBlockEntity(session.targetPos());
        if (blockEntity == null) return null;
        Bridge api = bridge;
        if (api == null) bridge = api = new Bridge();
        if (!api.blockEntityType.isInstance(blockEntity) && !api.partHostType.isInstance(blockEntity)) return null;

        Object host = api.host(blockEntity, descriptor);
        if (host == null) {
            report.accept("ae2: no part at the selected hit position");
            return null;
        }
        MenuType<?> preferred;
        if (descriptor.menuType == null) {
            preferred = api.preferredMenu(host, session.player());
        } else {
            ResourceLocation id = ResourceLocation.tryParse(descriptor.menuType);
            if (id == null || !BuiltInRegistries.MENU.containsKey(id)) {
                report.accept("ae2: unknown recorded menu type " + descriptor.menuType);
                return null;
            }
            preferred = BuiltInRegistries.MENU.get(id);
        }
        List<Binding> matching = api.bindings().stream()
            .filter(binding -> binding.hostType().isInstance(host))
            .filter(binding -> preferred == null || binding.type() == preferred)
            .toList();
        // A concrete host or a more specific interface wins over broad hosts for priority,
        // storage and crafting submenus. Never rely on HashMap iteration order.
        List<Binding> candidates = matching.stream()
            .filter(binding -> preferred != null || matching.stream().noneMatch(other ->
                binding.hostType() != other.hostType() && binding.hostType().isAssignableFrom(other.hostType())))
            .sorted(Comparator.comparing(binding -> BuiltInRegistries.MENU.getKey(binding.type()).toString()))
            .toList();
        List<Opened> opened = new ArrayList<>();
        try {
            for (Binding binding : candidates) {
                AbstractContainerMenu menu = null;
                try {
                    byte[] extraData = api.openingData(session, binding, host);
                    menu = InterfacePlatform.get().createMenuWithExtraData(binding.type(), nextWindowId.getAsInt(),
                        session.player().getInventory(), extraData, session.registries());
                    if (menu == null) continue;
                    if (menu.getType() != binding.type() || api.menuTarget.invoke(menu) != host) {
                        report.accept("ae2: factory did not bind the requested sandbox host");
                        continue;
                    }
                    if (preferred == null && api.subMenuType.isInstance(menu)) continue;
                    api.setLocator.invoke(menu, api.locator(host));
                    Component title = api.title(binding, host);
                    if (title.getString().isEmpty()) title = blockEntity.getBlockState().getBlock().getName();
                    opened.add(new Opened(menu, title));
                    menu = null; // ownership passes to opened
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    report.accept("ae2: " + BuiltInRegistries.MENU.getKey(binding.type()) + " failed: "
                        + (ex.getCause() == null ? ex : ex.getCause()));
                } finally {
                    if (menu != null) release(session, menu, report);
                }
            }
            if (opened.size() == 1) return opened.removeFirst();
            report.accept(opened.isEmpty() ? "ae2: no main menu factory matched " + host.getClass().getName()
                : "ae2: ambiguous main menus " + opened.stream()
                    .map(result -> BuiltInRegistries.MENU.getKey(result.menu().getType()).toString()).toList()
                    + "; capture the intended interface");
            return null;
        } finally {
            for (Opened result : opened) release(session, result.menu(), report);
        }
    }

    private static void release(SandboxSession session, AbstractContainerMenu menu, Consumer<String> report) {
        try {
            menu.removed(session.player());
        } catch (Throwable ex) {
            report.accept("ae2: rejected menu cleanup failed: " + ex);
        }
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Bridge {
        final Class<?> blockEntityType = Class.forName("appeng.blockentity.AEBaseBlockEntity");
        final Class<?> partHostType = Class.forName("appeng.api.parts.IPartHost");
        final Class<?> partType = Class.forName("appeng.parts.AEBasePart");
        final Class<?> subMenuType = Class.forName("appeng.menu.ISubMenu");
        final Class<?> builderType = Class.forName("appeng.menu.implementations.MenuTypeBuilder");
        final Field registry = field(Class.forName("appeng.menu.MenuOpener"), "registry");
        final Field hostType = field(builderType, "hostInterface");
        final Field menuTitle = field(builderType, "menuTitleStrategy");
        final Field initialData = field(builderType, "initialDataSerializer");
        final Method serializeInitial = Class.forName(builderType.getName() + "$InitialDataSerializer")
            .getMethod("serializeInitialData", Object.class, RegistryFriendlyByteBuf.class);
        final Class<?> locators = Class.forName("appeng.menu.locator.MenuLocators");
        final Class<?> locatorType = Class.forName("appeng.menu.locator.MenuHostLocator");
        final Method blockLocator = locators.getMethod("forBlockEntity", BlockEntity.class);
        final Method partLocator = locators.getMethod("forPart", partType);
        final Method writeLocator = locators.getMethod("writeToPacket", FriendlyByteBuf.class, locatorType);
        final Method selectPart = partHostType.getMethod("selectPartLocal", Vec3.class);
        final Field selectedPart = Class.forName("appeng.api.parts.SelectedPart").getField("part");
        final Method getPart = partHostType.getMethod("getPart", Direction.class);
        final Class<?> menuBase = Class.forName("appeng.menu.AEBaseMenu");
        final Method menuTarget = menuBase.getMethod("getTarget");
        final Method setLocator = menuBase.getMethod("setLocator", locatorType);
        private int registrySize = -1;
        private List<Binding> bindings = List.of();

        Bridge() throws ReflectiveOperationException {}

        List<Binding> bindings() throws ReflectiveOperationException {
            Map<?, ?> registered = (Map<?, ?>) registry.get(null);
            if (registrySize == registered.size()) return bindings;
            List<Binding> discovered = new ArrayList<>();
            for (Map.Entry<?, ?> entry : registered.entrySet()) {
                if (!(entry.getKey() instanceof MenuType<?> type)) continue;
                Object opener = entry.getValue();
                // The bound method reference retains its builder. Inspect the field's type,
                // not the compiler-generated lambda name or captured-field name.
                for (Field captured : opener.getClass().getDeclaredFields()) {
                    if (!builderType.isAssignableFrom(captured.getType())) continue;
                    captured.setAccessible(true);
                    Object builder = captured.get(opener);
                    discovered.add(new Binding(type, (Class<?>) hostType.get(builder), builder));
                    break;
                }
            }
            bindings = List.copyOf(discovered);
            registrySize = registered.size();
            return bindings;
        }

        @Nullable
        Object host(BlockEntity blockEntity, InterfaceDescriptor descriptor) throws ReflectiveOperationException {
            if (!partHostType.isInstance(blockEntity)) return blockEntity;
            InterfaceDescriptor.Hit hit = descriptor.hit;
            if (hit != null && hit.point() != null) {
                double[] point = hit.point();
                return selectedPart.get(selectPart.invoke(blockEntity, new Vec3(point[0], point[1], point[2])));
            }
            Direction side = hit == null ? null : Direction.byName(hit.face());
            return getPart.invoke(blockEntity, side);
        }

        @Nullable
        MenuType<?> preferredMenu(Object host, Player player) throws ReflectiveOperationException {
            // Terminals and buses expose their actual primary menu, which can share the
            // same host interface with other menus. Respect virtual overrides in AE2/addons.
            for (Class<?> owner = host.getClass(); owner != null; owner = owner.getSuperclass()) {
                for (Method method : owner.getDeclaredMethods()) {
                    if (!method.getName().equals("getMenuType") || !MenuType.class.isAssignableFrom(method.getReturnType())) continue;
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 0 || (params.length == 1 && params[0] == Player.class)) {
                        method.setAccessible(true);
                        return (MenuType<?>) method.invoke(host, params.length == 0 ? new Object[0] : new Object[]{player});
                    }
                }
            }
            return null;
        }

        Object locator(Object host) throws ReflectiveOperationException {
            return partType.isInstance(host) ? partLocator.invoke(null, host) : blockLocator.invoke(null, host);
        }

        byte[] openingData(SandboxSession session, Binding binding, Object host) throws ReflectiveOperationException {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), session.registries());
            try {
                writeLocator.invoke(null, buffer, locator(host));
                buffer.writeBoolean(false);
                Object serializer = initialData.get(binding.builder());
                if (serializer != null) serializeInitial.invoke(serializer, host, buffer);
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return bytes;
            } finally {
                buffer.release();
            }
        }

        @SuppressWarnings("unchecked")
        Component title(Binding binding, Object host) throws ReflectiveOperationException {
            return ((Function<Object, Component>) menuTitle.get(binding.builder())).apply(host);
        }
    }
}
