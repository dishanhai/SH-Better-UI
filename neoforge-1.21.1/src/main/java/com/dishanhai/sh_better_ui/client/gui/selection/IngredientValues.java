package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/** 山海：NeoForge 原生素材工厂。创建和交回时各复制一次，保留物品与流体组件。 */
public final class IngredientValues {
    private static final AtomicLong REGISTRY_CHANGES = new AtomicLong();

    private IngredientValues() {}

    public static Ingredient item(ItemStack stack) {
        ItemStack value = Objects.requireNonNull(stack).copy();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(value.getItem());
        return new ItemValue(value, IngredientKey.of(Kind.ITEM, id, value.getComponentsPatch()));
    }

    public static Ingredient fluid(FluidStack stack) {
        FluidStack value = Objects.requireNonNull(stack).copy();
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(value.getFluid());
        return new FluidValue(value, IngredientKey.of(Kind.FLUID, id, value.getComponentsPatch()));
    }

    static long registryRevision() { return REGISTRY_CHANGES.get(); }

    static List<Ingredient> registryIngredients() {
        var rows = new ArrayList<Ingredient>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!stack.isEmpty()) rows.add(item(stack));
        }
        for (var fluid : BuiltInRegistries.FLUID) {
            var state = fluid.defaultFluidState();
            if (!state.isEmpty() && state.isSource()) rows.add(fluid(new FluidStack(fluid, FluidType.BUCKET_VOLUME)));
        }
        return List.copyOf(rows);
    }

    private static List<ResourceLocation> tagIds(Stream<? extends TagKey<?>> tags) {
        return tags.map(TagKey::location).sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    private static TooltipFlag tooltipFlag() {
        return Minecraft.getInstance().options.advancedItemTooltips
                ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
    }

    private interface Value extends Ingredient {
        IngredientKey identity();
        @Override
        default String key() { return identity().value(); }
        @Override
        default String modName() { return id().getNamespace(); }
        @Override
        default List<String> aliases() { return List.of(); }
        @Override
        default int width() { return 16; }
        @Override
        default int height() { return 16; }
    }

    private record ItemValue(ItemStack stack, IngredientKey identity) implements Value {
        @Override
        public String name() { return stack.getHoverName().getString(); }
        @Override
        public ResourceLocation id() { return BuiltInRegistries.ITEM.getKey(stack.getItem()); }
        @Override
        public Kind kind() { return Kind.ITEM; }
        @Override
        public boolean visible() { return !stack.isEmpty(); }
        @Override
        public List<ResourceLocation> tags() { return tagIds(stack.getTags()); }
        @Override
        public <T> Optional<T> copy(Class<T> type) {
            Objects.requireNonNull(type);
            return visible() && type.isInstance(stack) ? Optional.of(type.cast(stack.copy())) : Optional.empty();
        }
        @Override
        public void render(GuiGraphics graphics, int x, int y) {
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
        }
        @Override
        public List<Component> tooltip() {
            Minecraft client = Minecraft.getInstance();
            return stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, tooltipFlag());
        }
        @Override
        public void renderTooltip(GuiGraphics graphics, int x, int y) {
            graphics.renderTooltip(Minecraft.getInstance().font, stack, x, y);
        }
    }

    private record FluidValue(FluidStack stack, IngredientKey identity) implements Value {
        @Override
        public String name() { return stack.getHoverName().getString(); }
        @Override
        public ResourceLocation id() { return BuiltInRegistries.FLUID.getKey(stack.getFluid()); }
        @Override
        public Kind kind() { return Kind.FLUID; }
        @Override
        public boolean visible() { return !stack.isEmpty(); }
        @Override
        public List<ResourceLocation> tags() { return tagIds(stack.getTags()); }
        @Override
        public <T> Optional<T> copy(Class<T> type) {
            Objects.requireNonNull(type);
            return visible() && type.isInstance(stack) ? Optional.of(type.cast(stack.copy())) : Optional.empty();
        }
        @Override
        public void render(GuiGraphics graphics, int x, int y) {
            var extension = IClientFluidTypeExtensions.of(stack.getFluid());
            ResourceLocation texture = extension.getStillTexture(stack);
            if (texture == null) return;
            var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
            int tint = extension.getTintColor(stack);
            graphics.blit(x, y, 0, 16, 16, sprite, (tint >> 16 & 255) / 255f,
                    (tint >> 8 & 255) / 255f, (tint & 255) / 255f, (tint >>> 24) / 255f);
        }
        @Override
        public List<Component> tooltip() {
            return List.of(stack.getHoverName(),
                    Component.translatable("sh_better_ui.selection.fluid_amount", stack.getAmount()));
        }
    }

    @EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
    public static final class RegistryEvents {
        private RegistryEvents() {}

        @SubscribeEvent
        public static void onTagsUpdated(TagsUpdatedEvent event) { REGISTRY_CHANGES.incrementAndGet(); }
    }
}
