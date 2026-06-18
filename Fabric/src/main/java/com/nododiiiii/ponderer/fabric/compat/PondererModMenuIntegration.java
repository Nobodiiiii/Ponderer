package com.nododiiiii.ponderer.fabric.compat;

import com.nododiiiii.ponderer.Ponderer;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.createmod.catnip.config.ui.BaseConfigScreen;

public class PondererModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new BaseConfigScreen(parent, Ponderer.MODID);
    }
}
