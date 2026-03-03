package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings sub-page for AI scene generation configuration.
 * Accessed from FunctionScreen Settings section.
 */
public class AiConfigScreen extends AbstractSimiScreen {

    private static final int WIDTH = UILayoutConstants.WIDE_WINDOW_W;
    private static final int ROW_H = UILayoutConstants.ROW_H;
    private static final int MARGIN = 12;
    private static final int LABEL_W = 80;

    // ─── Scroll state ────────────────────────────────────────────────────
    private static final int FORM_TOP_Y = 30;
    private static final int BOTTOM_H = 40;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private record FormWidgetRecord(AbstractWidget widget, int contentOffsetY) {}
    private final List<FormWidgetRecord> formWidgetRecords = new ArrayList<>();

    private HintableTextFieldWidget providerField;
    private HintableTextFieldWidget baseUrlField;
    private HintableTextFieldWidget apiKeyField;
    private HintableTextFieldWidget modelField;
    private HintableTextFieldWidget proxyField;
    private HintableTextFieldWidget maxTokensField;
    private boolean trustAllSsl;
    private boolean webUseProxy;
    private boolean initialized = false;

    private record ClickableButton(int x, int y, int w, int h, String label, Runnable action, boolean scrollable) {}
    private final List<ClickableButton> clickableButtons = new ArrayList<>();

    public AiConfigScreen() {
        super(Component.translatable("ponderer.ui.ai_config.title"));
    }

    /** Raw content height before clamping. */
    private int getContentHeight() {
        return 36 + 8 * ROW_H + BOTTOM_H;
    }

    private int getWindowHeight() {
        int content = getContentHeight();
        int maxH = height - UILayoutConstants.SCREEN_MARGIN * 2;
        return Math.min(content, maxH);
    }

    private boolean isScrollEnabled() { return maxScroll > 0; }

    private int viewportTop() { return guiTop + FORM_TOP_Y; }
    private int viewportBottom() { return guiTop + getWindowHeight() - BOTTOM_H; }

    private void updateScrollPositions() {
        int vpTop = viewportTop();
        int vpBot = viewportBottom();
        for (FormWidgetRecord rec : formWidgetRecords) {
            int newY = vpTop + rec.contentOffsetY - scrollOffset;
            rec.widget.setY(newY);
            rec.widget.visible = (newY + rec.widget.getHeight() > vpTop && newY < vpBot);
            rec.widget.active = rec.widget.visible;
        }
    }

    @Override
    protected void init() {
        // On re-init, save current widget values before rebuilding
        String curProvider, curBaseUrl, curApiKey, curModel, curProxy, curMaxTokens;
        if (initialized) {
            curProvider = providerField.getValue();
            curBaseUrl = baseUrlField.getValue();
            curApiKey = apiKeyField.getValue();
            curModel = modelField.getValue();
            curProxy = proxyField.getValue();
            curMaxTokens = maxTokensField.getValue();
        } else {
            curProvider = Config.AI_PROVIDER.get();
            curBaseUrl = Config.AI_API_BASE_URL.get();
            curApiKey = Config.AI_API_KEY.get();
            curModel = Config.AI_MODEL.get();
            curProxy = Config.AI_PROXY.get();
            curMaxTokens = String.valueOf(Config.AI_MAX_TOKENS.get());
            trustAllSsl = Config.AI_TRUST_ALL_SSL.get();
            webUseProxy = Config.AI_WEB_USE_PROXY.get();
            initialized = true;
        }

        setWindowSize(WIDTH, getWindowHeight());
        super.init();
        clickableButtons.clear();
        formWidgetRecords.clear();

        // Compute scroll bounds
        int contentH = getContentHeight() - FORM_TOP_Y - BOTTOM_H;
        int viewportH = getWindowHeight() - FORM_TOP_Y - BOTTOM_H;
        maxScroll = Math.max(0, contentH - viewportH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        var font = Minecraft.getInstance().font;
        int fieldX = guiLeft + MARGIN + LABEL_W + 4;
        int fieldW = WIDTH - MARGIN * 2 - LABEL_W - 4;
        int contentY = 0; // relative to form top

        // Provider
        providerField = new SoftHintTextFieldWidget(font, fieldX, guiTop + FORM_TOP_Y + contentY + 2, fieldW, 16);
        providerField.setHint(UIText.of("ponderer.ui.ai_config.provider.hint"));
        providerField.setMaxLength(64);
        providerField.setValue(curProvider);
        addRenderableWidget(providerField);
        formWidgetRecords.add(new FormWidgetRecord(providerField, contentY + 2));
        contentY += ROW_H;

        // Base URL
        baseUrlField = new SoftHintTextFieldWidget(font, fieldX, guiTop + FORM_TOP_Y + contentY + 2, fieldW, 16);
        baseUrlField.setHint(UIText.of("ponderer.ui.ai_config.base_url.hint"));
        baseUrlField.setMaxLength(256);
        baseUrlField.setValue(curBaseUrl);
        addRenderableWidget(baseUrlField);
        formWidgetRecords.add(new FormWidgetRecord(baseUrlField, contentY + 2));
        contentY += ROW_H;

        // API Key
        apiKeyField = new SoftHintTextFieldWidget(font, fieldX, guiTop + FORM_TOP_Y + contentY + 2, fieldW, 16);
        apiKeyField.setHint(UIText.of("ponderer.ui.ai_config.api_key.hint"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setValue(curApiKey);
        addRenderableWidget(apiKeyField);
        formWidgetRecords.add(new FormWidgetRecord(apiKeyField, contentY + 2));
        contentY += ROW_H;

        // Model
        modelField = new SoftHintTextFieldWidget(font, fieldX, guiTop + FORM_TOP_Y + contentY + 2, fieldW, 16);
        modelField.setHint(UIText.of("ponderer.ui.ai_config.model.hint"));
        modelField.setMaxLength(128);
        modelField.setValue(curModel);
        addRenderableWidget(modelField);
        formWidgetRecords.add(new FormWidgetRecord(modelField, contentY + 2));
        contentY += ROW_H;

        // Proxy
        proxyField = new SoftHintTextFieldWidget(font, fieldX, guiTop + FORM_TOP_Y + contentY + 2, fieldW, 16);
        proxyField.setHint(UIText.of("ponderer.ui.ai_config.proxy.hint"));
        proxyField.setMaxLength(128);
        proxyField.setValue(curProxy);
        addRenderableWidget(proxyField);
        formWidgetRecords.add(new FormWidgetRecord(proxyField, contentY + 2));
        contentY += ROW_H;

        // Max Tokens
        maxTokensField = new SoftHintTextFieldWidget(font, fieldX, guiTop + FORM_TOP_Y + contentY + 2, fieldW, 16);
        maxTokensField.setHint(UIText.of("ponderer.ui.ai_config.max_tokens.hint"));
        maxTokensField.setMaxLength(10);
        maxTokensField.setValue(curMaxTokens);
        addRenderableWidget(maxTokensField);
        formWidgetRecords.add(new FormWidgetRecord(maxTokensField, contentY + 2));
        contentY += ROW_H;

        // Trust All SSL (toggle button) — scrollable
        clickableButtons.add(new ClickableButton(fieldX, guiTop + FORM_TOP_Y + contentY + 1, fieldW, 16,
            UIText.of("ponderer.ui.ai_config.trust_ssl") + ": " + (trustAllSsl ? "ON" : "OFF"),
            this::toggleTrustSsl, true));
        contentY += ROW_H;

        // Web Use Proxy (toggle button) — scrollable
        clickableButtons.add(new ClickableButton(fieldX, guiTop + FORM_TOP_Y + contentY + 1, fieldW, 16,
            UIText.of("ponderer.ui.ai_config.web_use_proxy") + ": " + (webUseProxy ? "ON" : "OFF"),
            this::toggleWebUseProxy, true));

        // Save & Back buttons (fixed, not scrollable)
        int btnY = guiTop + getWindowHeight() - 32;
        clickableButtons.add(new ClickableButton(guiLeft + MARGIN, btnY, 70, 20,
            UIText.of("ponderer.ui.ai_config.save"), this::doSave, false));
        clickableButtons.add(new ClickableButton(guiLeft + WIDTH - MARGIN - 70, btnY, 70, 20,
            UIText.of("ponderer.ui.function_page.back"), this::goBack, false));

        // Apply scroll positions
        updateScrollPositions();

        // Focus first field
        providerField.setFocused(true);
        setFocused(providerField);
    }

    private void doSave() {
        Config.AI_PROVIDER.set(providerField.getValue().trim());
        Config.AI_API_BASE_URL.set(baseUrlField.getValue().trim());
        Config.AI_API_KEY.set(apiKeyField.getValue().trim());
        Config.AI_MODEL.set(modelField.getValue().trim());
        Config.AI_PROXY.set(proxyField.getValue().trim());

        // Parse and validate max_tokens
        try {
            int maxTokens = Integer.parseInt(maxTokensField.getValue().trim());
            // Clamp to valid range (1024-65536)
            maxTokens = Math.max(1024, Math.min(65536, maxTokens));
            Config.AI_MAX_TOKENS.set(maxTokens);
        } catch (NumberFormatException e) {
            // If invalid, keep default
            Config.AI_MAX_TOKENS.set(16384);
        }

        Config.AI_TRUST_ALL_SSL.set(trustAllSsl);
        Config.AI_WEB_USE_PROXY.set(webUseProxy);
        goBack();
    }

    private void toggleTrustSsl() {
        trustAllSsl = !trustAllSsl;
        init(minecraft, width, height);
    }

    private void toggleWebUseProxy() {
        webUseProxy = !webUseProxy;
        init(minecraft, width, height);
    }

    private void goBack() {
        Minecraft.getInstance().setScreen(new FunctionScreen());
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int wH = getWindowHeight();
        new BoxElement()
            .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
            .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true), new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(WIDTH, wH)
            .render(graphics);

        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, this.title, guiLeft + WIDTH / 2, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WIDTH - 5, guiTop + 21, UILayoutConstants.COLOR_SEPARATOR);

        // ── Scrollable form area (labels) with scissor ──
        int vpTop = viewportTop();
        int vpBot = viewportBottom();
        graphics.enableScissor(guiLeft, vpTop, guiLeft + WIDTH, vpBot);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        int y = guiTop + FORM_TOP_Y;
        int lx = guiLeft + MARGIN;
        int lc = UILayoutConstants.COLOR_LABEL;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.provider"), lx, y + 5, lc);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.base_url"), lx, y + 5, lc);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.api_key"), lx, y + 5, lc);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.model"), lx, y + 5, lc);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.proxy"), lx, y + 5, lc);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.max_tokens"), lx, y + 5, lc);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.trust_ssl"), lx, y + 5, lc);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.web_use_proxy"), lx, y + 5, lc);

        // Render scrollable toggle buttons
        for (ClickableButton btn : clickableButtons) {
            if (!btn.scrollable) continue;
            int btnY = btn.y;
            renderClickableButton(graphics, font, btn, mouseX, (int)(mouseY + scrollOffset), btnY);
        }

        graphics.pose().popPose();
        graphics.disableScissor();

        // ── Fixed bottom buttons ──
        for (ClickableButton btn : clickableButtons) {
            if (btn.scrollable) continue;
            boolean hovered = mouseX >= btn.x && mouseX < btn.x + btn.w
                && mouseY >= btn.y && mouseY < btn.y + btn.h;
            int bgColor = hovered ? 0x80_4466aa : 0x60_333366;
            int borderColor = hovered ? 0xCC_6688cc : 0x60_555588;
            graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, bgColor);
            graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + 1, borderColor);
            graphics.fill(btn.x, btn.y + btn.h - 1, btn.x + btn.w, btn.y + btn.h, borderColor);
            graphics.fill(btn.x, btn.y, btn.x + 1, btn.y + btn.h, borderColor);
            graphics.fill(btn.x + btn.w - 1, btn.y, btn.x + btn.w, btn.y + btn.h, borderColor);
            int textWidth = font.width(btn.label);
            graphics.drawString(font, btn.label, btn.x + (btn.w - textWidth) / 2,
                btn.y + (btn.h - font.lineHeight) / 2 + 1, hovered ? 0xFFFFFF : UILayoutConstants.COLOR_LABEL);
        }

        // ── Scrollbar ──
        if (isScrollEnabled()) {
            renderScrollbar(graphics, vpTop, vpBot);
        }
    }

    private void renderClickableButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        ClickableButton btn, int mouseX, int mouseY, int btnY) {
        boolean hovered = mouseX >= btn.x && mouseX < btn.x + btn.w
            && mouseY >= btnY && mouseY < btnY + btn.h;
        int bgColor = hovered ? 0x80_4466aa : 0x60_333366;
        int borderColor = hovered ? 0xCC_6688cc : 0x60_555588;
        graphics.fill(btn.x, btnY, btn.x + btn.w, btnY + btn.h, bgColor);
        graphics.fill(btn.x, btnY, btn.x + btn.w, btnY + 1, borderColor);
        graphics.fill(btn.x, btnY + btn.h - 1, btn.x + btn.w, btnY + btn.h, borderColor);
        graphics.fill(btn.x, btnY, btn.x + 1, btnY + btn.h, borderColor);
        graphics.fill(btn.x + btn.w - 1, btnY, btn.x + btn.w, btnY + btn.h, borderColor);
        int textWidth = font.width(btn.label);
        graphics.drawString(font, btn.label, btn.x + (btn.w - textWidth) / 2,
            btnY + (btn.h - font.lineHeight) / 2 + 1, hovered ? 0xFFFFFF : UILayoutConstants.COLOR_LABEL);
    }

    private void renderScrollbar(GuiGraphics graphics, int vpTop, int vpBot) {
        int trackX = guiLeft + WIDTH - UILayoutConstants.SCROLLBAR_W - 2;
        int trackH = vpBot - vpTop;
        graphics.fill(trackX, vpTop, trackX + UILayoutConstants.SCROLLBAR_W, vpBot, UILayoutConstants.COLOR_SCROLLBAR_BG);

        int contentH = getContentHeight() - FORM_TOP_Y - BOTTOM_H;
        int thumbH = Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB,
            (int) ((float) trackH * trackH / contentH));
        int thumbY = vpTop + (int) ((float) scrollOffset / maxScroll * (trackH - thumbH));
        graphics.fill(trackX, thumbY, trackX + UILayoutConstants.SCROLLBAR_W, thumbY + thumbH,
            UILayoutConstants.COLOR_SCROLLBAR_FG);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickableButton btn : clickableButtons) {
                double adjY = btn.scrollable ? mouseY + scrollOffset : mouseY;
                if (mouseX >= btn.x && mouseX < btn.x + btn.w
                    && adjY >= btn.y && adjY < btn.y + btn.h) {
                    if (btn.scrollable) {
                        // Check if within viewport
                        int vpTop = viewportTop();
                        int vpBot = viewportBottom();
                        if (mouseY < vpTop || mouseY >= vpBot) continue;
                    }
                    btn.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isScrollEnabled()) {
            scrollOffset = Mth.clamp(scrollOffset - (int)(scrollY * UILayoutConstants.SCROLL_SPEED), 0, maxScroll);
            updateScrollPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(keyCode, scanCode, modifiers);
        if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
            return true;
        // Block all other keys when a text field is focused to prevent game keybinds from firing
        if (getFocused() instanceof net.minecraft.client.gui.components.EditBox)
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() != null && getFocused().charTyped(codePoint, modifiers))
            return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        goBack();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
