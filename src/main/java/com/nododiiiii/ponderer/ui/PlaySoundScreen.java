package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

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
    private BoxWidget sourceBtn;

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
        beginForm();
        soundField = addFormTextField("ponderer.ui.play_sound.sound", "ponderer.ui.play_sound.sound.tooltip", UIText.of("ponderer.ui.play_sound.sound.hint"), 140);
        volumeField = addFormNumberField("ponderer.ui.play_sound.volume", "ponderer.ui.play_sound.volume.tooltip", "1.0", 50);
        pitchField = addFormNumberField("ponderer.ui.play_sound.pitch", "ponderer.ui.play_sound.pitch.tooltip", "1.0", 50);
        sourceBtn = addFormCycleButton("ponderer.ui.play_sound.source", "ponderer.ui.play_sound.source.tooltip", 100,
                () -> sourceIndex = (sourceIndex + 1) % SOURCES.length,
                () -> sourceLabel(SOURCES[sourceIndex]));
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

    private String sourceLabel(String value) {
        String key = "ponderer.ui.play_sound.source." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    @Override
    protected String getStepType() { return "play_sound"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("sound", soundField.getValue());
        m.put("volume", volumeField.getValue());
        m.put("pitch", pitchField.getValue());
        m.put("sourceIndex", String.valueOf(sourceIndex));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("sound")) soundField.setValue(snapshot.get("sound"));
        if (snapshot.containsKey("volume")) volumeField.setValue(snapshot.get("volume"));
        if (snapshot.containsKey("pitch")) pitchField.setValue(snapshot.get("pitch"));
        if (snapshot.containsKey("sourceIndex")) {
            try { sourceIndex = Integer.parseInt(snapshot.get("sourceIndex")); } catch (NumberFormatException ignored) {}
        }
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
