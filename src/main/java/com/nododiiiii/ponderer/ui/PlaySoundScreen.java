package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Editor for "play_sound" step.
 * Fields: sound (ResourceLocation), soundVolume (float), pitch (float), source (SoundSource cycle).
 */
public class PlaySoundScreen extends AbstractStepEditorScreen {

    private static final String[] SOURCES = {
        "master", "music", "record", "weather", "block",
        "hostile", "neutral", "player", "ambient", "voice"
    };

    private HintableTextFieldWidget soundField;
    private HintableTextFieldWidget volumeField;
    private HintableTextFieldWidget pitchField;
    private int sourceIndex = 0;
    private BoxWidget sourceButton;

    public PlaySoundScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.play_sound"), scene, sceneIndex, parent);
    }

    public PlaySoundScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                           int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.play_sound"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 4; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.play_sound"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26;
        int lx = guiLeft + 10;
        soundField = createTextField(x, y, 140, 18, UIText.of("ponderer.ui.play_sound.sound.hint"));
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.play_sound.sound"), UIText.of("ponderer.ui.play_sound.sound.tooltip"));
        y += 22;
        volumeField = createSmallNumberField(x, y, 50, "1.0");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.play_sound.volume"), UIText.of("ponderer.ui.play_sound.volume.tooltip"));
        y += 22;
        pitchField = createSmallNumberField(x, y, 50, "1.0");
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui.play_sound.pitch"), UIText.of("ponderer.ui.play_sound.pitch.tooltip"));
        y += 22;
        sourceButton = createFormButton(x, y, 100);
        sourceButton.withCallback(() -> sourceIndex = (sourceIndex + 1) % SOURCES.length);
        addRenderableWidget(sourceButton);
        addLabelTooltip(lx, y + 1, UIText.of("ponderer.ui.play_sound.source"), UIText.of("ponderer.ui.play_sound.source.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.sound != null) soundField.setValue(step.sound);
        if (step.soundVolume != null) volumeField.setValue(String.valueOf(step.soundVolume));
        if (step.pitch != null) pitchField.setValue(String.valueOf(step.pitch));
        if (step.source != null) {
            for (int i = 0; i < SOURCES.length; i++) {
                if (SOURCES[i].equalsIgnoreCase(step.source)) { sourceIndex = i; break; }
            }
        }
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC;

        graphics.drawString(font, UIText.of("ponderer.ui.play_sound.sound"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.play_sound.volume"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.play_sound.pitch"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui.play_sound.source"), lx, y + 1, lc);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, sourceLabel(SOURCES[sourceIndex]),
            sourceButton.getX() + 50, sourceButton.getY() + 2, 0xFFFFFF);
    }

    private String sourceLabel(String value) {
        String key = "ponderer.ui.play_sound.source." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String sound = soundField.getValue().trim();
        if (sound.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.play_sound.error.required");
            return null;
        }
        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "play_sound";
        s.sound = sound;
        float vol = (float) parseDoubleOr(volumeField.getValue(), 1.0);
        if (vol != 1.0f) s.soundVolume = vol;
        float p = (float) parseDoubleOr(pitchField.getValue(), 1.0);
        if (p != 1.0f) s.pitch = p;
        if (sourceIndex > 0) s.source = SOURCES[sourceIndex];
        return s;
    }
}
