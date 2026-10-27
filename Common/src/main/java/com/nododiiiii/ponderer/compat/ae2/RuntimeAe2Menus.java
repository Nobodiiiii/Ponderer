package com.nododiiiii.ponderer.compat.ae2;

import com.nododiiiii.ponderer.runtime.spi.InterfaceMenuAdapter;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public final class RuntimeAe2Menus implements InterfaceMenuAdapter {
    @Override public Opened open(SandboxSession session, InterfaceDescriptor descriptor,
                                 IntSupplier nextId, Consumer<String> report) throws ReflectiveOperationException {
        var opened = Ae2InterfaceCompat.open(session, descriptor, nextId, report);
        return opened == null ? null : new Opened(opened.menu(), opened.title());
    }
}
