package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.List;

/**
 * Editor for "play_sound" step.
 * Fields: sound (ResourceLocation), soundVolume (float), pitch (float), source (SoundSource cycle).
 */
public class PlaySoundScreen extends AbstractStepEditorScreen {

    private static final String[] SOURCES = {
        "master", "music", "record", "weather", "block",
        "hostile", "neutral", "player", "ambient", "voice"
    };

    private final StepTextFieldHandle soundField = new StepTextFieldHandle("sound");
    private final StepTextFieldHandle volumeField = new StepTextFieldHandle("volume");
    private final StepTextFieldHandle pitchField = new StepTextFieldHandle("pitch");
    private int sourceIndex = 0;

    public PlaySoundScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.play_sound"), scene, sceneIndex, parent);
    }

    public PlaySoundScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                           int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.play_sound"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.play_sound"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            soundField,
            "ponderer.ui.play_sound.sound",
            "ponderer.ui.play_sound.sound.tooltip",
            UIText.of("ponderer.ui.play_sound.sound.hint"),
            140));
        entries.add(FieldSpecs.number(
            volumeField,
            "ponderer.ui.play_sound.volume",
            "ponderer.ui.play_sound.volume.tooltip",
            "1.0",
            50,
            null));
        entries.add(FieldSpecs.number(
            pitchField,
            "ponderer.ui.play_sound.pitch",
            "ponderer.ui.play_sound.pitch.tooltip",
            "1.0",
            50,
            null));
        entries.add(FieldSpecs.choice(
            "ponderer.ui.play_sound.source",
            "ponderer.ui.play_sound.source.tooltip",
            100,
            () -> sourceIndex = (sourceIndex + 1) % SOURCES.length,
            () -> sourceLabel(SOURCES[sourceIndex])));
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
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("sourceIndex", String.valueOf(sourceIndex));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
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
