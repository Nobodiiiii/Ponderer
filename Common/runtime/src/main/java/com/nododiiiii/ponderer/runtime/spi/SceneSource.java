package com.nododiiiii.ponderer.runtime.spi;

import java.util.List;
import com.nododiiiii.ponderer.scene.SceneSnapshot;

@FunctionalInterface
public interface SceneSource {
    List<SceneSnapshot> scenes();
}
