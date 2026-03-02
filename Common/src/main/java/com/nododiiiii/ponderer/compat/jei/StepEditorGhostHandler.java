package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.ui.AbstractStepEditorScreen;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;
import java.util.Optional;

public class StepEditorGhostHandler implements IGhostIngredientHandler<AbstractStepEditorScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            AbstractStepEditorScreen gui,
            ITypedIngredient<I> ingredient,
            boolean doStart) {

        IdFieldMode mode = PondererJeiPlugin.getActiveMode();
        if (mode == null) return List.of();

        HintableTextFieldWidget targetField = gui.getJeiTargetField();
        if (targetField == null) return List.of();

        Optional<ItemStack> stackOpt = ingredient.getItemStack();
        if (stackOpt.isEmpty()) return List.of();

        if (!isCompatible(stackOpt.get(), mode)) return List.of();

        Rect2i area = new Rect2i(
                targetField.getX(), targetField.getY(),
                targetField.getWidth(), targetField.getHeight()
        );

        return List.of(new Target<I>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I ing) {
                if (ing instanceof ItemStack stack) {
                    String id = resolveId(stack, mode);
                    if (id != null) {
                        targetField.setValue(id);
                    }
                }
            }
        });
    }

    @Override
    public void onComplete() {
    }

    static String resolveId(ItemStack stack, IdFieldMode mode) {
        return switch (mode) {
            case ITEM, INGREDIENT -> {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                yield key.toString();
            }
            case BLOCK -> {
                if (stack.getItem() instanceof BlockItem bi) {
                    yield BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString();
                }
                yield null;
            }
            case ENTITY -> {
                if (stack.getItem() instanceof SpawnEggItem egg) {
                    var type = egg.getType(stack.getTag());
                    yield BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                }
                // Boats: item names (oak_boat) don't match entity type (boat) in 1.21.1
                if (stack.getItem() instanceof BoatItem) {
                    ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    String entityName = itemKey.getPath().contains("chest") ? "chest_boat" : "boat";
                    yield new ResourceLocation(itemKey.getNamespace(), entityName).toString();
                }
                // Fallback: check if item registry name matches an entity type
                // (works for minecarts, armor stands, etc.)
                ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(itemKey)) {
                    yield itemKey.toString();
                }
                yield null;
            }
        };
    }

    static boolean isCompatible(ItemStack stack, IdFieldMode mode) {
        return resolveId(stack, mode) != null;
    }
}
