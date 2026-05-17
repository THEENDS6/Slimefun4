package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.machine.ManualMachineOutput;
import cc.theends6.sfx.internal.machine.ManualMachineRecipe;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService.CraftingTransactionResult;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService.CraftingTransactionStatus;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService.IngredientRequest;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.RecipeChoice.ExactChoice;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxAutoCrafterRecipeProvider implements SfxElectricRecipeProvider {
    static final int WORK_TICKS = 10;

    private enum Kind {
        VANILLA,
        ENHANCED,
        ARMOR
    }

    private final Kind kind;
    private final SfxVirtualContainerService virtualContainers;
    private final SfxItems items;
    private final List<ManualMachineRecipe> manualRecipes;

    static SfxAutoCrafterRecipeProvider vanilla(SfxVirtualContainerService virtualContainers, SfxItems items) {
        return new SfxAutoCrafterRecipeProvider(Kind.VANILLA, virtualContainers, items, List.of());
    }

    static SfxAutoCrafterRecipeProvider enhanced(SfxVirtualContainerService virtualContainers, SfxItems items, List<ManualMachineRecipe> recipes) {
        return new SfxAutoCrafterRecipeProvider(Kind.ENHANCED, virtualContainers, items, recipes);
    }

    static SfxAutoCrafterRecipeProvider armor(SfxVirtualContainerService virtualContainers, SfxItems items, List<ManualMachineRecipe> recipes) {
        return new SfxAutoCrafterRecipeProvider(Kind.ARMOR, virtualContainers, items, recipes);
    }

    private SfxAutoCrafterRecipeProvider(Kind kind, SfxVirtualContainerService virtualContainers, SfxItems items, List<ManualMachineRecipe> manualRecipes) {
        this.kind = kind;
        this.virtualContainers = virtualContainers;
        this.items = items;
        this.manualRecipes = List.copyOf(manualRecipes == null ? List.of() : manualRecipes);
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return List.of();
    }

    @Override
    public boolean hasSpecialTick() {
        return true;
    }

    @Override
    public int specialTickIntervalTicks() {
        return 1;
    }

    @Override
    public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        if (!state.enabled() || state.activeRecipeKey() == null || state.activeRecipeKey().isBlank()) {
            return 0;
        }
        if (state.progressWork() > 0) {
            return definition.energyConsumptionPerTick();
        }
        Location below = location == null ? null : location.clone().subtract(0, 1, 0);
        if (below == null) {
            return 0;
        }
        CraftRequest request = craftRequest(plugin, state.activeRecipeKey());
        if (request.status != SfxElectricMachineRenderStatus.WORKING) {
            return 0;
        }
        CraftingTransactionResult check = virtualContainers.checkCraftingTransaction(below, request.ingredients, request.outputs);
        return mapCraftingStatus(check.status()) == SfxElectricMachineRenderStatus.WORKING ? definition.energyConsumptionPerTick() : 0;
    }

    @Override
    public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems ignored, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, SfxMachineTickContext context) {
        String selected = state.activeRecipeKey();
        if (selected == null || selected.isBlank()) {
            state.progressWork(0);
            state.activeBaseTicks(WORK_TICKS);
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_RECIPE, false);
        }
        Location below = location == null ? null : location.clone().subtract(0, 1, 0);
        if (below == null) {
            state.progressWork(0);
            state.activeBaseTicks(WORK_TICKS);
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        CraftRequest request = craftRequest(plugin, selected);
        if (request.status != SfxElectricMachineRenderStatus.WORKING) {
            state.progressWork(0);
            state.activeBaseTicks(WORK_TICKS);
            return SfxElectricMachineTickResult.status(request.status, true);
        }
        CraftingTransactionResult precheck = virtualContainers.checkCraftingTransaction(below, request.ingredients, request.outputs);
        SfxElectricMachineRenderStatus precheckStatus = mapCraftingStatus(precheck.status());
        if (precheckStatus != SfxElectricMachineRenderStatus.WORKING) {
            if (precheckStatus != SfxElectricMachineRenderStatus.PAUSED) {
                state.progressWork(0);
            }
            state.activeBaseTicks(WORK_TICKS);
            return SfxElectricMachineTickResult.status(precheckStatus, true);
        }
        int energyPerTick = Math.max(0, definition.energyConsumptionPerTick());
        int elapsed = Math.max(1, context == null ? 1 : context.elapsedTicksInt());
        int remainingWork = Math.max(0, WORK_TICKS - state.progressWork());
        int progressTicks = Math.min(elapsed, Math.max(1, remainingWork));
        if (energyPerTick > 0) {
            progressTicks = Math.min(progressTicks, state.storedEnergy() / energyPerTick);
        }
        if (progressTicks <= 0) {
            state.activeBaseTicks(WORK_TICKS);
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        int consumed = energyPerTick * progressTicks;
        if (consumed > 0) {
            state.storedEnergy(state.storedEnergy() - consumed);
        }
        state.activeBaseTicks(WORK_TICKS);
        int progress = Math.min(WORK_TICKS, state.progressWork() + progressTicks);
        state.progressWork(progress);
        if (progress < WORK_TICKS) {
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, consumed, true);
        }
        state.progressWork(0);
        CraftingTransactionResult finish = virtualContainers.commitCraftingTransaction(below, request.ingredients, request.outputs);
        SfxElectricMachineRenderStatus finishStatus = mapCraftingStatus(finish.status());
        return SfxElectricMachineTickResult.changed(finishStatus, consumed, true);
    }

    private SfxElectricMachineRenderStatus mapCraftingStatus(CraftingTransactionStatus status) {
        return switch (status) {
            case SUCCESS -> SfxElectricMachineRenderStatus.WORKING;
            case NO_CONTAINER -> SfxElectricMachineRenderStatus.NO_TARGET;
            case MISSING_INPUT -> SfxElectricMachineRenderStatus.NO_INPUT;
            case OUTPUT_FULL -> SfxElectricMachineRenderStatus.OUTPUT_FULL;
            case BUSY -> SfxElectricMachineRenderStatus.PAUSED;
        };
    }

    boolean canSelect(JavaPlugin plugin, String selected) {
        return switch (kind) {
            case VANILLA -> findVanillaRecipe(plugin, selected) != null;
            case ENHANCED, ARMOR -> findManualRecipe(selected) != null;
        };
    }

    String selectionKeyFor(JavaPlugin plugin, ItemStack hand) {
        if (isEmpty(hand)) {
            return null;
        }
        if (kind == Kind.VANILLA) {
            Recipe recipe = findVanillaRecipeByResult(plugin, hand);
            return recipe instanceof Keyed keyed ? keyed.getKey().toString() : null;
        }
        String itemId = items.readMarker(hand).map(marker -> marker.itemId()).orElse(null);
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return findManualRecipe(itemId) == null ? null : itemId;
    }

    List<SfxAutoCrafterRecipeChoice> selectionChoices(JavaPlugin plugin, ItemStack hand) {
        if (isEmpty(hand)) {
            return List.of();
        }
        if (kind == Kind.VANILLA) {
            List<SfxAutoCrafterRecipeChoice> choices = new ArrayList<>();
            for (Recipe recipe : findVanillaRecipesByResult(plugin, hand)) {
                if (recipe instanceof Keyed keyed) {
                    choices.add(new SfxAutoCrafterRecipeChoice(keyed.getKey().toString(), previewInputs(recipe), recipe.getResult()));
                }
            }
            return choices;
        }
        String itemId = items.readMarker(hand).map(marker -> marker.itemId()).orElse(null);
        ManualMachineRecipe recipe = findManualRecipe(itemId);
        return recipe == null ? List.of() : List.of(manualChoice(itemId, recipe));
    }

    SfxAutoCrafterRecipeChoice choiceForKey(JavaPlugin plugin, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (kind == Kind.VANILLA) {
            Recipe recipe = findVanillaRecipe(plugin, key);
            return recipe == null ? null : new SfxAutoCrafterRecipeChoice(key, previewInputs(recipe), recipe.getResult());
        }
        ManualMachineRecipe recipe = findManualRecipe(key);
        return recipe == null ? null : manualChoice(key, recipe);
    }

    private SfxAutoCrafterRecipeChoice manualChoice(String key, ManualMachineRecipe recipe) {
        ItemStack[] preview = new ItemStack[9];
        List<SfxRecipeSlot> inputs = recipe.input();
        for (int i = 0; i < Math.min(9, inputs.size()); i++) {
            preview[i] = previewSlot(inputs.get(i));
        }
        ItemStack output = recipe.fixedOutputs().isEmpty() ? null : recipe.fixedOutputs().get(0).create(items);
        return new SfxAutoCrafterRecipeChoice(key, preview, output);
    }

    private ItemStack previewSlot(SfxRecipeSlot slot) {
        if (slot == null || slot.isEmpty()) {
            return null;
        }
        ItemStack stack = slot.isSfxItem() ? items.create(slot.sfxItemId(), slot.amount()) : new ItemStack(slot.material(), slot.amount());
        return stack == null ? null : stack;
    }

    private ItemStack[] previewInputs(Recipe recipe) {
        ItemStack[] preview = new ItemStack[9];
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> map = shaped.getChoiceMap();
            for (int row = 0; row < Math.min(3, shape.length); row++) {
                String line = shape[row];
                for (int col = 0; col < Math.min(3, line.length()); col++) {
                    preview[row * 3 + col] = previewChoice(map.get(line.charAt(col)));
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = shapeless.getChoiceList();
            for (int i = 0; i < Math.min(9, choices.size()); i++) {
                preview[i] = previewChoice(choices.get(i));
            }
        }
        return preview;
    }

    private ItemStack previewChoice(RecipeChoice choice) {
        if (choice == null) {
            return null;
        }
        if (choice instanceof ExactChoice exact && !exact.getChoices().isEmpty()) {
            ItemStack stack = exact.getChoices().get(0);
            return stack == null ? null : stack.clone();
        }
        if (choice instanceof MaterialChoice materialChoice && !materialChoice.getChoices().isEmpty()) {
            return new ItemStack(materialChoice.getChoices().get(0));
        }
        return new ItemStack(Material.PAPER);
    }

    private CraftRequest craftRequest(JavaPlugin plugin, String selected) {
        return switch (kind) {
            case VANILLA -> vanillaCraftRequest(plugin, selected);
            case ENHANCED, ARMOR -> manualCraftRequest(selected);
        };
    }

    private CraftRequest vanillaCraftRequest(JavaPlugin plugin, String selected) {
        Recipe recipe = findVanillaRecipe(plugin, selected);
        if (recipe == null) {
            return CraftRequest.status(SfxElectricMachineRenderStatus.NO_RECIPE);
        }
        List<RecipeChoice> choices = recipeChoices(recipe);
        if (choices.isEmpty() || isEmpty(recipe.getResult())) {
            return CraftRequest.status(SfxElectricMachineRenderStatus.NO_RECIPE);
        }
        List<IngredientRequest> ingredients = new ArrayList<>();
        for (RecipeChoice choice : choices) {
            if (choice == null) {
                continue;
            }
            ingredients.add(new IngredientRequest(choice::test, 1));
        }
        return new CraftRequest(SfxElectricMachineRenderStatus.WORKING, ingredients, List.of(recipe.getResult().clone()));
    }

    private CraftRequest manualCraftRequest(String selected) {
        ManualMachineRecipe recipe = findManualRecipe(selected);
        if (recipe == null) {
            return CraftRequest.status(SfxElectricMachineRenderStatus.NO_RECIPE);
        }
        List<IngredientRequest> ingredients = new ArrayList<>();
        for (SfxRecipeSlot slot : recipe.input()) {
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            ingredients.add(new IngredientRequest(stack -> matchesSlot(stack, slot), Math.max(1, slot.amount())));
        }
        List<ItemStack> outputs = new ArrayList<>();
        for (ManualMachineOutput output : recipe.fixedOutputs()) {
            ItemStack stack = output.create(items);
            if (!isEmpty(stack)) {
                outputs.add(stack);
            }
        }
        if (outputs.isEmpty()) {
            return CraftRequest.status(SfxElectricMachineRenderStatus.NO_RECIPE);
        }
        return new CraftRequest(SfxElectricMachineRenderStatus.WORKING, ingredients, outputs);
    }

    private ManualMachineRecipe findManualRecipe(String selected) {
        if (selected == null || selected.isBlank()) {
            return null;
        }
        for (ManualMachineRecipe recipe : manualRecipes) {
            for (ManualMachineOutput output : recipe.fixedOutputs()) {
                if (output.isSfxItem() && selected.equals(output.sfxItemId())) {
                    return recipe;
                }
            }
        }
        return null;
    }

    private Recipe findVanillaRecipe(JavaPlugin plugin, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey != null) {
            Recipe exact = plugin.getServer().getRecipe(namespacedKey);
            if (exact instanceof ShapedRecipe || exact instanceof ShapelessRecipe) {
                return exact;
            }
        }
        Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof Keyed keyed && keyed.getKey().toString().equals(key) && (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
                return recipe;
            }
        }
        return null;
    }

    private List<Recipe> findVanillaRecipesByResult(JavaPlugin plugin, ItemStack hand) {
        if (isEmpty(hand)) {
            return List.of();
        }
        ItemStack probe = hand.clone();
        probe.setAmount(1);
        List<Recipe> recipes = new ArrayList<>();
        Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
                continue;
            }
            ItemStack result = recipe.getResult();
            if (isEmpty(result)) {
                continue;
            }
            ItemStack normalized = result.clone();
            normalized.setAmount(1);
            if (normalized.isSimilar(probe)) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    private Recipe findVanillaRecipeByResult(JavaPlugin plugin, ItemStack hand) {
        ItemStack probe = hand.clone();
        probe.setAmount(1);
        Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
                continue;
            }
            ItemStack result = recipe.getResult();
            if (isEmpty(result)) {
                continue;
            }
            ItemStack normalized = result.clone();
            normalized.setAmount(1);
            if (normalized.isSimilar(probe)) {
                return recipe;
            }
        }
        return null;
    }

    private List<RecipeChoice> recipeChoices(Recipe recipe) {
        List<RecipeChoice> choices = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> map = shaped.getChoiceMap();
            for (String row : shape) {
                for (int i = 0; i < row.length(); i++) {
                    RecipeChoice choice = map.get(row.charAt(i));
                    if (choice != null) {
                        choices.add(choice);
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            choices.addAll(shapeless.getChoiceList());
        }
        return choices;
    }

    private boolean matchesSlot(ItemStack stack, SfxRecipeSlot slot) {
        if (slot.isSfxItem()) {
            return items.readMarker(stack).map(marker -> marker.itemId().equals(slot.sfxItemId())).orElse(false);
        }
        if (items.isSfxItem(stack)) {
            return false;
        }
        return stack.getType() == slot.material();
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private record CraftRequest(SfxElectricMachineRenderStatus status, List<IngredientRequest> ingredients, List<ItemStack> outputs) {
        static CraftRequest status(SfxElectricMachineRenderStatus status) {
            return new CraftRequest(status, List.of(), List.of());
        }
    }
}
