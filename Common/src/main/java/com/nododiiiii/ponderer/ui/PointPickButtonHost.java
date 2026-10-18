package com.nododiiiii.ponderer.ui;

public interface PointPickButtonHost {

    void startPointPickFromButton(PickState.TargetField target, boolean halfOffset);

    default FieldType pointPickFieldType(PickState.TargetField target, boolean halfOffset) {
        return switch (target) {
            case UI_POINT -> FieldType.UI_POSITION;
            case POINT, LOOK_AT -> FieldType.POSITION;
            case POS1, POS2 -> halfOffset ? FieldType.POSITION : FieldType.BLOCK_POSITION;
        };
    }
}
