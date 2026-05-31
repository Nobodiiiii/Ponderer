package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.gui.element.TextStencilElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

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
        this.langGetter = langGetter;
        this.langText = new TextStencilElement(Minecraft.getInstance().font, "en_us")
            .centered(true, true);
        this.langButton = new FormBoxWidget(0, 0, LANG_BUTTON_WIDTH, 16)
            .showingElement(langText)
            .withCallback(onToggle);
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
    protected int getTrailingWidth() {
        return LANG_BUTTON_WIDTH + CONTROL_GAP;
    }

    @Override
    protected void renderTrailing(GuiGraphics graphics, int y, int x, int width, int height,
                                  int mouseX, int mouseY, float partialTicks) {
        langText.withText(shortLang(langGetter.get()));
        langButton.setX(x + width - 4 - LANG_BUTTON_WIDTH);
        langButton.setY(y + 10);
        langButton.setWidth(LANG_BUTTON_WIDTH);
        langButton.setHeight(height - 20);
        langButton.render(graphics, mouseX, mouseY, partialTicks);
    }

    private static String shortLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return "en_us";
        }
        return lang.length() > 5 ? lang.substring(0, 5) : lang;
    }
}
