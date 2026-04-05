package com.nododiiiii.ponderer.ui;

import java.util.Map;

public class StepXyzFieldHandle implements SnapshotParticipant {

    private final StepTextFieldHandle x;
    private final StepTextFieldHandle y;
    private final StepTextFieldHandle z;

    public StepXyzFieldHandle(String snapshotPrefix) {
        this(snapshotPrefix + "X", snapshotPrefix + "Y", snapshotPrefix + "Z");
    }

    public StepXyzFieldHandle(String xKey, String yKey, String zKey) {
        this.x = new StepTextFieldHandle(xKey);
        this.y = new StepTextFieldHandle(yKey);
        this.z = new StepTextFieldHandle(zKey);
    }

    public StepTextFieldHandle xHandle() {
        return x;
    }

    public StepTextFieldHandle yHandle() {
        return y;
    }

    public StepTextFieldHandle zHandle() {
        return z;
    }

    public void setValue(double x, double y, double z) {
        this.x.setValue(String.valueOf(x));
        this.y.setValue(String.valueOf(y));
        this.z.setValue(String.valueOf(z));
    }

    public void setValue(int x, int y, int z) {
        this.x.setValue(String.valueOf(x));
        this.y.setValue(String.valueOf(y));
        this.z.setValue(String.valueOf(z));
    }

    public String x() {
        return x.getValue();
    }

    public String y() {
        return y.getValue();
    }

    public String z() {
        return z.getValue();
    }

    public void snapshot(Map<String, String> snapshot) {
        x.snapshot(snapshot);
        y.snapshot(snapshot);
        z.snapshot(snapshot);
    }

    public void restore(Map<String, String> snapshot) {
        x.restore(snapshot);
        y.restore(snapshot);
        z.restore(snapshot);
    }
}
