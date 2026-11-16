package com.dishanhai.sh_better_ui.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/** 山海：仅由 JEI 加载的 Forge／NeoForge 可选插件。 */
@JeiPlugin
public final class JeiPluginBridge implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.tryParse("sh_better_ui:integration");
    private static final AtomicLong NEXT_INGREDIENT_KEY = new AtomicLong();

    private Backend backend;

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                return JeiIntegration.extraAreas();
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        onRuntimeUnavailable();
        backend = new Backend(runtime);
        JeiIntegration.install(backend);
    }

    @Override
    public void onRuntimeUnavailable() {
        if (backend == null) return;
        backend.active = false;
        JeiIntegration.uninstall(backend);
        backend = null;
    }

    private record Identity(JeiIntegration.Kind kind, Object uid) {}

    private static final class Backend implements JeiIntegration.Backend,
            IIngredientManager.IIngredientListener, IIngredientVisibility.IListener {
        private final IJeiRuntime runtime;
        private final IIngredientManager ingredients;
        private final IIngredientVisibility visibility;
        private final IModIdHelper modIds;
        private final AtomicLong revision = new AtomicLong();
        private Language language = Language.getInstance();
        private long snapshotRevision = -1;
        private Map<Identity, String> keys = Map.of();
        private List<JeiIntegration.Ingredient> snapshot = List.of();
        private volatile boolean active = true;

        private Backend(IJeiRuntime runtime) {
            this.runtime = runtime;
            this.ingredients = runtime.getIngredientManager();
            this.visibility = runtime.getJeiHelpers().getIngredientVisibility();
            this.modIds = runtime.getJeiHelpers().getModIdHelper();
            ingredients.registerIngredientListener(this);
            visibility.registerListener(this);
        }

        @Override
        public long revision() {
            Language currentLanguage = Language.getInstance();
            if (language != currentLanguage) {
                language = currentLanguage;
                revision.incrementAndGet();
            }
            return revision.get();
        }

        @Override
        public List<JeiIntegration.Ingredient> ingredients() {
            if (!active) return List.of();
            long currentRevision = revision();
            if (snapshotRevision != currentRevision) {
                var rows = new ArrayList<JeiIntegration.Ingredient>();
                var nextKeys = new HashMap<Identity, String>();
                collect(VanillaTypes.ITEM_STACK, JeiIntegration.Kind.ITEM, rows, nextKeys);
                collect(runtime.getJeiHelpers().getPlatformFluidHelper().getFluidIngredientType(),
                        JeiIntegration.Kind.FLUID, rows, nextKeys);
                keys = nextKeys;
                snapshot = List.copyOf(rows);
                snapshotRevision = currentRevision;
            }
            return snapshot;
        }

        @Override
        public List<ItemStack> bookmarkedItems() {
            if (!active) return List.of();
            // JEI exposes hover access publicly, but not the complete bookmark list.
            // Keep this optional adapter isolated from the UUK screen and skip recipe bookmarks.
            try {
                Object overlay = runtime.getBookmarkOverlay();
                var listField = overlay.getClass().getDeclaredField("bookmarkList");
                listField.setAccessible(true);
                Object bookmarks = listField.get(overlay);
                var entriesField = bookmarks.getClass().getDeclaredField("bookmarksList");
                entriesField.setAccessible(true);
                var result = new ArrayList<ItemStack>();
                if (entriesField.get(bookmarks) instanceof List<?> entries) {
                    for (Object entry : entries) {
                        try {
                            Object typed = entry.getClass().getMethod("getIngredient").invoke(entry);
                            if (typed instanceof ITypedIngredient<?> ingredient
                                    && ingredient.getIngredient() instanceof ItemStack stack && !stack.isEmpty()) result.add(stack.copy());
                        } catch (ReflectiveOperationException ignored) { /* Other bookmark types have no ingredient. */ }
                    }
                }
                return List.copyOf(result);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                return List.of();
            }
        }

        private <T> void collect(IIngredientType<T> type, JeiIntegration.Kind kind,
                                 List<JeiIntegration.Ingredient> rows, Map<Identity, String> nextKeys) {
            if (!ingredients.getRegisteredIngredientTypes().contains(type)) return;
            IIngredientHelper<T> helper = ingredients.getIngredientHelper(type);
            IIngredientRenderer<T> renderer = ingredients.getIngredientRenderer(type);
            for (ITypedIngredient<T> typed : ingredients.getAllTypedIngredients(type)) {
                // The native UID can be an object. Its equality, rather than its string form, identifies variants.
                var identity = new Identity(kind, helper.getUid(typed, UidContext.Ingredient));
                if (nextKeys.containsKey(identity)) continue;
                String key = keys.get(identity);
                if (key == null) key = kind.name() + ':' + NEXT_INGREDIENT_KEY.getAndIncrement();
                nextKeys.put(identity, key);
                if (!visibility.isIngredientVisible(typed)) continue;

                T value = typed.getIngredient();
                ResourceLocation id = helper.getResourceLocation(value);
                String name = helper.getDisplayName(value);
                String modName = modIds.getModNameForModId(helper.getDisplayModId(value));
                rows.add(new IngredientEntry<>(this, typed, helper, renderer, key, name.isBlank() ? id.toString() : name,
                        id, modName, List.copyOf(ingredients.getIngredientAliases(typed)), kind));
            }
        }

        @Override
        public List<JeiIntegration.Recipe> recipes(JeiIntegration.Ingredient ingredient,
                                                   JeiIntegration.Direction direction, int limit) {
            if (limit <= 0) return List.of();
            IngredientEntry<?> entry = resolve(ingredient);
            if (entry == null) return List.of();
            List<IFocus<?>> focus = focuses(entry.typed(), direction);
            IRecipeManager manager = runtime.getRecipeManager();
            return manager.createRecipeCategoryLookup().limitFocus(focus).get()
                    .flatMap(category -> recipes(manager, category, focus, entry))
                    .limit(limit).toList();
        }

        private <T> Stream<JeiIntegration.Recipe> recipes(IRecipeManager manager, IRecipeCategory<T> category,
                                                          List<IFocus<?>> focus, IngredientEntry<?> ingredient) {
            return manager.createRecipeLookup(category.getRecipeType()).limitFocus(focus).get()
                    .map(recipe -> new RecipeEntry<>(ingredient, manager, category, recipe, focus));
        }

        @Override
        public boolean open(JeiIntegration.Ingredient ingredient, JeiIntegration.Direction direction) {
            IngredientEntry<?> entry = resolve(ingredient);
            if (entry == null) return false;
            List<IFocus<?>> focus = focuses(entry.typed(), direction);
            if (runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(focus).get().findAny().isEmpty()) {
                return false;
            }
            runtime.getRecipesGui().show(focus);
            return true;
        }

        private boolean owns(IngredientEntry<?> entry) {
            return entry.owner() == this && entry.visible();
        }

        private IngredientEntry<?> resolve(JeiIntegration.Ingredient ingredient) {
            if (ingredient instanceof IngredientEntry<?> entry) return owns(entry) ? entry : null;
            if (!active || !ingredient.visible()) return null;
            return switch (ingredient.kind()) {
                case ITEM -> resolve(ingredient, VanillaTypes.ITEM_STACK);
                case FLUID -> resolve(ingredient, runtime.getJeiHelpers().getPlatformFluidHelper().getFluidIngredientType());
            };
        }

        private <T> IngredientEntry<T> resolve(JeiIntegration.Ingredient ingredient, IIngredientType<T> type) {
            if (!ingredients.getRegisteredIngredientTypes().contains(type)) return null;
            var typed = ingredient.copy(type.getIngredientClass())
                    .flatMap(value -> ingredients.createTypedIngredient(type, value));
            if (typed.isEmpty() || !visibility.isIngredientVisible(typed.get())) return null;
            return new IngredientEntry<>(this, typed.get(), ingredients.getIngredientHelper(type),
                    ingredients.getIngredientRenderer(type), ingredient.key(), ingredient.name(), ingredient.id(),
                    ingredient.modName(), ingredient.aliases(), ingredient.kind());
        }

        private List<IFocus<?>> focuses(ITypedIngredient<?> typed, JeiIntegration.Direction direction) {
            var factory = runtime.getJeiHelpers().getFocusFactory();
            return switch (direction) {
                case OBTAINING -> List.of(factory.createFocus(RecipeIngredientRole.OUTPUT, typed));
                case USES -> List.of(factory.createFocus(RecipeIngredientRole.INPUT, typed),
                        factory.createFocus(RecipeIngredientRole.CATALYST, typed));
            };
        }

        @Override
        public <T> void onIngredientsAdded(IIngredientHelper<T> helper, Collection<ITypedIngredient<T>> added) {
            revision.incrementAndGet();
        }

        @Override
        public <T> void onIngredientsRemoved(IIngredientHelper<T> helper, Collection<ITypedIngredient<T>> removed) {
            revision.incrementAndGet();
        }

        // JEI 19 adds this callback; the matching method also compiles against JEI 15.
        public <T> void onIngredientsUpdated(IIngredientHelper<T> helper, Collection<ITypedIngredient<T>> updated) {
            revision.incrementAndGet();
        }

        @Override
        public <T> void onIngredientVisibilityChanged(ITypedIngredient<T> ingredient, boolean visible) {
            revision.incrementAndGet();
        }

        @Override
        public <T> void onIngredientsVisibilityChanged(Collection<ITypedIngredient<T>> changed, boolean visible) {
            revision.incrementAndGet();
        }
    }

    private record IngredientEntry<T>(Backend owner, ITypedIngredient<T> typed, IIngredientHelper<T> helper,
                                      IIngredientRenderer<T> renderer,
                                      String key, String name, ResourceLocation id, String modName, List<String> aliases,
                                      JeiIntegration.Kind kind) implements JeiIntegration.Ingredient {
        @Override
        public List<ResourceLocation> tags() {
            return visible() ? helper.getTagStream(typed.getIngredient())
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList() : List.of();
        }

        @Override
        public <V> Optional<V> copy(Class<V> type) {
            java.util.Objects.requireNonNull(type);
            if (!visible() || !type.isInstance(typed.getIngredient())) return Optional.empty();
            return Optional.of(type.cast(helper.copyIngredient(typed.getIngredient())));
        }

        @Override
        public boolean visible() {
            return owner.active && owner.visibility.isIngredientVisible(typed);
        }

        @Override
        public int width() {
            return renderer.getWidth();
        }

        @Override
        public int height() {
            return renderer.getHeight();
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y) {
            if (visible()) renderer.render(graphics, typed.getIngredient(), x, y);
        }

        @Override
        public List<Component> tooltip() {
            if (!visible()) return List.of();
            return tooltipLines();
        }

        @Override
        public void renderTooltip(GuiGraphics graphics, int x, int y) {
            if (!visible()) return;
            Minecraft client = Minecraft.getInstance();
            Font font = renderer.getFontRenderer(client, typed.getIngredient());
            if (font == null) font = client.font;
            ItemStack stack = typed.getItemStack().orElse(ItemStack.EMPTY);
            graphics.renderTooltip(font, tooltipLines(), stack.getTooltipImage(), stack, x, y);
        }

        private List<Component> tooltipLines() {
            var lines = new ArrayList<>(JeiPlatformTooltip.get(renderer, typed.getIngredient()));
            owner.modIds.getModNameForTooltip(typed).ifPresent(lines::add);
            return List.copyOf(lines);
        }
    }

    private record RecipeEntry<T>(IngredientEntry<?> ingredient, IRecipeManager manager, IRecipeCategory<T> category,
                                  T recipe, List<IFocus<?>> focuses) implements JeiIntegration.Recipe {
        @Override
        public Component title() {
            return category.getTitle();
        }

        @Override
        public Optional<JeiIntegration.RecipeLayout> layout() {
            if (!ingredient.visible()) return Optional.empty();
            var factory = ingredient.owner().runtime.getJeiHelpers().getFocusFactory();
            return manager.createRecipeLayoutDrawable(category, recipe, factory.createFocusGroup(focuses))
                    .map(drawable -> new Layout(ingredient, drawable));
        }
    }

    private record Layout(IngredientEntry<?> ingredient, IRecipeLayoutDrawable<?> drawable)
            implements JeiIntegration.RecipeLayout {
        @Override
        public int width() {
            return drawable.getRect().getWidth();
        }

        @Override
        public int height() {
            return drawable.getRect().getHeight();
        }

        @Override public int outerWidth() { return drawable.getRectWithBorder().getWidth(); }
        @Override public int outerHeight() { return drawable.getRectWithBorder().getHeight(); }
        @Override public int contentOffsetX() { return drawable.getRect().getX() - drawable.getRectWithBorder().getX(); }
        @Override public int contentOffsetY() { return drawable.getRect().getY() - drawable.getRectWithBorder().getY(); }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
            if (!ingredient.visible()) return;
            Rect2i bounds = drawable.getRect();
            if (bounds.getX() != x || bounds.getY() != y) drawable.setPosition(x, y);
            drawable.drawRecipe(graphics, mouseX, mouseY);
        }

        @Override
        public void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY) {
            if (ingredient.visible() && drawable.isMouseOver(mouseX, mouseY)) {
                drawable.drawOverlays(graphics, mouseX, mouseY);
            }
        }

        @Override
        public void tick() {
            if (ingredient.visible()) drawable.tick();
        }
    }
}
