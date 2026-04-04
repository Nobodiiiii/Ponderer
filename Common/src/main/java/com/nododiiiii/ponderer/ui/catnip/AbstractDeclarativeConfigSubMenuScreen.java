package com.nododiiiii.ponderer.ui.catnip;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigHelper;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.SubMenuConfigScreen;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractDeclarativeConfigSubMenuScreen extends SubMenuConfigScreen {

    private final String modId;
    private final String scopeKey;
    private final String titleKey;

    protected AbstractDeclarativeConfigSubMenuScreen(@Nullable Screen parent, String modId, String scopeKey,
                                                     String titleKey, ModConfig.Type type,
                                                     ForgeConfigSpec configSpec, UnmodifiableConfig configGroup) {
        super(parent, UIText.of(titleKey), type, configSpec, configGroup);
        this.modId = modId;
        this.scopeKey = scopeKey;
        this.titleKey = titleKey;
        ConfigScreen.modID = modId;
        ConfigHelper.changes.clear();
    }

    @Override
    protected void init() {
        ConfigScreen.modID = modId;
        super.init();

        if (goBack != null) {
            goBack.withCallback(this::attemptBackToParent);
        }
        if (search != null) {
            search.setResponder(this::updateLocalizedFilter);
        }
        configureResetButton();
        rebuildEntries();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (search != null && hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
            search.setFocused(true);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            attemptBackToParent();
            return true;
        }

        return false;
    }

    @Override
    public void onClose() {
        attemptBackToParent();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String breadcrumb = UIText.of("ponderer.ui.mod_name")
            + " > "
            + UIText.of(scopeKey)
            + " > "
            + UIText.of(titleKey);
        graphics.drawCenteredString(
            minecraft.font,
            breadcrumb,
            width / 2,
            15,
            UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
    }

    protected final void rebuildEntries() {
        if (list == null) {
            return;
        }

        list.children().clear();
        collectEntries(list.children());

        if (search != null) {
            updateLocalizedFilter(search.getValue());
        }
    }

    protected final void addStringConfigEntry(List<ConfigScreenList.Entry> entries, String labelKey,
                                              @Nullable String hintKey, @Nullable String tooltipKey,
                                              ForgeConfigSpec.ConfigValue<String> value) {
        entries.add(new LocalizedStringConfigEntry(labelKey, hintKey, tooltipKey, value, specOf(value)));
    }

    protected final void addBooleanConfigEntry(List<ConfigScreenList.Entry> entries, String labelKey,
                                               @Nullable String tooltipKey,
                                               ForgeConfigSpec.ConfigValue<Boolean> value) {
        entries.add(new LocalizedBooleanConfigEntry(labelKey, tooltipKey, value, specOf(value)));
    }

    protected final void addIntegerConfigEntry(List<ConfigScreenList.Entry> entries, String labelKey,
                                               @Nullable String hintKey, @Nullable String tooltipKey,
                                               ForgeConfigSpec.ConfigValue<Integer> value) {
        entries.add(new LocalizedIntegerConfigEntry(labelKey, hintKey, tooltipKey, value, specOf(value)));
    }

    protected abstract void collectEntries(List<ConfigScreenList.Entry> entries);

    @Nullable
    protected String getResetLabelKey() {
        return null;
    }

    @Nullable
    protected String getResetTooltipKey() {
        return null;
    }

    @Nullable
    protected String getResetConfirmKey() {
        return null;
    }

    private void configureResetButton() {
        if (resetAll == null || getResetConfirmKey() == null) {
            return;
        }

        resetAll.withCallback((x, y) -> new ConfirmationScreen()
            .centered()
            .withText(Component.translatable(getResetConfirmKey()))
            .withAction(success -> {
                if (success) {
                    resetConfig(configGroup);
                }
            })
            .open(this));

        if (getResetLabelKey() != null) {
            resetAll.getToolTip().clear();
            resetAll.getToolTip().add(Component.translatable(getResetLabelKey()));
            if (getResetTooltipKey() != null) {
                resetAll.getToolTip().addAll(FontHelper.cutTextComponent(
                    Component.translatable(getResetTooltipKey()),
                    Palette.ALL_GRAY));
            }
        }
    }

    private void updateLocalizedFilter(String query) {
        ListSearchHelper.applySearchFilter(list, search, query, 40);
    }

    private void attemptBackToParent() {
        if (ConfigHelper.changes.isEmpty()) {
            ScreenOpener.open(parent);
            return;
        }

        showLeavingPrompt(response -> {
            if (response == ConfirmationScreen.Response.Cancel) {
                return;
            }
            if (response == ConfirmationScreen.Response.Confirm) {
                saveChanges();
            } else {
                ConfigHelper.changes.clear();
            }
            ScreenOpener.open(parent);
        });
    }

    private <T> ForgeConfigSpec.ValueSpec specOf(ForgeConfigSpec.ConfigValue<T> value) {
        return spec.getRaw(value.getPath());
    }
}
