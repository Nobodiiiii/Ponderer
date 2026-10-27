package com.nododiiiii.ponderer.editor.screen.widgets;

import com.nododiiiii.ponderer.editor.screen.FieldType;
import com.nododiiiii.ponderer.editor.screen.UIText;
import net.createmod.catnip.gui.element.TextStencilElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LocalizedTextListEntry extends PlainTextListEntry {

    private static final int LANG_BUTTON_WIDTH = 28;

    private final Supplier<String> langGetter;
    private final BoxWidget langButton;
    private final TextStencilElement langText;

    public LocalizedTextListEntry(String labelKey, @Nullable String tooltipKey, @Nullable String hintKey,
                                  String initialValue, Supplier<String> langGetter,
                                  Runnable onToggle, Consumer<String> responder) {
        super(labelKey, tooltipKey, hintKey, initialValue, responder);
        setSharedField(FieldType.LOCALIZED_TEXT);
        this.langGetter = langGetter;
        this.langText = new TextStencilElement(Minecraft.getInstance().font, "en_us")
            .centered(true, true);
        this.langButton = new FormBoxWidget(0, 0, LANG_BUTTON_WIDTH, 16)
            .showingElement(langText)
            .withCallback(onToggle);
        this.langButton.getToolTip().add(Component.literal(UIText.of("ponderer.ui.language.toggle.tooltip")));
        this.langText.withElementRenderer(BoxWidget.gradientFactory.apply(langButton));
        listeners.add(langButton);
    }

    @Override
    public void tick() {
        super.tick();
        langButton.tick();
    }

    public BoxWidget langButton() {
        return langButton;
    }

    @Override
    protected boolean hasMultipleControls() {
        return true;
    }

    @Override
    protected int getTrailingWidth() {
        return EntryTextSupport.buttonWidth(shortLang(langGetter.get()), LANG_BUTTON_WIDTH) + EntryTextSupport.inputControlGap();
    }

    @Override
    protected void renderTrailing(GuiGraphics graphics, int y, int x, int width, int height,
                                  int mouseX, int mouseY, float partialTicks) {
        langText.withText(shortLang(langGetter.get()));
        int buttonWidth = EntryTextSupport.buttonWidth(shortLang(langGetter.get()), LANG_BUTTON_WIDTH);
        EntryTextSupport.setRowButtonBounds(langButton, EntryTextSupport.contentRight(x, width) - buttonWidth,
            buttonWidth, y, height);
        langButton.render(graphics, mouseX, mouseY, partialTicks);
    }

    private static String shortLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return "en_us";
        }
        return lang.length() > 5 ? lang.substring(0, 5) : lang;
    }
}
