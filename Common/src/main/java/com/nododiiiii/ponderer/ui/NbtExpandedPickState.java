package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.nbt.NbtCoordinateDetector;
import com.nododiiiii.ponderer.nbt.NbtPath;
import com.nododiiiii.ponderer.nbt.NbtPrettyPrinter;
import com.nododiiiii.ponderer.nbt.NbtTextCodec;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class NbtExpandedPickState {

    public static final String SNAPSHOT_NOTICE_KEY = "_expanded_nbt_pick_notice";
    public static final String SNAPSHOT_ERROR_KEY = "_expanded_nbt_pick_error";

    private static boolean active = false;
    private static boolean floatingTarget = false;
    private static Map<String, String> formSnapshot = new HashMap<>();
    @Nullable
    private static SnapshotReturnContext context;
    @Nullable
    private static NbtPath targetPath;

    private NbtExpandedPickState() {
    }

    public static void startPick(Map<String, String> snapshot, NbtPath path, SnapshotReturnContext context,
                                 DslScene scene, int sceneIndex, boolean floatingTarget) {
        NbtExpandedPickState.active = true;
        NbtExpandedPickState.floatingTarget = floatingTarget;
        NbtExpandedPickState.formSnapshot = new HashMap<>(snapshot);
        NbtExpandedPickState.context = context;
        NbtExpandedPickState.targetPath = path;

        if (!PonderScreenNavigation.openPonderUIForScene(scene, sceneIndex)) {
            cancelPick();
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isFloatingTarget() {
        return active && floatingTarget;
    }

    public static void completePick(BlockPos pos, @Nullable Direction face) {
        if (!active) {
            return;
        }
        NbtPath path = targetPath;
        if (path == null) {
            reopenEditor();
            return;
        }

        String text = formSnapshot.getOrDefault(NbtExpandedEditorScreen.TEXT_SNAPSHOT_KEY, "");
        NbtTextCodec.ParseResult parsed = NbtTextCodec.parse(text);
        if (!parsed.success()) {
            formSnapshot.put(SNAPSHOT_ERROR_KEY, parsed.errorMessage() == null ? UIText.of("ponderer.ui.nbt_editor.error.invalid") : parsed.errorMessage());
            reopenEditor();
            return;
        }

        CompoundTag tag = parsed.tag();
        NbtCoordinateDetector.Candidate candidate = NbtCoordinateDetector.findCandidate(tag, path);
        if (tag == null || candidate == null || !NbtCoordinateDetector.rewriteCoordinate(tag, candidate, pos, face)) {
            formSnapshot.put(SNAPSHOT_ERROR_KEY, UIText.of("ponderer.ui.nbt_editor.error.pick_target_missing"));
            reopenEditor();
            return;
        }

        NbtPrettyPrinter.FormattedText formatted = NbtPrettyPrinter.format(tag);
        formSnapshot.put(NbtExpandedEditorScreen.TEXT_SNAPSHOT_KEY, formatted.text());
        formSnapshot.put(SNAPSHOT_NOTICE_KEY, UIText.of("ponderer.ui.nbt_editor.pick.filled"));
        reopenEditor();
    }

    public static void cancelPick() {
        if (!active) {
            return;
        }
        reopenEditor();
    }

    public static void reset() {
        active = false;
        floatingTarget = false;
        formSnapshot.clear();
        context = null;
        targetPath = null;
    }

    private static void reopenEditor() {
        SnapshotReturnContext reopenContext = context;
        active = false;
        floatingTarget = false;
        context = null;
        targetPath = null;
        if (reopenContext != null) {
            reopenContext.reopenEditor(formSnapshot);
        } else {
            formSnapshot.clear();
        }
    }
}
