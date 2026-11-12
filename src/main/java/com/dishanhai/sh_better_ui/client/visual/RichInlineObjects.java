// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Native object snapshots belong to the view. Preview entities are never added to a world. */
final class RichInlineObjects {
    private static final int INGREDIENT_LIMIT = 128;
    private static final int ENTITY_LIMIT = 8;
    private final Map<RichObjectNode.Content, Optional<Ingredient>> ingredients = new LinkedHashMap<>();
    private final Map<UiId, Optional<LivingEntity>> entities = new LinkedHashMap<>();
    private Level level;

    Optional<Ingredient> ingredient(RichObjectNode.Content content) {
        if (content instanceof RichObjectNode.Item || content instanceof RichObjectNode.Block
                || content instanceof RichObjectNode.Fluid) {
            return remember(ingredients, INGREDIENT_LIMIT, content, RichInlineObjects::resolveIngredient);
        }
        return Optional.empty();
    }

    Optional<LivingEntity> entity(UiId type) {
        var current = Minecraft.getInstance().level;
        if (current != level) {
            entities.clear();
            level = current;
        }
        if (current == null) return Optional.empty();
        return remember(entities, ENTITY_LIMIT, type, id -> {
            try {
                return BuiltInRegistries.ENTITY_TYPE.getOptional(location(id)).map(value -> value.create(current))
                        .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).map(entity -> {
                            entity.setYRot(180f);
                            entity.yBodyRot = entity.yBodyRotO = 180f;
                            entity.yHeadRot = entity.yHeadRotO = 180f;
                            return entity;
                        });
            } catch (RuntimeException error) {
                ShBetterUi.LOGGER.debug("Could not create inline entity preview {}", id, error);
                return Optional.empty();
            }
        });
    }

    void disableEntity(UiId type) { entities.put(type, Optional.empty()); }

    void clear() {
        ingredients.clear();
        entities.clear();
        level = null;
    }

    private static Optional<Ingredient> resolveIngredient(RichObjectNode.Content content) {
        if (content instanceof RichObjectNode.Item item) {
            return BuiltInRegistries.ITEM.getOptional(location(item.item())).map(value -> {
                var stack = value.getDefaultInstance();
                stack.setCount(item.count());
                return stack;
            }).filter(stack -> !stack.isEmpty()).map(IngredientValues::item);
        }
        if (content instanceof RichObjectNode.Block block) {
            return BuiltInRegistries.BLOCK.getOptional(location(block.block()))
                    .map(value -> value.asItem().getDefaultInstance())
                    .filter(stack -> !stack.isEmpty()).map(IngredientValues::item);
        }
        var fluid = (RichObjectNode.Fluid) content;
        return BuiltInRegistries.FLUID.getOptional(location(fluid.fluid()))
                .filter(value -> {
                    var state = value.defaultFluidState();
                    return !state.isEmpty() && state.isSource();
                }).map(RichInlinePlatform::fluid);
    }

    private static ResourceLocation location(UiId id) {
        return ResourceLocation.tryParse(id.toString());
    }

    private static <K, V> V remember(Map<K, V> cache, int capacity, K key,
                                    java.util.function.Function<K, V> resolve) {
        var found = cache.get(key);
        if (found != null) return found;
        var value = resolve.apply(key);
        if (cache.size() >= capacity) cache.remove(cache.keySet().iterator().next());
        cache.put(key, value);
        return value;
    }
}
