package com.nododiiiii.ponderer.runtime.spi;

import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import java.util.function.IntSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;

@FunctionalInterface
public interface InterfaceMenuAdapter {
    record Opened(AbstractContainerMenu menu, Component title) {}
    @Nullable Opened open(SandboxSession session, InterfaceDescriptor descriptor,
                         IntSupplier nextWindowId, Consumer<String> report) throws ReflectiveOperationException;
}
