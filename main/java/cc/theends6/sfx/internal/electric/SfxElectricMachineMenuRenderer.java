package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxElectricMachineMenuRenderer {
    private static final int DISPLAY_SLOT = 22;
    private static final int[] INPUT_SLOTS = {19, 20};
    private static final int[] OUTPUT_SLOTS = {24, 25};
    private static final int[] BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 31, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] BORDER_IN = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private static final int[] BORDER_OUT = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxPlayerDataService profiles;

    SfxElectricMachineMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.profiles = profiles;
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        fillInventoryFrame(inventory);
        inventory.setItem(DISPLAY_SLOT, progressIcon(viewerId, definition, state, recipe, status));
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            inventory.setItem(INPUT_SLOTS[slot], state.input(slot) == null ? null : state.input(slot).toItemStack(items));
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            inventory.setItem(OUTPUT_SLOTS[slot], state.output(slot) == null ? null : state.output(slot).toItemStack(items));
        }
    }

    private void fillInventoryFrame(Inventory inventory) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack inputBorder = namedItem(
                Material.CYAN_STAINED_GLASS_PANE,
                localization.component("electric-ui.input.name", "<aqua>Input</aqua>"),
                List.of(localization.component("electric-ui.input.lore", "<gray>Place items here.</gray>")));
        ItemStack outputBorder = namedItem(
                Material.ORANGE_STAINED_GLASS_PANE,
                localization.component("electric-ui.output.name", "<gold>Output</gold>"),
                List.of(localization.component("electric-ui.output.lore", "<gray>Take finished items here.</gray>")));
        for (int slot : BORDER) {
            inventory.setItem(slot, filler);
        }
        for (int slot : BORDER_IN) {
            inventory.setItem(slot, inputBorder);
        }
        for (int slot : BORDER_OUT) {
            inventory.setItem(slot, outputBorder);
        }
    }

    private ItemStack progressIcon(UUID viewerId, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        return switch (status) {
            case WORKING -> buildProgressIcon(viewerId, definition, state, recipe, false);
            case BLOCKED_OUTPUT -> recipe == null
                    ? namedItem(
                            Material.RED_STAINED_GLASS_PANE,
                            localization.component("electric-ui.blocked.name", "<red>Blocked</red>"),
                            List.of(localization.component("electric-ui.blocked.lore", "<gray>Output is full. Free a slot to continue.</gray>")))
                    : buildProgressIcon(viewerId, definition, state, recipe, true);
            case OUTPUT_FULL -> namedItem(
                    Material.RED_STAINED_GLASS_PANE,
                    localization.component("electric-ui.output-full.name", "<red>Output Full</red>"),
                    List.of(localization.component("electric-ui.output-full.lore", "<gray>Free an output slot to continue.</gray>")));
            case NO_POWER -> recipe == null
                    ? namedItem(
                            Material.RED_STAINED_GLASS_PANE,
                            localization.component("electric-ui.no-power.name", "<red>No Power</red>"),
                            List.of(localization.component("electric-ui.no-power.lore", "<gray>Charge this machine to continue.</gray>")))
                    : buildNoPowerIcon(definition, state, recipe);
            case NO_RECIPE -> namedItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    localization.component("electric-ui.no-recipe.name", "<gray>No Recipe</gray>"),
                    List.of(localization.component("electric-ui.no-recipe.lore", "<gray>The current input has no matching recipe.</gray>")));
            case IDLE -> namedItem(
                    Material.BLACK_STAINED_GLASS_PANE,
                    localization.component("electric-ui.idle.name", "<gray>Idle</gray>"),
                    List.of(localization.component("electric-ui.idle.lore", "<gray>Waiting for input.</gray>")));
        };
    }

    private ItemStack buildProgressIcon(UUID viewerId, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe, boolean blocked) {
        int totalWork = requiredWork(recipe);
        int currentWork = blocked && state.hasPendingOutput() ? totalWork : Math.min(totalWork, state.progressWork());
        int remainingWork = Math.max(0, totalWork - currentWork);
        int remainingTicks = blocked ? 0 : (int) Math.ceil(remainingWork / (double) Math.max(1, definition.speed()));
        boolean extendedUi = isExtendedUiEnabled(viewerId);
        ItemStack stack = new ItemStack(definition.progressMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof Damageable damageable && stack.getType().getMaxDurability() > 0) {
            damageable.setDamage(progressDamage(stack, remainingWork, totalWork));
        }
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(progressBarLine(currentWork, totalWork));
        lore.add(Component.text(" "));
        lore.add(localization.component(
                blocked ? "electric-ui.blocked.time-left" : "electric-ui.progress.time-left",
                "<gray>{time}</gray>",
                Map.of("time", formatTimeLeft(Math.max(0, remainingTicks / 20)))));
        if (extendedUi) {
            lore.add(Component.empty());
            lore.add(localization.component(
                    "electric-ui.progress.recipe",
                    "<gray>Recipe: </gray><white>{recipe}</white>",
                    Map.of("recipe", displayStackName(recipe.output()))));
            lore.add(localization.component(
                    "electric-ui.progress.speed",
                    "<gray>Speed: </gray><aqua>{speed}x</aqua>",
                    Map.of("speed", definition.speed())));
            lore.add(localization.component(
                    "electric-ui.progress.work",
                    "<gray>Progress: </gray><white>{current}</white><gray>/</gray><white>{total}</white><gray> (+{rate}/tick)</gray>",
                    Map.of("current", currentWork, "total", totalWork, "rate", definition.speed())));
        }
        meta.displayName(blocked
                ? localization.component("electric-ui.blocked.name", "<red>Blocked</red>")
                : Component.text(" "));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildNoPowerIcon(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe) {
        int totalWork = requiredWork(recipe);
        int currentWork = Math.min(totalWork, state.progressWork());
        int remainingWork = Math.max(0, totalWork - currentWork);
        ItemStack stack = new ItemStack(definition.progressMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof Damageable damageable && stack.getType().getMaxDurability() > 0) {
            damageable.setDamage(progressDamage(stack, remainingWork, totalWork));
        }
        meta.displayName(localization.component("electric-ui.no-power.name", "<red>No Power</red>"));
        meta.lore(List.of(
                progressBarLine(currentWork, totalWork),
                Component.text(" "),
                localization.component("electric-ui.no-power.lore", "<gray>Charge this machine to continue.</gray>"),
                localization.component(
                        "electric-ui.energy-buffer",
                        "<gray>Stored: </gray><yellow>{stored}</yellow><gray>/</gray><yellow>{capacity}</yellow><gray> J</gray>",
                        Map.of("stored", state.storedEnergy(), "capacity", definition.energyCapacity()))));
        stack.setItemMeta(meta);
        return stack;
    }

    private Component progressBarLine(int currentWork, int totalWork) {
        float progressPercentage = Math.round(((currentWork * 100.0F) / totalWork) * 100.0F) / 100.0F;
        int filled = Math.min(20, Math.max(0, (int) (progressPercentage / 5.0F)));
        StringBuilder builder = new StringBuilder();
        builder.append(progressColor(progressPercentage));
        for (int i = 0; i < filled; i++) {
            builder.append(':');
        }
        builder.append("&7");
        for (int i = filled; i < 20; i++) {
            builder.append(':');
        }
        builder.append(" - ").append(progressPercentage).append('%');
        return Text.legacy(builder.toString());
    }

    private String progressColor(float percentage) {
        if (percentage < 16.0F) {
            return "&4";
        }
        if (percentage < 32.0F) {
            return "&c";
        }
        if (percentage < 48.0F) {
            return "&6";
        }
        if (percentage < 64.0F) {
            return "&e";
        }
        if (percentage < 80.0F) {
            return "&2";
        }
        return "&a";
    }

    private int progressDamage(ItemStack item, int remainingWork, int totalWork) {
        return Math.max(0, Math.min(item.getType().getMaxDurability(), (item.getType().getMaxDurability() / totalWork) * remainingWork));
    }

    private String formatTimeLeft(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds - minutes * 60;
        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private int requiredWork(SfxElectricRecipe recipe) {
        return Math.max(1, recipe.baseTicks() * 20);
    }

    private boolean isExtendedUiEnabled(UUID viewerId) {
        return profiles.find(viewerId)
                .map(profile -> profile.machineUiExtended())
                .orElse(true);
    }

    private String displayStackName(SfxElectricStack stack) {
        if (stack == null) {
            return "";
        }
        if (stack.isSfxItem()) {
            ItemStack item = stack.toItemStack(items);
            ItemMeta meta = item.getItemMeta();
            Component fallback = meta != null && meta.hasDisplayName() ? meta.displayName() : Component.text(stack.itemId());
            return plainText(localization.itemName(stack.itemId(), fallback));
        }
        return plainText(Component.translatable(stack.material().translationKey()));
    }

    private String plainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
