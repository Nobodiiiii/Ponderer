package com.nododiiiii.ponderer.application.ui;

import com.nododiiiii.ponderer.application.ui.widgets.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.application.ui.widgets.EntryTextSupport;
import com.nododiiiii.ponderer.application.ui.widgets.SearchableListEntry;
import com.nododiiiii.ponderer.application.ui.widgets.SectionHeaderRenderer;
import com.nododiiiii.ponderer.exporter.JavaModuleExportRequest.Mode;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.CatnipClientServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public class ExportModeSelectScreen extends AbstractReadonlyDeclarativeListScreen {

    private static final String HELP_KEY = "ponderer.ui.export_java.mode.help.";
    private static final int HEADING_HEIGHT = UILayoutConstants.COMPACT_LIST_ENTRY_H;
    private static final int TEXT_PADDING = 6;

    private final Consumer<Mode> onSelect;
    private final List<Option> options = createOptions();

    private record Option(Mode mode, Component title, List<Component> body) {
    }

    private record StyledRun(int start, int end, String text, Style style) {
    }

    public ExportModeSelectScreen(JavaModuleExportScreen source, Consumer<Mode> onSelect) {
        super(source, "ponderer.ui.scope.editor", HELP_KEY + "title", UILayoutConstants.EDITOR_LIST_W);
        this.onSelect = onSelect;
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new ConfigScreenList.LabeledEntry("") {
            @Override
            public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTicks) {
                super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
                List<FormattedCharSequence> lines = wrapText(Component.translatable(titleKey), textWidth(width));
                int textY = y + (height - lines.size() * lineHeight()) / 2;
                for (FormattedCharSequence line : lines) {
                    graphics.drawString(font, line, x + (width - font.width(line)) / 2, textY, titleColor(), false);
                    textY += lineHeight();
                }
            }
        });
    }

    @Override
    protected int getHeaderEntryHeight() {
        return UILayoutConstants.LIST_ENTRY_H;
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        for (Option option : options) {
            entries.add(new OptionEntry(option));
        }
        entries.add(new AdviceEntry());
    }

    @Override
    protected int getEntryHeight() {
        int rowWidth = currentListWidthValue() - 2 * UILayoutConstants.LIST_ROW_MARGIN;
        int lines = options.stream()
            .mapToInt(option -> wrappedBody(option, rowWidth).size())
            .max().orElse(0);
        return HEADING_HEIGHT + TEXT_PADDING + lines * lineHeight() + TEXT_PADDING + 4;
    }

    @Override
    protected int listBottomPadding(int viewportHeight) {
        int rowWidth = currentListWidthValue() - 2 * UILayoutConstants.LIST_ROW_MARGIN;
        int adviceHeight = wrapText(Component.translatable(HELP_KEY + "pack_author_advice"), textWidth(rowWidth)).size()
            * lineHeight() + TEXT_PADDING * 2 + 4;
        return Math.min(0, adviceHeight - getEntryHeight());
    }

    private List<FormattedCharSequence> wrappedBody(Option option, int rowWidth) {
        return option.body().stream().flatMap(line -> wrapText(line, textWidth(rowWidth)).stream()).toList();
    }

    private List<FormattedCharSequence> wrapText(Component text, int maxWidth) {
        List<StyledRun> runs = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        text.visit((style, value) -> {
            int start = plain.length();
            plain.append(value);
            runs.add(new StyledRun(start, plain.length(), value, style));
            return Optional.empty();
        }, Style.EMPTY);

        String value = plain.toString();
        BreakIterator breaks = BreakIterator.getLineInstance(CatnipClientServices.CLIENT_HOOKS.getCurrentLocale());
        breaks.setText(value);
        List<FormattedCharSequence> lines = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int newline = value.indexOf('\n', start);
            int paragraphEnd = newline < 0 ? value.length() : newline;
            int end = start + font.getSplitter()
                .headByWidth(styledSlice(runs, start, paragraphEnd), maxWidth, Style.EMPTY).getString().length();
            if (end < paragraphEnd) {
                // Prefer language-aware word and CJK boundaries; split an oversized word only when necessary.
                int boundary = breaks.preceding(end + 1);
                end = boundary > start ? boundary : Math.max(end, value.offsetByCodePoints(start, 1));
            }
            int visibleEnd = end;
            while (visibleEnd > start && Character.isWhitespace(value.codePointBefore(visibleEnd))) {
                visibleEnd -= Character.charCount(value.codePointBefore(visibleEnd));
            }
            lines.add(Language.getInstance().getVisualOrder(styledSlice(runs, start, visibleEnd)));
            start = end;
            if (start == paragraphEnd && newline >= 0) {
                start++;
            } else {
                while (start < paragraphEnd && Character.isWhitespace(value.codePointAt(start))) {
                    start += Character.charCount(value.codePointAt(start));
                }
            }
        }
        return lines;
    }

    private static FormattedText styledSlice(List<StyledRun> runs, int start, int end) {
        List<FormattedText> parts = new ArrayList<>();
        for (StyledRun run : runs) {
            int from = Math.max(start, run.start());
            int to = Math.min(end, run.end());
            if (from < to) {
                parts.add(FormattedText.of(run.text().substring(from - run.start(), to - run.start()), run.style()));
            }
        }
        return FormattedText.composite(parts);
    }

    private int lineHeight() {
        return font.lineHeight + 2;
    }

    private static int textWidth(int rowWidth) {
        return Math.max(1, EntryTextSupport.contentWidth(rowWidth));
    }

    private static int textColor() {
        return UIRenderHelper.COLOR_TEXT.getFirst().getRGB();
    }

    private static int titleColor() {
        return UIRenderHelper.COLOR_TEXT_STRONG_ACCENT.getFirst().getRGB();
    }

    private static List<Option> createOptions() {
        Component libDependency = emphasis("lib_dependency");
        Component fullExport = Component.translatable(HELP_KEY + "full_export", emphasis("full_export.emphasis"));
        return List.of(
            new Option(Mode.JAVA_LIB, Component.translatable(HELP_KEY + "java_lib.title"), List.of(
                Component.translatable(HELP_KEY + "java_lib.output", libDependency),
                fullExport,
                Component.translatable(HELP_KEY + "java_lib.editing"),
                Component.translatable(HELP_KEY + "java_lib.customization", emphasis("java_lib.customization.emphasis")))),
            new Option(Mode.JSON_LIB, Component.literal("JSON + Lib"), List.of(
                Component.translatable(HELP_KEY + "json_lib.output", libDependency),
                fullExport,
                Component.translatable(HELP_KEY + "json_lib.editing"),
                Component.translatable(HELP_KEY + "json_lib.customization"))),
            new Option(Mode.JAVA_SUPPORT, Component.literal("Java + Support"), List.of(
                Component.translatable(HELP_KEY + "java_support.output", emphasis("java_support.no_dependency")),
                Component.translatable(HELP_KEY + "java_support.compatibility", emphasis("java_support.compatibility.emphasis")),
                Component.translatable(HELP_KEY + "java_support.editing"))));
    }

    private static Component emphasis(String key) {
        return Component.translatable(HELP_KEY + key).withStyle(ChatFormatting.BOLD);
    }

    private void select(Option option) {
        onSelect.accept(option.mode());
        attemptBackToParent();
    }

    private class OptionEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {
        private final Option option;
        private final String searchText;

        private OptionEntry(Option option) {
            super("");
            this.option = option;
            this.searchText = narration().getString().toLowerCase(Locale.ROOT);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            boolean highlighted = annotations.containsKey("highlight");
            if (hovered || highlighted || highlightAnimation.getValue(partialTicks) > 0) {
                super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
            }
            int left = EntryTextSupport.contentLeft(x);
            SectionHeaderRenderer.render(graphics, font, option.title().getString(), left,
                EntryTextSupport.contentRight(x, width), y, HEADING_HEIGHT, titleColor());
            int textY = y + HEADING_HEIGHT + TEXT_PADDING;
            for (FormattedCharSequence line : wrappedBody(option, width)) {
                graphics.drawString(font, line, left, textY, textColor(), false);
                textY += lineHeight();
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            select(option);
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                select(option);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean matchesQuery(String query) {
            return searchText.contains(query);
        }

        @Override
        public void highlightEntry() {
            annotations.put("highlight", ":)");
        }

        private Component narration() {
            var narration = option.title().copy();
            for (Component line : option.body()) {
                narration.append("\n").append(line);
            }
            return narration;
        }

        @Override
        public Component getNarration() {
            return narration();
        }
    }

    private class AdviceEntry extends ConfigScreenList.Entry implements SearchableListEntry {
        private final Component text = Component.translatable(HELP_KEY + "pack_author_advice");

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            int textY = y + TEXT_PADDING;
            for (FormattedCharSequence line : wrapText(text, textWidth(width))) {
                graphics.drawString(font, line, EntryTextSupport.contentLeft(x), textY, textColor(), false);
                textY += lineHeight();
            }
        }

        @Override
        public boolean matchesQuery(String query) {
            return text.getString().toLowerCase(Locale.ROOT).contains(query);
        }

        @Override
        public void highlightEntry() {
        }

        @Override
        public Component getNarration() {
            return text;
        }
    }
}
