package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.EntryTextSupport;
import com.nododiiiii.ponderer.ui.catnip.FormBoxWidget;
import com.nododiiiii.ponderer.ui.catnip.PonderIconStencils;
import com.nododiiiii.ponderer.ui.catnip.SearchableListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

public class PondererKeyBindingsScreen extends AbstractDeclarativeListScreen {

    private static final int BINDING_BUTTON_MIN_WIDTH = 110;
    private static final int RESET_BUTTON_WIDTH = 20;

    @Nullable
    private ModKeyBindings.ManagedBinding listeningBinding;

    public PondererKeyBindingsScreen(@Nullable Screen parent) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.function_page.keybindings", UILayoutConstants.EDITOR_LIST_W);
    }

    @Override
    protected void init() {
        super.init();
        configureSidebarButtons();
        if (listeningBinding == null) {
            setInfoMessage(UIText.of("ponderer.ui.keybindings.instructions"));
        }
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        for (ModKeyBindings.ManagedBinding binding : ModKeyBindings.managedBindings()) {
            entries.add(new KeyBindingEntry(binding));
        }
    }

    @Override
    protected boolean hasUnsavedChanges() {
        return false;
    }

    @Override
    protected int getUnsavedChangeCount() {
        return 0;
    }

    @Override
    protected boolean saveEdits() {
        return false;
    }

    @Override
    protected void discardEdits() {
    }

    @Override
    protected boolean isSaveButtonActive() {
        return false;
    }

    @Override
    protected boolean isDiscardButtonActive() {
        return hasAnyNonDefaultBinding();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningBinding != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningBinding = null;
                setInfoMessage(UIText.of("ponderer.ui.keybindings.status.cancelled"));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                applyBinding(listeningBinding, InputConstants.UNKNOWN);
                return true;
            }

            applyBinding(listeningBinding, InputConstants.getKey(keyCode, scanCode));
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (listeningBinding != null) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void beginListening(ModKeyBindings.ManagedBinding binding) {
        listeningBinding = binding;
        setInfoMessage(UIText.of("ponderer.ui.keybindings.status.listening", actionText(binding)));
    }

    private void applyBinding(ModKeyBindings.ManagedBinding binding, InputConstants.Key key) {
        binding.mapping().setKey(key);
        saveBindings();
        listeningBinding = null;

        if (InputConstants.UNKNOWN.equals(key)) {
            setInfoMessage(UIText.of("ponderer.ui.keybindings.status.cleared", actionText(binding)));
            return;
        }

        setInfoMessage(UIText.of("ponderer.ui.keybindings.status.bound", actionText(binding), keyText(binding.mapping())));
    }

    private void resetAllBindings() {
        boolean changed = false;
        for (ModKeyBindings.ManagedBinding binding : ModKeyBindings.managedBindings()) {
            if (!binding.isDefault()) {
                binding.resetToDefault();
                changed = true;
            }
        }

        if (!changed) {
            setInfoMessage(UIText.of("ponderer.ui.keybindings.instructions"));
            return;
        }

        saveBindings();
        listeningBinding = null;
        setInfoMessage(UIText.of("ponderer.ui.keybindings.status.reset_all"));
    }

    private boolean hasAnyNonDefaultBinding() {
        for (ModKeyBindings.ManagedBinding binding : ModKeyBindings.managedBindings()) {
            if (!binding.isDefault()) {
                return true;
            }
        }
        return false;
    }

    private void resetBinding(ModKeyBindings.ManagedBinding binding) {
        if (binding.isDefault()) {
            return;
        }
        binding.resetToDefault();
        saveBindings();
        listeningBinding = null;
        setInfoMessage(UIText.of("ponderer.ui.keybindings.status.reset_one", actionText(binding), defaultKeyText(binding)));
    }

    private void configureSidebarButtons() {
        if (saveChanges != null) {
            saveChanges.visible = false;
            saveChanges.active = false;
            saveChanges.updateGradientFromState();
        }
        if (discardChanges != null) {
            discardChanges.visible = true;
            discardChanges.withCallback(this::resetAllBindings);
            PonderIconStencils.attach(discardChanges, PonderIconStencils.centered(PonderGuiTextures.ICON_CONFIG_RESET));
            discardChanges.getToolTip().clear();
            discardChanges.getToolTip().add(Component.literal(UIText.of("ponderer.ui.keybindings.reset_all")));
            discardChanges.getToolTip().add(Component.literal(UIText.of("ponderer.ui.keybindings.reset_all.tooltip")));
            discardChanges.active = hasAnyNonDefaultBinding();
            discardChanges.updateGradientFromState();
        }
    }

    private String bindingButtonLabel(ModKeyBindings.ManagedBinding binding) {
        if (binding.equals(listeningBinding)) {
            return UIText.of("ponderer.ui.keybindings.listening_button");
        }
        return keyText(binding.mapping());
    }

    private String bindingButtonTooltip(ModKeyBindings.ManagedBinding binding) {
        if (binding.equals(listeningBinding)) {
            return UIText.of("ponderer.ui.keybindings.rebind.tooltip_listening");
        }
        return UIText.of("ponderer.ui.keybindings.rebind.tooltip", defaultKeyText(binding));
    }

    private int bindingButtonColor(ModKeyBindings.ManagedBinding binding) {
        if (binding.equals(listeningBinding)) {
            return 0xFF80FFFF;
        }
        if (hasConflict(binding)) {
            return 0xFFFFC060;
        }
        return 0xFFFFFF;
    }

    private boolean hasConflict(ModKeyBindings.ManagedBinding binding) {
        if (binding.mapping().isUnbound()) {
            return false;
        }

        for (ModKeyBindings.ManagedBinding other : ModKeyBindings.managedBindings()) {
            if (other == binding) {
                continue;
            }
            if (binding.mapping().same(other.mapping())) {
                return true;
            }
        }
        return false;
    }

    private static String actionText(ModKeyBindings.ManagedBinding binding) {
        return Component.translatable(binding.translationKey()).getString();
    }

    private static String keyText(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }

    private static String defaultKeyText(ModKeyBindings.ManagedBinding binding) {
        return binding.defaultKey().getDisplayName().getString();
    }

    private void saveBindings() {
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    private final class KeyBindingEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

        private static final int BINDING_VISUAL_INSET = 4;
        private static final int CONTROL_GAP = EntryTextSupport.rightControlGap();

        private final ModKeyBindings.ManagedBinding binding;
        private final String searchText;
        private final BoxWidget bindingButton;
        private final BoxWidget resetButton;

        private KeyBindingEntry(ModKeyBindings.ManagedBinding binding) {
            super(actionText(binding));
            this.binding = binding;
            this.searchText = actionText(binding).toLowerCase(java.util.Locale.ROOT);

            getLabelTooltip().add(Component.literal(actionText(binding)));
            getLabelTooltip().add(Component.literal(UIText.of("ponderer.ui.keybindings.rebind.tooltip", defaultKeyText(binding))));

            this.bindingButton = new FormBoxWidget(0, 0, 120, 16)
                .withCallback(() -> beginListening(binding));
            this.resetButton = new FormBoxWidget(0, 0, RESET_BUTTON_WIDTH, 16)
                .withPadding(2, 2)
                .withCallback(() -> resetBinding(binding));
            PonderIconStencils.attach(this.resetButton, PonderIconStencils.centered(PonderGuiTextures.ICON_CONFIG_RESET));
            this.resetButton.active = !binding.isDefault();
            this.resetButton.updateGradientFromState();
            listeners.add(bindingButton);
            listeners.add(resetButton);
        }

        @Override
        public boolean matchesQuery(String query) {
            return searchText.contains(query);
        }

        @Override
        public void highlightEntry() {
            annotations.put("highlight", ":)");
        }

        @Override
        protected int getLabelWidth(int totalWidth) {
            return EntryTextSupport.compactLabelWidth(totalWidth);
        }

        @Override
        public void tick() {
            super.tick();
            bindingButton.tick();
            updateButtonState(resetButton, !binding.isDefault());
            resetButton.tick();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

            refreshButtonTooltips();

            int labelWidth = getLabelWidth(width);
            EntryTextSupport.AlignedControlBounds controlBounds = EntryTextSupport.rightAlignedControlBounds(
                x,
                width,
                labelWidth,
                EntryTextSupport.halfWidthControlScale(),
                BINDING_BUTTON_MIN_WIDTH + CONTROL_GAP + RESET_BUTTON_WIDTH);

            int buttonY = y + 10;
            int buttonHeight = Math.max(16, height - 20);

            int resetX = controlBounds.right() - RESET_BUTTON_WIDTH;
            resetButton.setX(resetX);
            resetButton.setY(buttonY);
            resetButton.setWidth(RESET_BUTTON_WIDTH);
            resetButton.setHeight(buttonHeight);
            updateButtonState(resetButton, !binding.isDefault());
            resetButton.render(graphics, mouseX, mouseY, partialTicks);

            int bindingWidth = Math.max(BINDING_BUTTON_MIN_WIDTH, resetX - CONTROL_GAP - controlBounds.x());
            int bindingVisualX = controlBounds.x();
            int bindingWidgetX = bindingVisualX + BINDING_VISUAL_INSET;
            int bindingWidgetWidth = Math.max(1, bindingWidth - BINDING_VISUAL_INSET * 2);
            bindingButton.setX(bindingWidgetX);
            bindingButton.setY(buttonY);
            bindingButton.setWidth(bindingWidgetWidth);
            bindingButton.setHeight(buttonHeight);
            bindingButton.render(graphics, mouseX, mouseY, partialTicks);

            graphics.drawCenteredString(Minecraft.getInstance().font, bindingButtonLabel(binding),
                bindingButton.getX() + bindingButton.getWidth() / 2,
                bindingButton.getY() + (bindingButton.getHeight() - 8) / 2,
                bindingButtonColor(binding));
        }

        private void refreshButtonTooltips() {
            bindingButton.getToolTip().clear();
            bindingButton.getToolTip().add(Component.literal(bindingButtonTooltip(binding)));

            resetButton.getToolTip().clear();
            resetButton.getToolTip().add(Component.literal(
                UIText.of("ponderer.ui.keybindings.reset_one.tooltip", defaultKeyText(binding))));
        }
    }
}
