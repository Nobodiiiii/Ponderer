package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings sub-page for AI scene generation configuration.
 * Accessed from FunctionScreen Settings section.
 */
public class AiConfigScreen extends AbstractSimiScreen {

    private static final int WIDTH = 260;
    private static final int ROW_H = 22;
    private static final int MARGIN = 12;
    private static final int LABEL_W = 80;

    private HintableTextFieldWidget providerField;
    private HintableTextFieldWidget baseUrlField;
    private HintableTextFieldWidget apiKeyField;
    private HintableTextFieldWidget modelField;
    private HintableTextFieldWidget proxyField;
    private HintableTextFieldWidget maxTokensField;
    private boolean trustAllSsl;
    private boolean webUseProxy;
    private boolean initialized = false;

    private record ClickableButton(int x, int y, int w, int h, String label, Runnable action) {}
    private final List<ClickableButton> clickableButtons = new ArrayList<>();

    public AiConfigScreen() {
        super(Component.translatable("ponderer.ui.ai_config.title"));
    }

    private int getWindowHeight() {
        return 36 + 8 * ROW_H + 40;
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

        var font = Minecraft.getInstance().font;
        int fieldX = guiLeft + MARGIN + LABEL_W + 4;
        int fieldW = WIDTH - MARGIN * 2 - LABEL_W - 4;
        int y = guiTop + 30;

        // Provider
        providerField = new SoftHintTextFieldWidget(font, fieldX, y + 2, fieldW, 16);
        providerField.setHint(UIText.of("ponderer.ui.ai_config.provider.hint"));
        providerField.setMaxLength(64);
        providerField.setValue(curProvider);
        addRenderableWidget(providerField);
        y += ROW_H;

        // Base URL
        baseUrlField = new SoftHintTextFieldWidget(font, fieldX, y + 2, fieldW, 16);
        baseUrlField.setHint(UIText.of("ponderer.ui.ai_config.base_url.hint"));
        baseUrlField.setMaxLength(256);
        baseUrlField.setValue(curBaseUrl);
        addRenderableWidget(baseUrlField);
        y += ROW_H;

        // API Key
        apiKeyField = new SoftHintTextFieldWidget(font, fieldX, y + 2, fieldW, 16);
        apiKeyField.setHint(UIText.of("ponderer.ui.ai_config.api_key.hint"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setValue(curApiKey);
        addRenderableWidget(apiKeyField);
        y += ROW_H;

        // Model
        modelField = new SoftHintTextFieldWidget(font, fieldX, y + 2, fieldW, 16);
        modelField.setHint(UIText.of("ponderer.ui.ai_config.model.hint"));
        modelField.setMaxLength(128);
        modelField.setValue(curModel);
        addRenderableWidget(modelField);
        y += ROW_H;

        // Proxy
        proxyField = new SoftHintTextFieldWidget(font, fieldX, y + 2, fieldW, 16);
        proxyField.setHint(UIText.of("ponderer.ui.ai_config.proxy.hint"));
        proxyField.setMaxLength(128);
        proxyField.setValue(curProxy);
        addRenderableWidget(proxyField);
        y += ROW_H;

        // Max Tokens
        maxTokensField = new SoftHintTextFieldWidget(font, fieldX, y + 2, fieldW, 16);
        maxTokensField.setHint(UIText.of("ponderer.ui.ai_config.max_tokens.hint"));
        maxTokensField.setMaxLength(10);
        maxTokensField.setValue(curMaxTokens);
        addRenderableWidget(maxTokensField);
        y += ROW_H;

        // Trust All SSL (toggle button)
        clickableButtons.add(new ClickableButton(fieldX, y + 1, fieldW, 16,
            UIText.of("ponderer.ui.ai_config.trust_ssl") + ": " + (trustAllSsl ? "ON" : "OFF"),
            this::toggleTrustSsl));
        y += ROW_H;

        // Web Use Proxy (toggle button)
        clickableButtons.add(new ClickableButton(fieldX, y + 1, fieldW, 16,
            UIText.of("ponderer.ui.ai_config.web_use_proxy") + ": " + (webUseProxy ? "ON" : "OFF"),
            this::toggleWebUseProxy));

        // Save & Back buttons
        int btnY = guiTop + getWindowHeight() - 32;
        clickableButtons.add(new ClickableButton(guiLeft + MARGIN, btnY, 70, 20,
            UIText.of("ponderer.ui.ai_config.save"), this::doSave));
        clickableButtons.add(new ClickableButton(guiLeft + WIDTH - MARGIN - 70, btnY, 70, 20,
            UIText.of("ponderer.ui.function_page.back"), this::goBack));

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
            .withBackground(new Color(0xdd_000000, true))
            .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(WIDTH, wH)
            .render(graphics);

        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, this.title, guiLeft + WIDTH / 2, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WIDTH - 5, guiTop + 21, 0x60_FFFFFF);

        int y = guiTop + 30;
        int lx = guiLeft + MARGIN;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.provider"), lx, y + 5, 0xCCCCCC);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.base_url"), lx, y + 5, 0xCCCCCC);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.api_key"), lx, y + 5, 0xCCCCCC);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.model"), lx, y + 5, 0xCCCCCC);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.proxy"), lx, y + 5, 0xCCCCCC);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.max_tokens"), lx, y + 5, 0xCCCCCC);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.trust_ssl"), lx, y + 5, 0xCCCCCC);
        y += ROW_H;
        graphics.drawString(font, UIText.of("ponderer.ui.ai_config.web_use_proxy"), lx, y + 5, 0xCCCCCC);

        // Buttons
        for (ClickableButton btn : clickableButtons) {
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
                btn.y + (btn.h - font.lineHeight) / 2 + 1, hovered ? 0xFFFFFF : 0xCCCCCC);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickableButton btn : clickableButtons) {
                if (mouseX >= btn.x && mouseX < btn.x + btn.w
                    && mouseY >= btn.y && mouseY < btn.y + btn.h) {
                    btn.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
