package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.network.PermissionListRequestPayload;
import com.nododiiiii.ponderer.network.PermissionListResponsePayload;
import com.nododiiiii.ponderer.network.PermissionUpdateRequestPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.ui.catnip.ActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.ui.catnip.FormTextButtonSpec;
import com.nododiiiii.ponderer.ui.catnip.LabeledActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PermissionManagementScreen extends AbstractStatefulDeclarativeFormScreen {

    private static final int SCREEN_WIDTH = UILayoutConstants.EDITOR_LIST_W;

    private final List<PermissionListResponsePayload.Entry> entries = new ArrayList<>();
    private String pendingSubject = "";
    private UploadPermissions.Role selectedRole = UploadPermissions.Role.UPLOAD;
    private String viewerRole = "";
    private boolean serverOperator;
    private boolean canManage;
    private boolean requestSent;
    private boolean waitingForServer = true;

    public PermissionManagementScreen(@Nullable Screen parent) {
        super(parent, "ponderer.ui.scope.server", "ponderer.ui.function_page.permissions.title", SCREEN_WIDTH);
    }

    @Override
    protected void init() {
        super.init();
        hideActionButton(saveChanges);
        hideActionButton(discardChanges);
        if (!requestSent) {
            requestRefresh();
        }
    }

    @Override
    protected boolean shouldAutoCaptureBaselineOnInit() {
        return false;
    }

    public void receiveSnapshot(PermissionListResponsePayload payload) {
        entries.clear();
        entries.addAll(payload.entries());
        entries.sort(Comparator
            .comparingInt((PermissionListResponsePayload.Entry entry) -> -roleLevel(entry.role()))
            .thenComparing(entry -> !entry.locked())
            .thenComparing(entry -> entry.subject().toLowerCase(Locale.ROOT)));
        viewerRole = payload.viewerRole() == null ? "" : payload.viewerRole();
        serverOperator = payload.serverOperator();
        canManage = payload.canManage();
        waitingForServer = false;
        PermissionListResponsePayload.Entry currentEntry = findEntry(pendingSubject);
        if (currentEntry != null) {
            selectedRole = roleFromId(currentEntry.role());
        }

        if (payload.messageKey() != null && !payload.messageKey().isBlank()) {
            String message = payload.messageSubject() == null || payload.messageSubject().isBlank()
                ? UIText.of(payload.messageKey())
                : UIText.of(payload.messageKey(), payload.messageSubject());
            if (payload.error()) {
                setErrorMessage(message);
            } else {
                setInfoMessage(message);
            }
        } else {
            setInfoMessage(UIText.of("ponderer.ui.function_page.permissions.loaded", entries.size()));
        }

        rebuildEntries(currentListScroll());
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> formEntries) {
        formEntries.add(screen -> {
            PlainTextListEntry subjectEntry = screen.createTextEntry(
                "ponderer.ui.function_page.permissions.player",
                "ponderer.ui.function_page.permissions.player.tooltip",
                "ponderer.ui.function_page.permissions.player.hint",
                pendingSubject,
                this::handleSubjectChanged,
                FormTextButtonSpec.action(
                    34,
                    this::fillSelf,
                    () -> UIText.of("ponderer.ui.function_page.permissions.use_self.short"),
                    () -> 0x80FFFF,
                    UIText.of("ponderer.ui.function_page.permissions.use_self.tooltip")));
            subjectEntry.field().setMaxLength(64);
        });

        formEntries.add(screen -> screen.appendBuiltEntry(new LabeledActionStripListEntry(
            "ponderer.ui.function_page.permissions.role",
            "ponderer.ui.function_page.permissions.role.tooltip",
            List.of(
                ActionStripListEntry.button(
                    () -> UIText.of(roleLabelKey(selectedRole)),
                    tooltip("ponderer.ui.function_page.permissions.role.tooltip"),
                    this::cycleSelectedRole,
                    () -> roleColor(selectedRole),
                    this::canCyclePendingRole),
                ActionStripListEntry.iconButton(
                    PonderGuiTextures.ICON_CONFIG_SAVE,
                    () -> sendSet(pendingSubject, selectedRole),
                    tooltip("ponderer.ui.function_page.permissions.set.tooltip"),
                    this::canEditPendingSubject),
                ActionStripListEntry.iconButton(
                    PonderGuiTextures.ICON_CONFIG_RESET,
                    this::requestRefresh,
                    tooltip("ponderer.ui.function_page.permissions.refresh.tooltip"),
                    () -> true)))));

        formEntries.add(screen -> screen.createSectionHeaderEntry(this::viewerText));

        if (waitingForServer && entries.isEmpty()) {
            formEntries.add(screen -> screen.createSectionHeaderEntry(
                UIText.of("ponderer.ui.function_page.permissions.loading")));
            return;
        }

        if (entries.isEmpty()) {
            formEntries.add(screen -> screen.createSectionHeaderEntry(
                UIText.of("ponderer.ui.function_page.permissions.empty")));
            return;
        }

        addRoleSection(formEntries, UploadPermissions.Role.ADMIN);
        addRoleSection(formEntries, UploadPermissions.Role.UPLOAD);
        addRoleSection(formEntries, UploadPermissions.Role.PULL);
    }

    private void addRoleSection(List<DeclarativeFormEntry> formEntries, UploadPermissions.Role role) {
        List<PermissionListResponsePayload.Entry> matching = entries.stream()
            .filter(entry -> role.id().equals(entry.role()))
            .toList();
        if (matching.isEmpty()) {
            return;
        }

        formEntries.add(screen -> screen.createSectionHeaderEntry(UIText.of(roleSectionKey(role), matching.size())));
        for (PermissionListResponsePayload.Entry entry : matching) {
            formEntries.add(screen -> {
                UploadPermissions.Role entryRole = roleFromId(entry.role());
                PlainTextListEntry row = screen.createTextEntry(
                    roleLabelKey(entryRole),
                    entry.locked()
                        ? "ponderer.ui.function_page.permissions.operator_locked.tooltip"
                        : "ponderer.ui.function_page.permissions.row.role.tooltip",
                    null,
                    entry.subject(),
                    ignored -> {
                    });
                row.field().setEditable(false);
                if (entry.locked()) {
                    row.setTrailingText(() -> UIText.of("ponderer.ui.function_page.permissions.source.operator"));
                    return;
                }
                if (!canManage) {
                    return;
                }

                UploadPermissions.Role nextRole = nextRole(entryRole);
                row.addTrailingButton(
                    58,
                    () -> sendSet(entry.subject(), nextRole),
                    () -> UIText.of(roleLabelKey(nextRole)),
                    () -> roleColor(nextRole),
                    UIText.of("ponderer.ui.function_page.permissions.row.role.tooltip"));
                row.addTrailingButton(
                    38,
                    () -> sendRemove(entry.subject()),
                    () -> UIText.of("ponderer.ui.function_page.permissions.remove"),
                    () -> 0xFF9090,
                    UIText.of("ponderer.ui.function_page.permissions.row.remove.tooltip"));
            });
        }
    }

    private void requestRefresh() {
        requestSent = true;
        waitingForServer = true;
        setInfoMessage(UIText.of("ponderer.ui.function_page.permissions.loading"));
        PondererServices.NETWORK.sendToServer(new PermissionListRequestPayload());
        if (list != null) {
            rebuildEntries(currentListScroll());
        }
    }

    private void sendSet(String rawSubject, UploadPermissions.Role role) {
        String subject = rawSubject == null ? "" : rawSubject.trim();
        if (subject.isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.error.required_field",
                UIText.of("ponderer.ui.function_page.permissions.player")));
            return;
        }
        if (!canManage) {
            setErrorMessage(UIText.of("ponderer.ui.function_page.permissions.denied", subject));
            return;
        }
        if (isLockedSubject(subject)) {
            setErrorMessage(UIText.of("ponderer.ui.function_page.permissions.operator_locked", subject));
            return;
        }

        waitingForServer = true;
        setInfoMessage(UIText.of("ponderer.ui.function_page.permissions.saving"));
        PondererServices.NETWORK.sendToServer(new PermissionUpdateRequestPayload("set", subject, role.id()));
        rebuildEntries(currentListScroll());
    }

    private void sendRemove(String subject) {
        if (!canManage) {
            setErrorMessage(UIText.of("ponderer.ui.function_page.permissions.denied", subject));
            return;
        }

        waitingForServer = true;
        setInfoMessage(UIText.of("ponderer.ui.function_page.permissions.saving"));
        PondererServices.NETWORK.sendToServer(new PermissionUpdateRequestPayload("remove", subject, ""));
        rebuildEntries(currentListScroll());
    }

    private void cycleSelectedRole() {
        if (!canCyclePendingRole()) {
            return;
        }
        selectedRole = nextRole(selectedRole);
        clearStatusMessages();
    }

    private void fillSelf() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            handleSubjectChanged(player.getGameProfile().getName());
            clearStatusMessages();
            rebuildEntries(currentListScroll());
        }
    }

    private void handleSubjectChanged(String value) {
        pendingSubject = value == null ? "" : value;
        PermissionListResponsePayload.Entry entry = findEntry(pendingSubject);
        if (entry != null) {
            selectedRole = roleFromId(entry.role());
        }
    }

    private String viewerText() {
        return UIText.of(
            canManage
                ? "ponderer.ui.function_page.permissions.viewer.manage"
                : "ponderer.ui.function_page.permissions.viewer.readonly",
            viewerRoleLabel());
    }

    private boolean canCyclePendingRole() {
        return canManage && !isLockedSubject(pendingSubject);
    }

    private boolean canEditPendingSubject() {
        String subject = pendingSubject == null ? "" : pendingSubject.trim();
        return canManage && !subject.isEmpty() && !isLockedSubject(subject);
    }

    @Nullable
    private PermissionListResponsePayload.Entry findEntry(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        String key = subject.trim().toLowerCase(Locale.ROOT);
        for (PermissionListResponsePayload.Entry entry : entries) {
            if (entry.subject().toLowerCase(Locale.ROOT).equals(key)) {
                return entry;
            }
        }
        return null;
    }

    private boolean isLockedSubject(String subject) {
        PermissionListResponsePayload.Entry entry = findEntry(subject);
        return entry != null && entry.locked();
    }

    private String viewerRoleLabel() {
        if (serverOperator) {
            return UIText.of("ponderer.ui.function_page.permissions.role.operator");
        }
        UploadPermissions.Role role = UploadPermissions.Role.fromId(viewerRole);
        if (role == null) {
            return UIText.of("ponderer.ui.function_page.permissions.role.none");
        }
        return UIText.of(roleLabelKey(role));
    }

    @Override
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("subject", pendingSubject);
        snapshot.put("role", selectedRole.id());
        return snapshot;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        pendingSubject = snapshot.getOrDefault("subject", pendingSubject);
        UploadPermissions.Role role = UploadPermissions.Role.fromId(snapshot.get("role"));
        if (role != null) {
            selectedRole = role;
        }
    }

    @Override
    protected boolean saveEdits() {
        return false;
    }

    private static void hideActionButton(@Nullable BoxWidget button) {
        if (button == null) {
            return;
        }
        button.visible = false;
        button.active = false;
    }

    private static java.util.function.Supplier<List<Component>> tooltip(String key) {
        return () -> List.of(Component.literal(UIText.of(key)));
    }

    private static UploadPermissions.Role nextRole(UploadPermissions.Role role) {
        return switch (role) {
            case PULL -> UploadPermissions.Role.UPLOAD;
            case UPLOAD -> UploadPermissions.Role.ADMIN;
            case ADMIN -> UploadPermissions.Role.PULL;
        };
    }

    private static UploadPermissions.Role roleFromId(String roleId) {
        UploadPermissions.Role role = UploadPermissions.Role.fromId(roleId);
        return role == null ? UploadPermissions.Role.PULL : role;
    }

    private static int roleLevel(String roleId) {
        return switch (roleFromId(roleId)) {
            case ADMIN -> 3;
            case UPLOAD -> 2;
            case PULL -> 1;
        };
    }

    private static String roleLabelKey(UploadPermissions.Role role) {
        return switch (role) {
            case ADMIN -> "ponderer.ui.function_page.permissions.role.admin";
            case UPLOAD -> "ponderer.ui.function_page.permissions.role.upload";
            case PULL -> "ponderer.ui.function_page.permissions.role.pull";
        };
    }

    private static String roleSectionKey(UploadPermissions.Role role) {
        return switch (role) {
            case ADMIN -> "ponderer.ui.function_page.permissions.section.admin";
            case UPLOAD -> "ponderer.ui.function_page.permissions.section.upload";
            case PULL -> "ponderer.ui.function_page.permissions.section.pull";
        };
    }

    private static int roleColor(UploadPermissions.Role role) {
        return switch (role) {
            case ADMIN -> 0xFFE08A;
            case UPLOAD -> 0x90E890;
            case PULL -> 0x88C8FF;
        };
    }
}
