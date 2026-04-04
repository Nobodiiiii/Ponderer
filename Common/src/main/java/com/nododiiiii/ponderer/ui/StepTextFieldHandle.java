package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;

import javax.annotation.Nullable;
import java.util.Map;

public class StepTextFieldHandle {

    private final String snapshotKey;
    private String value = "";
    @Nullable
    private HintableTextFieldWidget widget;

    public StepTextFieldHandle(String snapshotKey) {
        this.snapshotKey = snapshotKey;
    }

    public void attach(HintableTextFieldWidget widget) {
        this.widget = widget;
        widget.setValue(value);
    }

    public void setValue(@Nullable String value) {
        this.value = value != null ? value : "";
        if (widget != null) {
            widget.setValue(this.value);
        }
    }

    public String getValue() {
        if (widget != null) {
            value = widget.getValue();
        }
        return value;
    }

    @Nullable
    public HintableTextFieldWidget widget() {
        return widget;
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
