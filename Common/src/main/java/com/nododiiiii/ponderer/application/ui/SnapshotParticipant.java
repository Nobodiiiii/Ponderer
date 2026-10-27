package com.nododiiiii.ponderer.application.ui;

import java.util.Map;

public interface SnapshotParticipant {

    default void snapshot(Map<String, String> snapshot) {
    }

    default void restore(Map<String, String> snapshot) {
    }
}
