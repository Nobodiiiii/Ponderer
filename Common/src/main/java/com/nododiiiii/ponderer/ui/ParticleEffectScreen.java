package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.ponder.ParticleEffectSpec;
import com.nododiiiii.ponderer.ponder.ParticleEffects;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class ParticleEffectScreen extends AbstractStepEditorScreen {
    private static final String PREFIX = "ponderer.ui.spawn_particles";
    private final StepTextFieldHandle particle = new StepTextFieldHandle("particle");
    private final StepXyzFieldHandle pos = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle spread = new StepXyzFieldHandle("spread");
    private final StepXyzFieldHandle motion = new StepXyzFieldHandle("motion");
    private final StepTextFieldHandle count = new StepTextFieldHandle("count");
    private final StepTextFieldHandle duration = new StepTextFieldHandle("duration");
    private int distributionIndex;

    public ParticleEffectScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        this(scene, sceneIndex, parent, -1, null);
    }

    public ParticleEffectScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                int editIndex, @Nullable DslScene.DslStep step) {
        super(Component.translatable(PREFIX), scene, sceneIndex, parent, editIndex, step);
        applyPreset(0);
        pos.setValue(0.5, 0.5, 0.5);
    }

    @Override protected String getHeaderTitle() { return UIText.of(PREFIX); }
    @Override protected String getStepType() { return "spawn_particles"; }

    @Override
    protected void collectStepEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.choice(PREFIX + ".preset", PREFIX + ".preset.tooltip", 130,
            () -> applyPreset((presetIndex() + 1) % (ParticleEffectSpec.PRESETS.size() + 1)),
            () -> UIText.of(PREFIX + ".preset." + (presetIndex() == ParticleEffectSpec.PRESETS.size()
                ? "custom" : ParticleEffectSpec.PRESETS.get(presetIndex()).key()))));
        entries.add(FieldSpecs.text(particle, PREFIX + ".particle", PREFIX + ".particle.tooltip", "minecraft:flame", 160));
        entries.add(FieldSpecs.xyz(pos, PREFIX + ".pos", PREFIX + ".pos.tooltip", PickState.TargetField.POS1, true));
        entries.add(FieldSpecs.cycle(
            FieldBindings.integer("distribution", () -> distributionIndex, value -> distributionIndex =
                Math.floorMod(value, ParticleEffectSpec.DISTRIBUTIONS.size())),
            PREFIX + ".distribution", PREFIX + ".distribution.tooltip", 130,
            ParticleEffectSpec.DISTRIBUTIONS.size(), () -> {},
            () -> UIText.of(PREFIX + ".distribution." + ParticleEffectSpec.DISTRIBUTIONS.get(distributionIndex)),
            () -> 0xFFFFFF));
        entries.add(FieldSpecs.xyz(spread, PREFIX + ".spread", PREFIX + ".spread.tooltip", FieldType.VECTOR));
        entries.add(FieldSpecs.number(count, PREFIX + ".count", PREFIX + ".count.tooltip", "10", 65, null));
        entries.add(FieldSpecs.ticksNumber(duration, PREFIX + ".duration", PREFIX + ".duration.tooltip", "2", 65));
        entries.add(FieldSpecs.xyz(motion, PREFIX + ".motion", PREFIX + ".motion.tooltip", FieldType.VECTOR));
    }

    private int presetIndex() {
        for (int i = 0; i < ParticleEffectSpec.PRESETS.size(); i++) {
            if (ParticleEffectSpec.PRESETS.get(i).particle().equals(particle.getValue().trim())) return i;
        }
        return ParticleEffectSpec.PRESETS.size();
    }

    private void applyPreset(int index) {
        if (index == ParticleEffectSpec.PRESETS.size()) {
            // Keep all editable values when switching to custom input.
            particle.setValue("");
            return;
        }
        DslScene.DslStep preset = ParticleEffectSpec.PRESETS.get(index).step();
        populateEffect(preset);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        populateEffect(ParticleEffectSpec.normalize(step));
    }

    private void populateEffect(DslScene.DslStep step) {
        particle.setValue(step.particle);
        setVector(pos, step.pos);
        setVector(spread, ParticleEffectSpec.spread(step));
        setVector(motion, ParticleEffectSpec.motion(step));
        count.setValue(String.valueOf(ParticleEffectSpec.count(step)));
        duration.setValue(String.valueOf(ParticleEffectSpec.duration(step)));
        distributionIndex = Math.max(0, ParticleEffectSpec.DISTRIBUTIONS.indexOf(ParticleEffectSpec.distribution(step)));
    }

    private static void setVector(StepXyzFieldHandle field, @Nullable List<Double> value) {
        if (value != null && value.size() == 3) field.setValue(value.get(0), value.get(1), value.get(2));
    }

    @Nullable
    private List<Double> readVector(StepXyzFieldHandle field, String label) {
        Double x = parseDouble(field.x(), UIText.of(PREFIX + "." + label) + " X");
        Double y = parseDouble(field.y(), UIText.of(PREFIX + "." + label) + " Y");
        Double z = parseDouble(field.z(), UIText.of(PREFIX + "." + label) + " Z");
        return x == null || y == null || z == null ? null : List.of(x, y, z);
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();
        DslScene.DslStep step = new DslScene.DslStep();
        step.type = getStepType();
        step.particle = particle.getValue().trim();
        step.pos = readVector(pos, "pos");
        if (step.pos == null) return null;
        step.spread = readVector(spread, "spread");
        if (step.spread == null) return null;
        step.motion = readVector(motion, "motion");
        if (step.motion == null) return null;
        step.count = parseInt(count.getValue(), UIText.of(PREFIX + ".count"));
        if (step.count == null) return null;
        step.duration = parseInt(duration.getValue(), UIText.of(PREFIX + ".duration"));
        if (step.duration == null) return null;
        step.distribution = ParticleEffectSpec.DISTRIBUTIONS.get(distributionIndex);
        try {
            ParticleEffectSpec.validate(step);
            if (minecraft == null || minecraft.level == null) {
                setErrorMessage(UIText.of(PREFIX + ".error.world"));
                return null;
            }
            ParticleEffects.parse(step.particle, minecraft.level.registryAccess());
        } catch (Exception e) {
            setErrorMessage(UIText.of(PREFIX + ".error.invalid", e.getMessage()));
            return null;
        }
        return step;
    }
}
