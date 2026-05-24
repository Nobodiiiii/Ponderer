package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.JavaModuleExportRequest;
import com.nododiiiii.ponderer.ponder.JavaModuleExportResult;
import com.nododiiiii.ponderer.ponder.JavaModuleExportService;
import com.nododiiiii.ponderer.ponder.JavaModuleScanResult;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

public class JavaModuleExportScreen extends AbstractStatefulDeclarativeFormScreen {

    private String draftTargetRoot = "";
    private Set<String> selectedSceneKeys = new HashSet<>();

    public JavaModuleExportScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.function_page.export_java.title",
            UILayoutConstants.EDITOR_LIST_W);
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            FieldBindings.transientString(() -> draftTargetRoot, value -> {
                draftTargetRoot = value;
                clearStatusMessages();
            }),
            "ponderer.ui.export_java.target_root",
            "ponderer.ui.export_java.target_root.tooltip",
            "ponderer.ui.export_java.target_root.hint",
            130,
            FieldDecorators.textAction(
                20,
                this::openFolderPicker,
                () -> "...",
                () -> 0xFFFFFF,
                UIText.of("ponderer.ui.export_java.browse.tooltip"))));
        entries.add(FieldSpecs.labeledButton(
            "ponderer.ui.export.scene",
            null,
            this::openSceneSelector,
            this::currentSceneSelectionButtonLabel,
            this::currentSceneSelectionTooltip));
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();

        String targetRoot = draftTargetRoot == null ? "" : draftTargetRoot.trim();
        if (targetRoot.isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.export_java.target_root.empty"));
            return false;
        }

        Path targetPath;
        try {
            targetPath = Path.of(targetRoot);
        } catch (Exception e) {
            setErrorMessage(UIText.of("ponderer.ui.export_java.failed") + ": " + e.getMessage());
            return false;
        }

        JavaModuleExportRequest request = new JavaModuleExportRequest(targetPath, selectedSceneKeys);
        JavaModuleScanResult scanResult = JavaModuleExportService.scan(request);
        if (scanResult.hasFatalFindings()) {
            setErrorMessage(scanResult.fatalFindings.get(0).message);
            return false;
        }

        if (scanResult.hasSkippableFindings()) {
            openSkippableWarningDialog(request, scanResult);
            return false;
        }

        return runExport(request, scanResult);
    }

    @Override
    protected boolean isSaveButtonActive() {
        return true;
    }

    @Override
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("target_root", draftTargetRoot);
        TreeSet<String> ordered = new TreeSet<>(selectedSceneKeys);
        snapshot.put("scene_count", String.valueOf(ordered.size()));
        int index = 0;
        for (String sceneKey : ordered) {
            snapshot.put("scene_" + index, sceneKey);
            index++;
        }
        return snapshot;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        draftTargetRoot = snapshot.getOrDefault("target_root", "");
        selectedSceneKeys = new HashSet<>();
        int count;
        try {
            count = Integer.parseInt(snapshot.getOrDefault("scene_count", "0"));
        } catch (NumberFormatException ignored) {
            count = 0;
        }
        for (int i = 0; i < count; i++) {
            String sceneKey = snapshot.get("scene_" + i);
            if (sceneKey != null && !sceneKey.isBlank()) {
                selectedSceneKeys.add(sceneKey);
            }
        }
    }

    private void openSceneSelector() {
        Minecraft.getInstance().setScreen(new PonderItemGridScreen(
            selectedIds -> {
                selectedSceneKeys = new HashSet<>(selectedIds);
                Minecraft.getInstance().setScreen(this);
            },
            () -> Minecraft.getInstance().setScreen(this),
            true));
    }

    private void openFolderPicker() {
        Path defaultPath = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath();
        if (draftTargetRoot != null && !draftTargetRoot.isBlank()) {
            try {
                defaultPath = Path.of(draftTargetRoot).toAbsolutePath().normalize();
            } catch (Exception ignored) {
            }
        }
        Path pickerDefaultPath = defaultPath;
        CompletableFuture.supplyAsync(() -> TinyFileDialogs.tinyfd_selectFolderDialog(
            UIText.of("ponderer.ui.export_java.browse"),
            Files.exists(pickerDefaultPath) ? pickerDefaultPath.toString() : null))
            .thenAcceptAsync(result -> {
                if (result == null || result.isBlank()) {
                    return;
                }
                draftTargetRoot = Path.of(result).toAbsolutePath().normalize().toString();
                clearStatusMessages();
                rebuildFormPreservingState();
            }, Minecraft.getInstance());
    }

    private void openSkippableWarningDialog(JavaModuleExportRequest request, JavaModuleScanResult scanResult) {
        List<Component> body = new ArrayList<>();
        body.add(Component.literal(UIText.of(
            "ponderer.ui.export_java.scan.summary",
            scanResult.sceneUnsupportedFindings.size(),
            scanResult.warningFindings.size())));

        appendFindingPreview(body, scanResult.sceneUnsupportedFindings, 3);
        appendFindingPreview(body, scanResult.warningFindings, 4);
        body.add(Component.literal(UIText.of("ponderer.ui.export_java.scan.continue")));

        new PondererDialogScreen(
            this,
            List.of(Component.literal(UIText.of("ponderer.ui.export_java.scan.title"))),
            body,
            List.of(
                PondererDialogScreen.button(Component.translatable("ponderer.ui.confirm"), dialog -> {
                    dialog.closeToSource();
                    runExport(request, scanResult);
                }),
                PondererDialogScreen.closeButton(Component.translatable("ponderer.ui.cancel"))))
            .open();
    }

    private void appendFindingPreview(List<Component> lines, List<JavaModuleScanResult.Finding> findings, int limit) {
        int count = 0;
        for (JavaModuleScanResult.Finding finding : findings) {
            if (count >= limit) {
                break;
            }
            StringBuilder builder = new StringBuilder(" - ");
            if (finding.sceneId != null && !finding.sceneId.isBlank()) {
                builder.append(finding.sceneId);
                if (finding.stepType != null && !finding.stepType.isBlank()) {
                    builder.append(" / ").append(finding.stepType);
                }
                builder.append(": ");
            }
            builder.append(finding.message);
            lines.add(Component.literal(builder.toString()));
            count++;
        }
    }

    private boolean runExport(JavaModuleExportRequest request, JavaModuleScanResult scanResult) {
        JavaModuleExportResult exportResult = JavaModuleExportService.export(request, scanResult);
        if (!exportResult.success) {
            setErrorMessage(exportResult.errorMessage == null
                ? UIText.of("ponderer.ui.export_java.failed")
                : exportResult.errorMessage);
            return false;
        }

        markStateSaved();
        if (exportResult.supportOnly) {
            setInfoMessage(UIText.of(
                "ponderer.ui.export_java.success.support_only",
                exportResult.supportFilePath == null ? "" : exportResult.supportFilePath.toString()));
            return true;
        }
        setInfoMessage(UIText.of(
            "ponderer.ui.export_java.success",
            exportResult.addedCount,
            exportResult.replacedCount,
            exportResult.partialCount,
            exportResult.blankCount,
            exportResult.skippedCount,
            exportResult.reportPath == null ? "" : exportResult.reportPath.toString()));
        return true;
    }

    private String currentSceneSelectionButtonLabel() {
        return selectedSceneKeys.isEmpty()
            ? UIText.of("ponderer.ui.export_java.support_only.label")
            : UIText.of("ponderer.ui.export.selected_scenes", selectedSceneKeys.size());
    }

    private String currentSceneSelectionTooltip() {
        return selectedSceneKeys.isEmpty()
            ? UIText.of("ponderer.ui.export_java.support_only.tooltip")
            : UIText.of("ponderer.ui.export.selected_scenes", selectedSceneKeys.size());
    }
}
