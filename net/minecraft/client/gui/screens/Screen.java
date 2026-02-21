package net.minecraft.client.gui.screens;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.TabOrderedElement;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public abstract class Screen extends AbstractContainerEventHandler implements Renderable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component USAGE_NARRATION = Component.translatable("narrator.screen.usage");
    protected static final CubeMap CUBE_MAP = new CubeMap(ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama"));
    protected static final PanoramaRenderer PANORAMA = new PanoramaRenderer(CUBE_MAP);
    public static final ResourceLocation MENU_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_background.png");
    public static final ResourceLocation HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/header_separator.png");
    public static final ResourceLocation FOOTER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/footer_separator.png");
    private static final ResourceLocation INWORLD_MENU_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_background.png");
    public static final ResourceLocation INWORLD_HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/inworld_header_separator.png");
    public static final ResourceLocation INWORLD_FOOTER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/inworld_footer_separator.png");
    protected final Component title;
    private final List<GuiEventListener> children = Lists.newArrayList();
    private final List<NarratableEntry> narratables = Lists.newArrayList();
    @Nullable
    protected Minecraft minecraft;
    private boolean initialized;
    public int width;
    public int height;
    public final List<Renderable> renderables = Lists.newArrayList();
    protected Font font;
    private static final long NARRATE_SUPPRESS_AFTER_INIT_TIME = TimeUnit.SECONDS.toMillis(2L);
    private static final long NARRATE_DELAY_NARRATOR_ENABLED = NARRATE_SUPPRESS_AFTER_INIT_TIME;
    private static final long NARRATE_DELAY_MOUSE_MOVE = 750L;
    private static final long NARRATE_DELAY_MOUSE_ACTION = 200L;
    private static final long NARRATE_DELAY_KEYBOARD_ACTION = 200L;
    private final ScreenNarrationCollector narrationState = new ScreenNarrationCollector();
    private long narrationSuppressTime = Long.MIN_VALUE;
    private long nextNarrationTime = Long.MAX_VALUE;
    @Nullable
    protected CycleButton<NarratorStatus> narratorButton;
    @Nullable
    private NarratableEntry lastNarratable;
    @Nullable
    private Screen.DeferredTooltipRendering deferredTooltipRendering;
    protected final Executor screenExecutor = p_289626_ -> this.minecraft.execute(() -> {
            if (this.minecraft.screen == this) {
                p_289626_.run();
            }
        });

    protected Screen(Component title) {
        this.title = title;
    }

    public Component getTitle() {
        return this.title;
    }

    public Component getNarrationMessage() {
        return this.getTitle();
    }

    public final void renderWithTooltip(GuiGraphics p_282345_, int p_283456_, int p_283586_, float p_282339_) {
        this.render(p_282345_, p_283456_, p_283586_, p_282339_);
        if (this.deferredTooltipRendering != null) {
            p_282345_.renderTooltip(this.font, this.deferredTooltipRendering.tooltip(), this.deferredTooltipRendering.positioner(), p_283456_, p_283586_);
            this.deferredTooltipRendering = null;
        }
    }

    /** Neo: mixins targeting this method won't fire for container screens as {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen#render} replicates this method in place of a super call to insert an event */
    @Override
    public void render(GuiGraphics p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        this.renderBackground(p_281549_, p_281550_, p_282878_, p_282465_);

        for (Renderable renderable : this.renderables) {
            renderable.render(p_281549_, p_281550_, p_282878_, p_282465_);
        }
    }

    @Override
    public boolean keyPressed(int p_96552_, int p_96553_, int p_96554_) {
        if (p_96552_ == 256 && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        } else if (super.keyPressed(p_96552_, p_96553_, p_96554_)) {
            return true;
        } else {
            FocusNavigationEvent focusnavigationevent = (FocusNavigationEvent)(switch (p_96552_) {
                case 258 -> this.createTabEvent();
                default -> null;
                case 262 -> this.createArrowEvent(ScreenDirection.RIGHT);
                case 263 -> this.createArrowEvent(ScreenDirection.LEFT);
                case 264 -> this.createArrowEvent(ScreenDirection.DOWN);
                case 265 -> this.createArrowEvent(ScreenDirection.UP);
            });
            if (focusnavigationevent != null) {
                ComponentPath componentpath = super.nextFocusPath(focusnavigationevent);
                if (componentpath == null && focusnavigationevent instanceof FocusNavigationEvent.TabNavigation) {
                    this.clearFocus();
                    componentpath = super.nextFocusPath(focusnavigationevent);
                }

                if (componentpath != null) {
                    this.changeFocus(componentpath);
                }
            }

            return false;
        }
    }

    private FocusNavigationEvent.TabNavigation createTabEvent() {
        boolean flag = !hasShiftDown();
        return new FocusNavigationEvent.TabNavigation(flag);
    }

    private FocusNavigationEvent.ArrowNavigation createArrowEvent(ScreenDirection direction) {
        return new FocusNavigationEvent.ArrowNavigation(direction);
    }

    protected void setInitialFocus() {
        if (this.minecraft.getLastInputType().isKeyboard()) {
            FocusNavigationEvent.TabNavigation focusnavigationevent$tabnavigation = new FocusNavigationEvent.TabNavigation(true);
            ComponentPath componentpath = super.nextFocusPath(focusnavigationevent$tabnavigation);
            if (componentpath != null) {
                this.changeFocus(componentpath);
            }
        }
    }

    protected void setInitialFocus(GuiEventListener listener) {
        ComponentPath componentpath = ComponentPath.path(this, listener.nextFocusPath(new FocusNavigationEvent.InitialFocus()));
        if (componentpath != null) {
            this.changeFocus(componentpath);
        }
    }

    public void clearFocus() {
        ComponentPath componentpath = this.getCurrentFocusPath();
        if (componentpath != null) {
            componentpath.applyFocus(false);
        }
    }

    @VisibleForTesting
    protected void changeFocus(ComponentPath path) {
        this.clearFocus();
        path.applyFocus(true);
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    public void onClose() {
        this.minecraft.popGuiLayer();
    }

    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget) {
        this.renderables.add(widget);
        return this.addWidget(widget);
    }

    protected <T extends Renderable> T addRenderableOnly(T renderable) {
        this.renderables.add(renderable);
        return renderable;
    }

    protected <T extends GuiEventListener & NarratableEntry> T addWidget(T listener) {
        this.children.add(listener);
        this.narratables.add(listener);
        return listener;
    }

    protected void removeWidget(GuiEventListener listener) {
        if (listener instanceof Renderable) {
            this.renderables.remove((Renderable)listener);
        }

        if (listener instanceof NarratableEntry) {
            this.narratables.remove((NarratableEntry)listener);
        }

        this.children.remove(listener);
    }

    protected void clearWidgets() {
        this.renderables.clear();
        this.children.clear();
        this.narratables.clear();
    }

    public static List<Component> getTooltipFromItem(Minecraft minecraft, ItemStack item) {
        return item.getTooltipLines(
            Item.TooltipContext.of(minecraft.level),
            minecraft.player,
            net.neoforged.neoforge.client.ClientTooltipFlag.of(minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL)
        );
    }

    protected void insertText(String text, boolean overwrite) {
    }

    public boolean handleComponentClicked(@Nullable Style style) {
        if (style == null) {
            return false;
        } else {
            ClickEvent clickevent = style.getClickEvent();
            if (hasShiftDown()) {
                if (style.getInsertion() != null) {
                    this.insertText(style.getInsertion(), false);
                }
            } else if (clickevent != null) {
                if (clickevent.getAction() == ClickEvent.Action.OPEN_URL) {
                    if (!this.minecraft.options.chatLinks().get()) {
                        return false;
                    }

                    try {
                        URI uri = Util.parseAndValidateUntrustedUri(clickevent.getValue());
                        if (this.minecraft.options.chatLinksPrompt().get()) {
                            this.minecraft.setScreen(new ConfirmLinkScreen(p_351659_ -> {
                                if (p_351659_) {
                                    Util.getPlatform().openUri(uri);
                                }

                                this.minecraft.setScreen(this);
                            }, clickevent.getValue(), false));
                        } else {
                            Util.getPlatform().openUri(uri);
                        }
                    } catch (URISyntaxException urisyntaxexception) {
                        LOGGER.error("Can't open url for {}", clickevent, urisyntaxexception);
                    }
                } else if (clickevent.getAction() == ClickEvent.Action.OPEN_FILE) {
                    Util.getPlatform().openFile(new File(clickevent.getValue()));
                } else if (clickevent.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                    this.insertText(StringUtil.filterText(clickevent.getValue()), true);
                } else if (clickevent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                    String s = StringUtil.filterText(clickevent.getValue());
                    if (s.startsWith("/")) {
                        if (!this.minecraft.player.connection.sendUnsignedCommand(s.substring(1))) {
                            LOGGER.error("Not allowed to run command with signed argument from click event: '{}'", s);
                        }
                    } else {
                        LOGGER.error("Failed to run command without '/' prefix from click event: '{}'", s);
                    }
                } else if (clickevent.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
                    this.minecraft.keyboardHandler.setClipboard(clickevent.getValue());
                } else {
                    LOGGER.error("Don't know how to handle {}", clickevent);
                }

                return true;
            }

            return false;
        }
    }

    public final void init(Minecraft minecraft, int width, int height) {
        this.minecraft = minecraft;
        this.font = minecraft.font;
        this.width = width;
        this.height = height;
        if (!this.initialized) {
            if (!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.Init.Pre(this, this.children, this::addEventWidget, this::removeWidget)).isCanceled()) {
            this.init();
            this.setInitialFocus();
            }
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.Init.Post(this, this.children, this::addEventWidget, this::removeWidget));
        } else {
            this.repositionElements();
        }

        this.initialized = true;
        this.triggerImmediateNarration(false);
        this.suppressNarration(NARRATE_SUPPRESS_AFTER_INIT_TIME);
    }

    protected void rebuildWidgets() {
        this.clearWidgets();
        this.clearFocus();
        if (!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.Init.Pre(this, this.children, this::addEventWidget, this::removeWidget)).isCanceled()) {
        this.init();
        this.setInitialFocus();
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.Init.Post(this, this.children, this::addEventWidget, this::removeWidget));
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return this.children;
    }

    protected void init() {
    }

    public void tick() {
    }

    public void removed() {
    }

    public void added() {
    }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.level == null) {
            this.renderPanorama(guiGraphics, partialTick);
        }

        this.renderBlurredBackground(partialTick);
        this.renderMenuBackground(guiGraphics);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.BackgroundRendered(this, guiGraphics));
    }

    protected void renderBlurredBackground(float p_330683_) {
        // Neo: fix blur effect rendered at high z with depth test breaking subsequent rendering of screen elements (https://github.com/neoforged/NeoForge/issues/1504)
        RenderSystem.disableDepthTest();
        this.minecraft.gameRenderer.processBlurEffect(p_330683_);
        this.minecraft.getMainRenderTarget().bindWrite(false);
    }

    protected void renderPanorama(GuiGraphics guiGraphics, float partialTick) {
        PANORAMA.render(guiGraphics, this.width, this.height, 1.0F, partialTick);
    }

    protected void renderMenuBackground(GuiGraphics partialTick) {
        this.renderMenuBackground(partialTick, 0, 0, this.width, this.height);
    }

    protected void renderMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        renderMenuBackgroundTexture(
            guiGraphics, this.minecraft.level == null ? MENU_BACKGROUND : INWORLD_MENU_BACKGROUND, x, y, 0.0F, 0.0F, width, height
        );
    }

    public static void renderMenuBackgroundTexture(
        GuiGraphics p_331514_, ResourceLocation p_333918_, int p_330327_, int p_331282_, float p_334038_, float p_334054_, int p_331309_, int p_331449_
    ) {
        int i = 32;
        RenderSystem.enableBlend();
        p_331514_.blit(p_333918_, p_330327_, p_331282_, 0, p_334038_, p_334054_, p_331309_, p_331449_, 32, 32);
        RenderSystem.disableBlend();
    }

    public void renderTransparentBackground(GuiGraphics guiGraphics) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
    }

    public boolean isPauseScreen() {
        return true;
    }

    public static boolean hasControlDown() {
        return Minecraft.ON_OSX
            ? InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 343)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 347)
            : InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 341)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 345);
    }

    public static boolean hasShiftDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 340)
            || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 344);
    }

    public static boolean hasAltDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 342)
            || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 346);
    }

    public static boolean isCut(int p_96629_) {
        return p_96629_ == 88 && hasControlDown() && !hasShiftDown() && !hasAltDown();
    }

    public static boolean isPaste(int p_96631_) {
        return p_96631_ == 86 && hasControlDown() && !hasShiftDown() && !hasAltDown();
    }

    public static boolean isCopy(int p_96633_) {
        return p_96633_ == 67 && hasControlDown() && !hasShiftDown() && !hasAltDown();
    }

    public static boolean isSelectAll(int p_96635_) {
        return p_96635_ == 65 && hasControlDown() && !hasShiftDown() && !hasAltDown();
    }

    protected void repositionElements() {
        this.rebuildWidgets();
    }

    public void resize(Minecraft minecraft, int width, int height) {
        this.width = width;
        this.height = height;
        this.repositionElements();
    }

    public static void wrapScreenError(Runnable p_96580_, String p_96581_, String p_96582_) {
        try {
            p_96580_.run();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, p_96581_);
            CrashReportCategory crashreportcategory = crashreport.addCategory("Affected screen");
            crashreportcategory.setDetail("Screen name", () -> p_96582_);
            throw new ReportedException(crashreport);
        }
    }

    protected boolean isValidCharacterForName(String p_96584_, char p_96585_, int p_96586_) {
        int i = p_96584_.indexOf(58);
        int j = p_96584_.indexOf(47);
        if (p_96585_ == ':') {
            return (j == -1 || p_96586_ <= j) && i == -1;
        } else {
            return p_96585_ == '/'
                ? p_96586_ > i
                : p_96585_ == '_' || p_96585_ == '-' || p_96585_ >= 'a' && p_96585_ <= 'z' || p_96585_ >= '0' && p_96585_ <= '9' || p_96585_ == '.';
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return true;
    }

    public void onFilesDrop(List<Path> packs) {
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    private void scheduleNarration(long delay, boolean stopSuppression) {
        this.nextNarrationTime = Util.getMillis() + delay;
        if (stopSuppression) {
            this.narrationSuppressTime = Long.MIN_VALUE;
        }
    }

    private void suppressNarration(long time) {
        this.narrationSuppressTime = Util.getMillis() + time;
    }

    public void afterMouseMove() {
        this.scheduleNarration(750L, false);
    }

    public void afterMouseAction() {
        this.scheduleNarration(200L, true);
    }

    public void afterKeyboardAction() {
        this.scheduleNarration(200L, true);
    }

    private boolean shouldRunNarration() {
        return this.minecraft.getNarrator().isActive();
    }

    public void handleDelayedNarration() {
        if (this.shouldRunNarration()) {
            long i = Util.getMillis();
            if (i > this.nextNarrationTime && i > this.narrationSuppressTime) {
                this.runNarration(true);
                this.nextNarrationTime = Long.MAX_VALUE;
            }
        }
    }

    public void triggerImmediateNarration(boolean onlyNarrateNew) {
        if (this.shouldRunNarration()) {
            this.runNarration(onlyNarrateNew);
        }
    }

    private void runNarration(boolean onlyNarrateNew) {
        this.narrationState.update(this::updateNarrationState);
        String s = this.narrationState.collectNarrationText(!onlyNarrateNew);
        if (!s.isEmpty()) {
            this.minecraft.getNarrator().sayNow(s);
        }
    }

    protected boolean shouldNarrateNavigation() {
        return true;
    }

    protected void updateNarrationState(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getNarrationMessage());
        if (this.shouldNarrateNavigation()) {
            output.add(NarratedElementType.USAGE, USAGE_NARRATION);
        }

        this.updateNarratedWidget(output);
    }

    protected void updateNarratedWidget(NarrationElementOutput narrationElementOutput) {
        List<NarratableEntry> list = this.narratables
            .stream()
            .filter(NarratableEntry::isActive)
            .sorted(Comparator.comparingInt(TabOrderedElement::getTabOrderGroup))
            .toList();
        Screen.NarratableSearchResult screen$narratablesearchresult = findNarratableWidget(list, this.lastNarratable);
        if (screen$narratablesearchresult != null) {
            if (screen$narratablesearchresult.priority.isTerminal()) {
                this.lastNarratable = screen$narratablesearchresult.entry;
            }

            if (list.size() > 1) {
                narrationElementOutput.add(
                    NarratedElementType.POSITION, Component.translatable("narrator.position.screen", screen$narratablesearchresult.index + 1, list.size())
                );
                if (screen$narratablesearchresult.priority == NarratableEntry.NarrationPriority.FOCUSED) {
                    narrationElementOutput.add(NarratedElementType.USAGE, this.getUsageNarration());
                }
            }

            screen$narratablesearchresult.entry.updateNarration(narrationElementOutput.nest());
        }
    }

    protected Component getUsageNarration() {
        return Component.translatable("narration.component_list.usage");
    }

    @Nullable
    public static Screen.NarratableSearchResult findNarratableWidget(List<? extends NarratableEntry> entries, @Nullable NarratableEntry target) {
        Screen.NarratableSearchResult screen$narratablesearchresult = null;
        Screen.NarratableSearchResult screen$narratablesearchresult1 = null;
        int i = 0;

        for (int j = entries.size(); i < j; i++) {
            NarratableEntry narratableentry = entries.get(i);
            NarratableEntry.NarrationPriority narratableentry$narrationpriority = narratableentry.narrationPriority();
            if (narratableentry$narrationpriority.isTerminal()) {
                if (narratableentry != target) {
                    return new Screen.NarratableSearchResult(narratableentry, i, narratableentry$narrationpriority);
                }

                screen$narratablesearchresult1 = new Screen.NarratableSearchResult(narratableentry, i, narratableentry$narrationpriority);
            } else if (narratableentry$narrationpriority.compareTo(
                    screen$narratablesearchresult != null ? screen$narratablesearchresult.priority : NarratableEntry.NarrationPriority.NONE
                )
                > 0) {
                screen$narratablesearchresult = new Screen.NarratableSearchResult(narratableentry, i, narratableentry$narrationpriority);
            }
        }

        return screen$narratablesearchresult != null ? screen$narratablesearchresult : screen$narratablesearchresult1;
    }

    public void updateNarratorStatus(boolean narratorEnabled) {
        if (narratorEnabled) {
            this.scheduleNarration(NARRATE_DELAY_NARRATOR_ENABLED, false);
        }

        if (this.narratorButton != null) {
            this.narratorButton.setValue(this.minecraft.options.narrator().get());
        }
    }

    protected void clearTooltipForNextRenderPass() {
        this.deferredTooltipRendering = null;
    }

    public void setTooltipForNextRenderPass(List<FormattedCharSequence> p_259937_) {
        this.setTooltipForNextRenderPass(p_259937_, DefaultTooltipPositioner.INSTANCE, true);
    }

    public void setTooltipForNextRenderPass(List<FormattedCharSequence> p_262939_, ClientTooltipPositioner p_263078_, boolean p_263107_) {
        if (this.deferredTooltipRendering == null || p_263107_) {
            this.deferredTooltipRendering = new Screen.DeferredTooltipRendering(p_262939_, p_263078_);
        }
    }

    public void setTooltipForNextRenderPass(Component p_259986_) {
        this.setTooltipForNextRenderPass(Tooltip.splitTooltip(this.minecraft, p_259986_));
    }

    public void setTooltipForNextRenderPass(Tooltip p_262992_, ClientTooltipPositioner p_262980_, boolean p_262988_) {
        this.setTooltipForNextRenderPass(p_262992_.toCharSequence(this.minecraft), p_262980_, p_262988_);
    }

    @Override
    public ScreenRectangle getRectangle() {
        return new ScreenRectangle(0, 0, this.width, this.height);
    }

    @Nullable
    public Music getBackgroundMusic() {
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    static record DeferredTooltipRendering(List<FormattedCharSequence> tooltip, ClientTooltipPositioner positioner) {
    }

    @OnlyIn(Dist.CLIENT)
    public static class NarratableSearchResult {
        public final NarratableEntry entry;
        public final int index;
        public final NarratableEntry.NarrationPriority priority;

        public NarratableSearchResult(NarratableEntry entry, int index, NarratableEntry.NarrationPriority priority) {
            this.entry = entry;
            this.index = index;
            this.priority = priority;
        }
    }

    private void addEventWidget(GuiEventListener b) {
        if (b instanceof Renderable r)
            this.renderables.add(r);
        if (b instanceof NarratableEntry ne)
            this.narratables.add(ne);
        children.add(b);
    }
}
