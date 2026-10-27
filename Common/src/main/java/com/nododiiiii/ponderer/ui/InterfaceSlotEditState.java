package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.runtime.viewer.UiAnchorViewport;
import com.nododiiiii.ponderer.runtime.core.SceneItemBindings;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.runtime.api.SceneCatalog;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the "return to Ponder, drag JEI ingredients into interface slots, then
 * reopen the editor" workflow for change_interface_slot steps.
 */
public final class InterfaceSlotEditState {
    private static boolean active = false;
    private static Map<String, String> formSnapshot = new LinkedHashMap<>();
    private static LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> slotBindings = new LinkedHashMap<>();
    @Nullable
    private static StepEditorContext context;
    @Nullable
    private static UiAnchorViewport.Rect jeiViewport;
    @Nullable
    private static SandboxSession previewSession;
    private static boolean previewDirty = true;

    private InterfaceSlotEditState() {
    }

    public static void startEdit(Map<String, String> snapshot,
                                 Map<Integer, DslScene.InterfaceSlotBinding> initialBindings,
                                 String stepType,
                                 int editIndex,
                                 int insertAfterIndex,
                                 DslScene scene,
                                 int sceneIndex,
                                 SceneEditorScreen parent) {
        InterfaceSlotEditState.active = true;
        InterfaceSlotEditState.formSnapshot = new LinkedHashMap<>(snapshot);
        InterfaceSlotEditState.slotBindings = copyBindings(initialBindings);
        InterfaceSlotEditState.context = new StepEditorContext(stepType, editIndex, insertAfterIndex, scene, sceneIndex, parent);
        InterfaceSlotEditState.jeiViewport = null;
        InterfaceSlotEditState.previewSession = null;
        InterfaceSlotEditState.previewDirty = true;
    }

    public static void openPonderUIForEdit() {
        if (!active || context == null) {
            return;
        }

        ResourceLocation itemId = getItemId();
        if (itemId == null) {
            finishAndReopenEditor();
            return;
        }

        PonderUI ponderUI = PonderUI.of(itemId);
        PonderUIAccessor accessor = (PonderUIAccessor) ponderUI;
        List<PonderScene> ponderScenes = accessor.ponderer$getScenes();
        for (int i = 0; i < ponderScenes.size(); i++) {
            SceneCatalog.SceneMatch match = com.nododiiiii.ponderer.bootstrap.ProductRuntime.engine().catalog().findBySceneId(ponderScenes.get(i).getId());
            if (match != null && match.sceneIndex() == context.sceneIndex() && match.scene().id.equals(context.scene().id)) {
                accessor.ponderer$setIndex(i);
                accessor.ponderer$getLazyIndex().startWithValue(i);
                ponderScenes.get(i).begin();
                break;
            }
        }

        Minecraft.getInstance().setScreen(ponderUI);
    }

    public static boolean isActive() {
        return active;
    }

    public static void captureJeiViewport(Screen mirrorScreen) {
        Minecraft mc = Minecraft.getInstance();
        jeiViewport = UiAnchorViewport.resolveJeiForScreen(mc, mirrorScreen);
    }

    public static void clearJeiViewport() {
        jeiViewport = null;
    }

    public static boolean hasJeiViewport() {
        return jeiViewport != null && jeiViewport.isValid();
    }

    @Nullable
    public static UiAnchorViewport.Rect getJeiViewport() {
        return jeiViewport;
    }

    public static int bindingCount() {
        return (int) slotBindings.values().stream().filter(binding -> !isClearBinding(binding)).count();
    }

    public static void putBinding(int slotIndex, @Nullable Integer slotX, @Nullable Integer slotY,
                                  String ingredientId, @Nullable String ingredientKind) {
        putBinding(slotIndex, slotX, slotY, new DslScene.InterfaceSlotBinding(slotIndex, ingredientId, ingredientKind));
    }

    public static void putBinding(int slotIndex, @Nullable Integer slotX, @Nullable Integer slotY,
                                  DslScene.InterfaceSlotBinding ingredient) {
        if (!active || slotIndex < 0 || ingredient.ingredientId == null || ingredient.ingredientId.isBlank()) {
            return;
        }
        DslScene.InterfaceSlotBinding binding = ingredient.copy();
        binding.slotIndex = slotIndex;
        binding.slotX = slotX;
        binding.slotY = slotY;
        slotBindings.put(slotIndex, binding);
        previewDirty = true;
        SandboxSession session = InterfaceHost.sessionFor(Minecraft.getInstance().screen);
        if (session != null) {
            applyPreview(session);
        }
    }

    /** Items are rendered by the menu itself; only non-item ingredients need an overlay. */
    public static LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> getGhostBindingsForRender() {
        LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> ghosts = copyBindings(slotBindings);
        ghosts.values().removeIf(InterfaceSlotEditState::isItemBinding);
        return ghosts;
    }

    /** Reuses playback's item decoding, layout checks and crafting updates for the editor. */
    public static void applyPreview(SandboxSession session) {
        if (!active || session.isDestroyed() || session.menu() == null
            || !(session.currentScreen() instanceof AbstractContainerScreen<?> container)
            || container.getMenu() != session.menu()) {
            return;
        }
        if (previewSession != session) {
            previewSession = session;
            previewDirty = true;
        }
        if (!previewDirty) {
            return;
        }
        previewDirty = false;
        InterfaceStepSpecs.Slots spec = InterfaceStepSpecs.fromLegacyChangeSlot(new ArrayList<>(slotBindings.values()));
        for (InterfaceStepSpecs.SlotWrite write : spec.slots()) {
            SessionOps.writeSlot(session, write);
        }
    }

    /** Timeline writes may run after the preview was attached to a newly opened interface. */
    public static void invalidatePreview() {
        previewDirty = true;
    }

    @Nullable
    public static DslScene.InterfaceSlotBinding getBinding(int slotIndex) {
        DslScene.InterfaceSlotBinding binding = slotBindings.get(slotIndex);
        if (binding == null || isClearBinding(binding)) {
            return null;
        }
        return binding.copy();
    }

    @Nullable
    public static DslScene.InterfaceSlotBinding getBindingForSlot(Slot slot) {
        DslScene.InterfaceSlotBinding binding = getBinding(slot.index);
        if (binding != null && !isItemBinding(binding) && (binding.slotX == null || binding.slotY == null
            || (binding.slotX == slot.x && binding.slotY == slot.y))) {
            return binding;
        }
        return bindingFromStack(slot.getItem());
    }

    /** Captures the same id/count/components representation used by interface capture and playback. */
    @Nullable
    public static DslScene.InterfaceSlotBinding bindingFromStack(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (stack.isEmpty() || mc.level == null) {
            return null;
        }
        JsonElement encoded = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess()), stack)
            .result().orElse(null);
        if (encoded == null || !encoded.isJsonObject()) {
            return null;
        }
        DslScene.InterfaceSlotBinding binding = new DslScene.InterfaceSlotBinding(0,
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "item");
        binding.itemCount = stack.getCount();
        JsonElement components = encoded.getAsJsonObject().get("components");
        if (components != null && components.isJsonObject()) {
            binding.itemComponents = components.getAsJsonObject().deepCopy();
        }
        return binding;
    }

    /** Air is a persisted empty-slot write in the existing legacy binding format. */
    public static void clearBinding(Slot slot) {
        putBinding(slot.index, slot.x, slot.y, "minecraft:air", "item");
    }

    private static boolean isItemBinding(DslScene.InterfaceSlotBinding binding) {
        return binding.ingredientKind == null || binding.ingredientKind.isBlank()
            || "item".equalsIgnoreCase(binding.ingredientKind.trim());
    }

    public static boolean isClearBinding(DslScene.InterfaceSlotBinding binding) {
        return isItemBinding(binding) && "minecraft:air".equals(binding.ingredientId);
    }

    public static void finishAndReopenEditor() {
        if (!active) {
            return;
        }

        InterfaceHost.cancelSlotDrag();
        writeBindingsToSnapshot(formSnapshot, slotBindings);
        StepEditorContext reopenContext = context;

        cleanupState();

        if (reopenContext != null) {
            reopenContext.reopenEditor(formSnapshot);
        } else {
            formSnapshot.clear();
        }
    }

    public static void reset() {
        if (!active) {
            return;
        }
        cleanupState();
        formSnapshot.clear();
    }

    public static void writeBindingsToSnapshot(Map<String, String> snapshot,
                                               Map<Integer, DslScene.InterfaceSlotBinding> bindings) {
        List<String> oldKeys = new ArrayList<>();
        for (String key : snapshot.keySet()) {
            if (key.startsWith("slot_")) {
                oldKeys.add(key);
            }
        }
        for (String key : oldKeys) {
            snapshot.remove(key);
        }

        snapshot.put("slot_count", String.valueOf(bindings.size()));
        int i = 0;
        for (DslScene.InterfaceSlotBinding binding : bindings.values()) {
            if (binding == null || binding.slotIndex == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
                continue;
            }
            snapshot.put("slot_" + i + "_index", String.valueOf(binding.slotIndex));
            if (binding.slotX != null) {
                snapshot.put("slot_" + i + "_x", String.valueOf(binding.slotX));
            }
            if (binding.slotY != null) {
                snapshot.put("slot_" + i + "_y", String.valueOf(binding.slotY));
            }
            snapshot.put("slot_" + i + "_id", binding.ingredientId);
            if (binding.ingredientKind != null && !binding.ingredientKind.isBlank()) {
                snapshot.put("slot_" + i + "_kind", binding.ingredientKind);
            }
            if (binding.itemCount != null) {
                snapshot.put("slot_" + i + "_count", String.valueOf(binding.itemCount));
            }
            if (binding.itemComponents != null) {
                snapshot.put("slot_" + i + "_components", binding.itemComponents.toString());
            }
            i++;
        }
        snapshot.put("slot_count", String.valueOf(i));
    }

    public static LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> readBindingsFromSnapshot(Map<String, String> snapshot) {
        LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> result = new LinkedHashMap<>();
        if (snapshot == null || !snapshot.containsKey("slot_count")) {
            return result;
        }

        int count;
        try {
            count = Integer.parseInt(snapshot.getOrDefault("slot_count", "0"));
        } catch (NumberFormatException e) {
            return result;
        }

        for (int i = 0; i < count; i++) {
            String indexRaw = snapshot.get("slot_" + i + "_index");
            String id = snapshot.get("slot_" + i + "_id");
            if (indexRaw == null || id == null || id.isBlank()) {
                continue;
            }
            try {
                int slotIndex = Integer.parseInt(indexRaw);
                Integer slotX = parseOptionalInt(snapshot.get("slot_" + i + "_x"));
                Integer slotY = parseOptionalInt(snapshot.get("slot_" + i + "_y"));
                String kind = snapshot.get("slot_" + i + "_kind");
                DslScene.InterfaceSlotBinding binding = new DslScene.InterfaceSlotBinding(slotIndex, slotX, slotY, id, kind);
                binding.itemCount = parseOptionalInt(snapshot.get("slot_" + i + "_count"));
                String components = snapshot.get("slot_" + i + "_components");
                if (components != null) {
                    JsonElement parsed = JsonParser.parseString(components);
                    if (parsed.isJsonObject()) {
                        binding.itemComponents = parsed.getAsJsonObject();
                    }
                }
                result.put(slotIndex, binding);
            } catch (NumberFormatException | JsonParseException ignored) {
            }
        }
        return result;
    }

    private static void cleanupState() {
        active = false;
        slotBindings.clear();
        jeiViewport = null;
        context = null;
        previewSession = null;
        previewDirty = true;
    }

    @Nullable
    private static ResourceLocation getItemId() {
        if (context == null || context.scene().items == null || context.scene().items.isEmpty()) return null;
        return SceneItemBindings.firstItem(context.scene());
    }

    private static LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> copyBindings(
        Map<Integer, DslScene.InterfaceSlotBinding> bindings
    ) {
        LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> copy = new LinkedHashMap<>();
        for (DslScene.InterfaceSlotBinding binding : bindings.values()) {
            if (binding == null || binding.slotIndex == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
                continue;
            }
            copy.put(binding.slotIndex, binding.copy());
        }
        return copy;
    }

    @Nullable
    private static Integer parseOptionalInt(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
