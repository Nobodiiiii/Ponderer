package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.network.PermissionListRequestPayload;
import com.nododiiiii.ponderer.network.PermissionListResponsePayload;
import com.nododiiiii.ponderer.network.PermissionUpdateRequestPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.ButtonPairListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.SearchableListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PermissionManagementScreen extends AbstractReadonlyDeclarativeListScreen {

    private static final int SCREEN_WIDTH = 380;

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
        if (!requestSent) {
            requestRefresh();
        }
    }

    public void receiveSnapshot(PermissionListResponsePayload payload) {
        entries.clear();
        entries.addAll(payload.entries());
        entries.sort(Comparator
            .comparingInt((PermissionListResponsePayload.Entry entry) -> -roleLevel(entry.role()))
            .thenComparing(entry -> entry.subject().toLowerCase(Locale.ROOT)));
        viewerRole = payload.viewerRole() == null ? "" : payload.viewerRole();
        serverOperator = payload.serverOperator();
        canManage = payload.canManage();
        waitingForServer = false;

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
    protected int getEntryHeight() {
        return UILayoutConstants.LIST_ENTRY_H;
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> headerEntries) {
        PlainTextListEntry subjectEntry = new PlainTextListEntry(
            "ponderer.ui.function_page.permissions.player",
            "ponderer.ui.function_page.permissions.player.tooltip",
            "ponderer.ui.function_page.permissions.player.hint",
            pendingSubject,
            value -> pendingSubject = value);
        subjectEntry.field().setMaxLength(64);
        headerEntries.add(subjectEntry);

        headerEntries.add(new ButtonPairListEntry(
            UIText.of(roleLabelKey(selectedRole)),
            UIText.of("ponderer.ui.function_page.permissions.role.tooltip"),
            this::cycleSelectedRole,
            UIText.of("ponderer.ui.function_page.permissions.set"),
            UIText.of("ponderer.ui.function_page.permissions.set.tooltip"),
            () -> sendSet(pendingSubject, selectedRole)));

        headerEntries.add(new ButtonPairListEntry(
            UIText.of("ponderer.ui.function_page.permissions.use_self"),
            UIText.of("ponderer.ui.function_page.permissions.use_self.tooltip"),
            this::fillSelf,
            UIText.of("ponderer.ui.function_page.permissions.refresh"),
            UIText.of("ponderer.ui.function_page.permissions.refresh.tooltip"),
            this::requestRefresh));
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> rows) {
        rows.add(new PermissionSummaryEntry(entries, viewerRole, serverOperator, canManage));

        if (waitingForServer && entries.isEmpty()) {
            rows.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.function_page.permissions.loading")));
            return;
        }

        if (entries.isEmpty()) {
            rows.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.function_page.permissions.empty")));
            return;
        }

        addRoleSection(rows, UploadPermissions.Role.ADMIN);
        addRoleSection(rows, UploadPermissions.Role.UPLOAD);
        addRoleSection(rows, UploadPermissions.Role.PULL);
    }

    private void addRoleSection(List<ConfigScreenList.Entry> rows, UploadPermissions.Role role) {
        List<PermissionListResponsePayload.Entry> matching = entries.stream()
            .filter(entry -> role.id().equals(entry.role()))
            .toList();
        if (matching.isEmpty()) {
            return;
        }

        rows.add(new SectionHeaderListEntry(UIText.of(roleSectionKey(role), matching.size())));
        for (PermissionListResponsePayload.Entry entry : matching) {
            rows.add(new PermissionRowEntry(
                entry,
                canManage,
                () -> sendSet(entry.subject(), nextRole(roleFromId(entry.role()))),
                () -> sendRemove(entry.subject())));
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
        selectedRole = nextRole(selectedRole);
        clearStatusMessages();
        rebuildEntries(currentListScroll());
    }

    private void fillSelf() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            pendingSubject = player.getGameProfile().getName();
            clearStatusMessages();
            rebuildEntries(currentListScroll());
        }
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
        return roleFromId(roleId).ordinal();
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

    private static final class PermissionSummaryEntry extends ConfigScreenList.LabeledEntry
        implements SearchableListEntry {

        private final List<PermissionListResponsePayload.Entry> entries;
        private final String viewerRole;
        private final boolean serverOperator;
        private final boolean canManage;

        private PermissionSummaryEntry(List<PermissionListResponsePayload.Entry> entries, String viewerRole,
                                       boolean serverOperator, boolean canManage) {
            super("");
            this.entries = entries;
            this.viewerRole = viewerRole == null ? "" : viewerRole;
            this.serverOperator = serverOperator;
            this.canManage = canManage;
        }

        @Override
        public boolean matchesQuery(String query) {
            return buildSearchText().contains(query);
        }

        @Override
        public void highlightEntry() {
            annotations.put("highlight", ":)");
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            var font = Minecraft.getInstance().font;
            String summary = UIText.of("ponderer.ui.function_page.permissions.summary",
                entries.size(),
                countRole(UploadPermissions.Role.ADMIN),
                countRole(UploadPermissions.Role.UPLOAD),
                countRole(UploadPermissions.Role.PULL));
            String status = UIText.of(
                canManage
                    ? "ponderer.ui.function_page.permissions.viewer.manage"
                    : "ponderer.ui.function_page.permissions.viewer.readonly",
                viewerRoleLabel());

            graphics.drawString(font, font.plainSubstrByWidth(summary, width - 8), x + 4, y + 8, 0xE0E0E0);
            graphics.drawString(font, font.plainSubstrByWidth(status, width - 8), x + 4, y + 22, 0xA0A0A0);
        }

        private int countRole(UploadPermissions.Role role) {
            int count = 0;
            for (PermissionListResponsePayload.Entry entry : entries) {
                if (role.id().equals(entry.role())) {
                    count++;
                }
            }
            return count;
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

        private String buildSearchText() {
            return (UIText.of("ponderer.ui.function_page.permissions.summary", entries.size(), 0, 0, 0)
                + " "
                + viewerRoleLabel()).toLowerCase(Locale.ROOT);
        }
    }

    private static final class PermissionRowEntry extends ConfigScreenList.LabeledEntry
        implements SearchableListEntry {

        private static final int ROLE_BUTTON_WIDTH = 70;
        private static final int REMOVE_BUTTON_WIDTH = 44;
        private static final int BUTTON_GAP = 6;

        private final PermissionListResponsePayload.Entry entry;
        private final boolean canManage;
        private final Runnable onCycleRole;
        private final Runnable onRemove;
        private final BoxWidget roleButton;
        private final BoxWidget removeButton;

        private PermissionRowEntry(PermissionListResponsePayload.Entry entry, boolean canManage,
                                   Runnable onCycleRole, Runnable onRemove) {
            super("");
            this.entry = entry;
            this.canManage = canManage;
            this.onCycleRole = onCycleRole;
            this.onRemove = onRemove;
            this.roleButton = new BoxWidget(0, 0, ROLE_BUTTON_WIDTH, 16).withCallback(onCycleRole);
            this.removeButton = new BoxWidget(0, 0, REMOVE_BUTTON_WIDTH, 16).withCallback(onRemove);
            listeners.add(roleButton);
            listeners.add(removeButton);
            refreshTooltips();
        }

        @Override
        public boolean matchesQuery(String query) {
            return (entry.subject() + " " + UIText.of(roleLabelKey(role()))).toLowerCase(Locale.ROOT)
                .contains(query);
        }

        @Override
        public void highlightEntry() {
            annotations.put("highlight", ":)");
        }

        @Override
        public void tick() {
            super.tick();
            roleButton.active = canManage;
            removeButton.active = canManage;
            roleButton.tick();
            removeButton.tick();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            var font = Minecraft.getInstance().font;
            UploadPermissions.Role role = role();
            int buttonY = y + Math.max(8, (height - 16) / 2);
            int removeX = x + width - REMOVE_BUTTON_WIDTH - 4;
            int roleX = removeX - BUTTON_GAP - ROLE_BUTTON_WIDTH;
            int subjectWidth = Math.max(40, roleX - x - 10);

            graphics.drawString(font, font.plainSubstrByWidth(entry.subject(), subjectWidth), x + 4, y + 15,
                annotations.containsKey("highlight") ? 0xFFF3D46B : 0xE0E0E0);

            renderButton(graphics, roleButton, roleX, buttonY, ROLE_BUTTON_WIDTH, UIText.of(roleLabelKey(role)),
                roleColor(role), mouseX, mouseY, partialTicks);
            renderButton(graphics, removeButton, removeX, buttonY, REMOVE_BUTTON_WIDTH,
                UIText.of("ponderer.ui.function_page.permissions.remove"), 0xFF9090,
                mouseX, mouseY, partialTicks);
        }

        private UploadPermissions.Role role() {
            return roleFromId(entry.role());
        }

        private void renderButton(GuiGraphics graphics, BoxWidget button, int x, int y, int width, String label,
                                  int color, int mouseX, int mouseY, float partialTicks) {
            refreshTooltips();
            button.setX(x);
            button.setY(y);
            button.setWidth(width);
            button.setHeight(16);
            button.active = canManage;
            button.updateGradientFromState();
            button.render(graphics, mouseX, mouseY, partialTicks);
            graphics.drawCenteredString(Minecraft.getInstance().font, label,
                button.getX() + button.getWidth() / 2,
                button.getY() + 4,
                button.active ? color : 0x777777);
        }

        private void refreshTooltips() {
            roleButton.getToolTip().clear();
            roleButton.getToolTip().add(net.minecraft.network.chat.Component.literal(
                UIText.of("ponderer.ui.function_page.permissions.row.role.tooltip")));
            removeButton.getToolTip().clear();
            removeButton.getToolTip().add(net.minecraft.network.chat.Component.literal(
                UIText.of("ponderer.ui.function_page.permissions.row.remove.tooltip")));
        }
    }
}
