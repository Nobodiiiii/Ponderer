package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.ProjectorMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public final class ModMenuTypes {

    public static final Supplier<MenuType<ProjectorMenu>> PROJECTOR =
        PondererServices.REGISTRATION.registerMenuType(
            "projector",
            (windowId, inventory, extraData) -> new ProjectorMenu(windowId, inventory, extraData));

    private ModMenuTypes() {
    }

    public static void init() {
    }
}
