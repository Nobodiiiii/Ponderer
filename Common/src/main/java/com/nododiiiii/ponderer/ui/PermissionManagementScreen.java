package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.network.PermissionListRequestPayload;
import com.nododiiiii.ponderer.network.PermissionListResponsePayload;
import com.nododiiiii.ponderer.network.PermissionUpdateRequestPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.ui.catnip.ActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.CollapsibleSectionHeaderListEntry;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.ui.catnip.FormTextButtonSpec;
import com.nododiiiii.ponderer.ui.catnip.LabeledActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlayerPermissionListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PermissionManagementScreen extends AbstractStatefulDeclarativeFormScreen {

    private static final int SCREEN_WIDTH = UILayoutConstants.EDITOR_LIST_W;

    private final List<PermissionListResponsePayload.Entry> entries = new ArrayList<>();
    private final Map<String, UploadPermissions.Role> pendingRoleEdits = new LinkedHashMap<>();
    private final Map<String, String> pendingRemovals = new LinkedHashMap<>();
    private final Set<UploadPermissions.Role> collapsedRoleSections = EnumSet.noneOf(UploadPermissions.Role.class);
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
            selectedRole = displayRole(currentEntry);
        }
        reconcilePendingChanges();
        restoreBaselineState(new LinkedHashMap<>());

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

        rebuildListPreservingScroll();
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
            .filter(entry -> !isPendingRemoval(entry.subject()))
            .filter(entry -> role.id().equals(roleFromId(entry.role()).id()))
            .toList();
        if (matching.isEmpty()) {
            return;
        }

        formEntries.add(screen -> screen.appendBuiltEntry(new CollapsibleSectionHeaderListEntry(
            () -> UIText.of(roleSectionKey(role), matching.size()),
            () -> isRoleSectionCollapsed(role),
            () -> toggleRoleSection(role),
            () -> UIText.of("ponderer.ui.function_page.permissions.section.expand"),
            () -> UIText.of("ponderer.ui.function_page.permissions.section.collapse"))));
        if (isRoleSectionCollapsed(role)) {
            return;
        }

        for (PermissionListResponsePayload.Entry entry : matching) {
            formEntries.add(screen -> screen.appendBuiltEntry(createRoleEntry(entry)));
        }
    }

    private PlayerPermissionListEntry createRoleEntry(PermissionListResponsePayload.Entry entry) {
        UploadPermissions.Role entryRole = displayRole(entry);
        UploadPermissions.Role nextRole = nextRole(entryRole);
        boolean editable = canManage && !entry.locked();
        boolean dirty = isEntryRoleDirty(entry);
        String roleTooltipKey = entry.locked()
            ? "ponderer.ui.function_page.permissions.operator_locked.tooltip"
            : "ponderer.ui.function_page.permissions.row.role.tooltip";
        return new PlayerPermissionListEntry(
            entry.subject(),
            UIText.of(roleTooltipKey),
            List.of(
                ActionStripListEntry.button(
                    () -> entry.locked()
                        ? UIText.of("ponderer.ui.function_page.permissions.role.operator")
                        : UIText.of(roleLabelKey(entryRole)),
                    tooltip(roleTooltipKey),
                    () -> stageRoleEdit(entry, nextRole),
                    () -> roleColor(entryRole),
                    () -> editable),
                ActionStripListEntry.iconButton(
                    PonderGuiTextures.ICON_CONFIG_SAVE,
                    () -> sendEntryRoleEdit(entry),
                    tooltip("ponderer.ui.function_page.permissions.set.tooltip"),
                    () -> editable && dirty),
                ActionStripListEntry.failIconButton(
                    PonderGuiTextures.ICON_DISABLE,
                    () -> stageRemoval(entry),
                    tooltip("ponderer.ui.function_page.permissions.row.remove.tooltip"),
                    () -> editable)));
    }

    private void requestRefresh() {
        requestSent = true;
        waitingForServer = true;
        setInfoMessage(UIText.of("ponderer.ui.function_page.permissions.loading"));
        PondererServices.NETWORK.sendToServer(new PermissionListRequestPayload());
        if (list != null) {
            rebuildListPreservingScroll();
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
        rebuildListPreservingScroll();
    }

    private void stageRoleEdit(PermissionListResponsePayload.Entry entry, UploadPermissions.Role role) {
        if (!canEditEntry(entry)) {
            return;
        }
        String key = subjectKey(entry.subject());
        pendingRemovals.remove(key);
        if (role == roleFromId(entry.role())) {
            pendingRoleEdits.remove(key);
        } else {
            pendingRoleEdits.put(key, role);
        }
        clearStatusMessages();
        rebuildListPreservingScroll();
    }

    private void stageRemoval(PermissionListResponsePayload.Entry entry) {
        if (!canEditEntry(entry)) {
            return;
        }
        String key = subjectKey(entry.subject());
        pendingRoleEdits.remove(key);
        pendingRemovals.put(key, entry.subject());
        clearStatusMessages();
        rebuildListPreservingScroll();
    }

    private void sendEntryRoleEdit(PermissionListResponsePayload.Entry entry) {
        if (!canEditEntry(entry) || !isEntryRoleDirty(entry)) {
            return;
        }
        sendSet(entry.subject(), displayRole(entry));
    }

    private boolean canEditEntry(PermissionListResponsePayload.Entry entry) {
        if (!canManage) {
            setErrorMessage(UIText.of("ponderer.ui.function_page.permissions.denied", entry.subject()));
            return false;
        }
        if (entry.locked() || isLockedSubject(entry.subject())) {
            setErrorMessage(UIText.of("ponderer.ui.function_page.permissions.operator_locked", entry.subject()));
            return false;
        }
        return true;
    }

    private boolean isRoleSectionCollapsed(UploadPermissions.Role role) {
        return collapsedRoleSections.contains(role);
    }

    private void toggleRoleSection(UploadPermissions.Role role) {
        if (!collapsedRoleSections.remove(role)) {
            collapsedRoleSections.add(role);
        }
        rebuildListPreservingScroll();
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
            rebuildListPreservingScroll();
        }
    }

    private void handleSubjectChanged(String value) {
        pendingSubject = value == null ? "" : value;
        PermissionListResponsePayload.Entry entry = findEntry(pendingSubject);
        if (entry != null) {
            selectedRole = displayRole(entry);
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
        for (Map.Entry<String, UploadPermissions.Role> entry : pendingRoleEdits.entrySet()) {
            snapshot.put("role:" + entry.getKey(), entry.getValue().id());
        }
        for (Map.Entry<String, String> entry : pendingRemovals.entrySet()) {
            snapshot.put("remove:" + entry.getKey(), entry.getValue());
        }
        return snapshot;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        pendingRoleEdits.clear();
        pendingRemovals.clear();
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            if (entry.getKey().startsWith("role:")) {
                UploadPermissions.Role role = UploadPermissions.Role.fromId(entry.getValue());
                if (role != null) {
                    pendingRoleEdits.put(entry.getKey().substring("role:".length()), role);
                }
            } else if (entry.getKey().startsWith("remove:")) {
                pendingRemovals.put(entry.getKey().substring("remove:".length()), entry.getValue());
            }
        }
    }

    @Override
    protected void afterSnapshotRestored(Map<String, String> snapshot) {
        rebuildListPreservingScroll();
    }

    @Override
    protected boolean saveEdits() {
        if (!hasPendingListChanges()) {
            return true;
        }
        if (!canManage) {
            setErrorMessage(UIText.of("ponderer.ui.function_page.permissions.denied", pendingSubject));
            return false;
        }

        for (String subject : pendingRemovals.values()) {
            if (isLockedSubject(subject)) {
                setErrorMessage(UIText.of("ponderer.ui.function_page.permissions.operator_locked", subject));
                return false;
            }
        }
        for (String key : pendingRoleEdits.keySet()) {
            PermissionListResponsePayload.Entry entry = findEntryByKey(key);
            if (entry == null) {
                continue;
            }
            if (entry.locked()) {
                setErrorMessage(UIText.of("ponderer.ui.function_page.permissions.operator_locked", entry.subject()));
                return false;
            }
        }

        waitingForServer = true;
        setInfoMessage(UIText.of("ponderer.ui.function_page.permissions.saving"));
        for (String subject : pendingRemovals.values()) {
            PondererServices.NETWORK.sendToServer(new PermissionUpdateRequestPayload("remove", subject, ""));
        }
        for (Map.Entry<String, UploadPermissions.Role> entry : pendingRoleEdits.entrySet()) {
            PermissionListResponsePayload.Entry source = findEntryByKey(entry.getKey());
            if (source != null) {
                PondererServices.NETWORK.sendToServer(new PermissionUpdateRequestPayload(
                    "set", source.subject(), entry.getValue().id()));
            }
        }
        rebuildListPreservingScroll();
        return true;
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

    private UploadPermissions.Role displayRole(PermissionListResponsePayload.Entry entry) {
        return pendingRoleEdits.getOrDefault(subjectKey(entry.subject()), roleFromId(entry.role()));
    }

    private boolean isEntryRoleDirty(PermissionListResponsePayload.Entry entry) {
        return pendingRoleEdits.containsKey(subjectKey(entry.subject()));
    }

    private boolean isPendingRemoval(String subject) {
        return pendingRemovals.containsKey(subjectKey(subject));
    }

    private boolean hasPendingListChanges() {
        return !pendingRoleEdits.isEmpty() || !pendingRemovals.isEmpty();
    }

    private void reconcilePendingChanges() {
        pendingRoleEdits.entrySet().removeIf(entry -> {
            PermissionListResponsePayload.Entry current = findEntryByKey(entry.getKey());
            return current == null || roleFromId(current.role()) == entry.getValue();
        });
        pendingRemovals.entrySet().removeIf(entry -> findEntryByKey(entry.getKey()) == null);
    }

    @Nullable
    private PermissionListResponsePayload.Entry findEntryByKey(String key) {
        for (PermissionListResponsePayload.Entry entry : entries) {
            if (subjectKey(entry.subject()).equals(key)) {
                return entry;
            }
        }
        return null;
    }

    private static String subjectKey(String subject) {
        return subject == null ? "" : subject.trim().toLowerCase(Locale.ROOT);
    }
}
