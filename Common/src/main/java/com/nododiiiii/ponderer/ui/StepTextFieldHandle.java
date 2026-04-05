package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;

import javax.annotation.Nullable;
import java.util.Map;

public class StepTextFieldHandle implements HintableTextWidgetBinding {

    private final String snapshotKey;
    private String value = "";
    @Nullable
    private HintableTextFieldWidget widget;

    public StepTextFieldHandle(String snapshotKey) {
        this.snapshotKey = snapshotKey;
    }

    @Override
    public void attach(HintableTextFieldWidget widget) {
        this.widget = widget;
        if (!value.equals(widget.getValue())) {
            widget.setValue(value);
        }
    }

    public void setValue(@Nullable String value) {
        this.value = value != null ? value : "";
        if (widget != null && !this.value.equals(widget.getValue())) {
            widget.setValue(this.value);
        }
    }

    @Override
    public void set(@Nullable String value) {
        setValue(value);
    }

    public String getValue() {
        if (widget != null) {
            value = widget.getValue();
        }
        return value;
    }

    @Nullable
    @Override
    public HintableTextFieldWidget widget() {
        return widget;
    }

    @Override
    public String get() {
        return getValue();
    }

    @Override
    public String snapshotKey() {
        return snapshotKey;
    }

    @Override
    public String deserialize(String raw) {
        return raw;
    }

    public void snapshot(Map<String, String> snapshot) {
        snapshot.put(snapshotKey, getValue());
    }

    public void restore(Map<String, String> snapshot) {
        if (snapshot.containsKey(snapshotKey)) {
            setValue(snapshot.get(snapshotKey));
        }
    }
}
