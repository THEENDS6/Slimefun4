package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemKind;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxRecipe;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.machine.ManualMachineDefinition;
import cc.theends6.sfx.internal.machine.ManualMachineOperation;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;





public final class BaseContentBootstrap {
    public static final String ENHANCED_CRAFTING_TABLE = "sf:enhanced_crafting_table";
    public static final String GRIND_STONE = "sf:grind_stone";
    public static final String MANUAL_COMPRESSOR = "sf:compressor";
    public static final String ORE_CRUSHER = "sf:ore_crusher";
    public static final String ORE_WASHER = "sf:ore_washer";
    public static final String ARMOR_FORGE = "sf:armor_forge";
    public static final String MAKESHIFT_SMELTERY = "sf:makeshift_smeltery";
    public static final String SMELTERY = "sf:smeltery";
    public static final String PRESSURE_CHAMBER = "sf:pressure_chamber";
    public static final String MAGIC_WORKBENCH = "sf:magic_workbench";
    public static final String TABLE_SAW = "sf:table_saw";
    public static final String JUICER = "sf:juicer";
    public static final String AUTOMATED_PANNING_MACHINE = "sf:automated_panning_machine";

    private BaseContentBootstrap() {
    }

    public static void register(SfxItemRegistry registry, DefaultManualMachineRegistry machines) {
        registerInternalCategories(registry);
        registerGuideItems(registry);
        registerManualMachineDefinitions(machines);
    }

    public static void syncManualMachineGuideContent(DefaultSfxItemRegistry registry, DefaultManualMachineRegistry machines) {
        for (ManualMachineDefinition machine : machines.machines()) {
            SfxItemDefinition machineItem = registry.item(machine.id()).orElseGet(() -> fallbackMachineItem(registry, machine));
            if (machineItem != null) {
                registry.replaceItem(withAddedRecipe(machineItem, structureRecipe(machine)));
            }
        }
    }

    private static void registerInternalCategories(SfxItemRegistry registry) {
        registry.registerCategory(new SfxItemCategory(
                "sfx:internal",
                Text.mm("<dark_gray>Internal</dark_gray>"),
                ItemBuilder.of(Material.BARRIER).name("<dark_gray>Internal</dark_gray>").build(),
                999999,
                true
        ));
    }

    private static void registerGuideItems(SfxItemRegistry registry) {
        registry.registerItem(SfxItemDefinition.builder("sfx:guide", Material.ENCHANTED_BOOK, Text.mm("<green>SFX Guide</green>"))
                .category("sfx:internal")
                .kind(SfxItemKind.GUIDE)
                .hidden(true)
                .giveable(false)
                .build());

        registry.registerItem(SfxItemDefinition.builder("sfx:cheat_guide", Material.ENCHANTED_BOOK, Text.mm("<red>SFX Cheat Guide</red>"))
                .category("sfx:internal")
                .kind(SfxItemKind.GUIDE)
                .hidden(true)
                .giveable(false)
                .build());
    }

    private static void registerManualMachineDefinitions(DefaultManualMachineRegistry machines) {
        machines.registerMachine(new ManualMachineDefinition(
                ENHANCED_CRAFTING_TABLE,
                Text.legacy("&eEnhanced Crafting Table"),
                Material.CRAFTING_TABLE,
                actual(null, null, null, null, Material.CRAFTING_TABLE, null, null, Material.DISPENSER, null),
                display(null, null, null, null, Material.CRAFTING_TABLE, null, null, Material.DISPENSER, null),
                BlockFace.SELF,
                BlockFace.DOWN,
                ManualMachineOperation.SHAPED_3X3
        ));
        machines.registerMachine(new ManualMachineDefinition(
                GRIND_STONE,
                Text.legacy("&bGrind Stone"),
                Material.DISPENSER,
                actual(null, null, null, null, Material.OAK_FENCE, null, null, Material.DISPENSER, null),
                display(null, null, null, null, Material.OAK_FENCE, null, null, Material.DISPENSER, null),
                BlockFace.SELF,
                BlockFace.DOWN,
                ManualMachineOperation.SINGLE_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                MANUAL_COMPRESSOR,
                Text.legacy("&bCompressor"),
                Material.PISTON,
                actual(null, null, null, null, Material.NETHER_BRICK_FENCE, null, Material.PISTON, Material.DISPENSER, Material.PISTON),
                display(null, null, null, null, Material.NETHER_BRICK_FENCE, null, Material.PISTON, Material.DISPENSER, Material.PISTON),
                BlockFace.SELF,
                BlockFace.DOWN,
                ManualMachineOperation.SINGLE_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                ORE_CRUSHER,
                Text.legacy("&bOre Crusher"),
                Material.DISPENSER,
                actual(null, null, null, null, Material.NETHER_BRICK_FENCE, null, Material.IRON_BARS, Material.DISPENSER, Material.IRON_BARS),
                display(null, null, null, null, Material.NETHER_BRICK_FENCE, null, Material.IRON_BARS, Material.DISPENSER, Material.IRON_BARS),
                BlockFace.SELF,
                BlockFace.DOWN,
                ManualMachineOperation.SINGLE_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                ORE_WASHER,
                Text.legacy("&6Ore Washer"),
                Material.CAULDRON,
                actual(null, Material.DISPENSER, null, null, Material.OAK_FENCE, null, null, Material.CAULDRON, null),
                display(null, Material.DISPENSER, null, null, Material.OAK_FENCE, null, null, Material.CAULDRON, null),
                BlockFace.SELF,
                BlockFace.UP,
                ManualMachineOperation.SINGLE_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                ARMOR_FORGE,
                Text.legacy("&6Armor Forge"),
                Material.ANVIL,
                actual(null, null, null, null, Material.ANVIL, null, null, Material.DISPENSER, null),
                display(null, null, null, null, Material.ANVIL, null, null, Material.DISPENSER, null),
                BlockFace.SELF,
                BlockFace.DOWN,
                ManualMachineOperation.SHAPED_3X3
        ));
        machines.registerMachine(new ManualMachineDefinition(
                MAKESHIFT_SMELTERY,
                Text.legacy("&eMakeshift Smeltery"),
                Material.BLAST_FURNACE,
                actual(null, Material.OAK_FENCE, null, Material.BRICKS, Material.DISPENSER, Material.BRICKS, null, null, null),
                display(null, Material.OAK_FENCE, null, Material.BRICKS, Material.DISPENSER, Material.BRICKS, null, Material.FLINT_AND_STEEL, null),
                BlockFace.DOWN,
                BlockFace.SELF,
                ManualMachineOperation.SHAPELESS_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                SMELTERY,
                Text.legacy("&6Smeltery"),
                Material.FURNACE,
                actual(null, Material.NETHER_BRICK_FENCE, null, Material.NETHER_BRICKS, Material.DISPENSER, Material.NETHER_BRICKS, null, null, null),
                display(null, Material.NETHER_BRICK_FENCE, null, Material.NETHER_BRICKS, Material.DISPENSER, Material.NETHER_BRICKS, null, Material.FLINT_AND_STEEL, null),
                BlockFace.DOWN,
                BlockFace.SELF,
                ManualMachineOperation.SHAPELESS_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                PRESSURE_CHAMBER,
                Text.legacy("&bPressure Chamber"),
                Material.GLASS,
                actual(Material.SMOOTH_STONE_SLAB, Material.DISPENSER, Material.SMOOTH_STONE_SLAB, Material.PISTON, Material.GLASS, Material.PISTON, Material.PISTON, Material.CAULDRON, Material.PISTON),
                display(Material.SMOOTH_STONE_SLAB, Material.DISPENSER, Material.SMOOTH_STONE_SLAB, Material.PISTON, Material.GLASS, Material.PISTON, Material.PISTON, Material.CAULDRON, Material.PISTON),
                BlockFace.DOWN,
                BlockFace.UP,
                ManualMachineOperation.SINGLE_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                MAGIC_WORKBENCH,
                Text.legacy("&6Magic Workbench"),
                Material.CRAFTING_TABLE,
                actual(null, null, null, null, Material.CRAFTING_TABLE, null, Material.BOOKSHELF, Material.DISPENSER, null),
                display(null, null, null, null, null, null, Material.BOOKSHELF, Material.CRAFTING_TABLE, Material.DISPENSER),
                BlockFace.SELF,
                BlockFace.DOWN,
                ManualMachineOperation.SHAPED_3X3
        ));
        machines.registerMachine(new ManualMachineDefinition(
                TABLE_SAW,
                Text.legacy("&6Table Saw"),
                Material.STONECUTTER,
                actual(null, null, null, Material.SMOOTH_STONE_SLAB, Material.STONECUTTER, Material.SMOOTH_STONE_SLAB, null, Material.IRON_BLOCK, null),
                display(null, null, null, Material.SMOOTH_STONE_SLAB, Material.STONECUTTER, Material.SMOOTH_STONE_SLAB, null, Material.IRON_BLOCK, null),
                BlockFace.SELF,
                BlockFace.SELF,
                ManualMachineOperation.HAND_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                JUICER,
                Text.legacy("&aJuicer"),
                Material.GLASS_BOTTLE,
                actual(null, Material.GLASS, null, null, Material.NETHER_BRICK_FENCE, null, null, Material.DISPENSER, null),
                display(null, Material.GLASS, null, null, Material.NETHER_BRICK_FENCE, null, null, Material.DISPENSER, null),
                BlockFace.SELF,
                BlockFace.DOWN,
                ManualMachineOperation.SINGLE_INPUT
        ));
        machines.registerMachine(new ManualMachineDefinition(
                AUTOMATED_PANNING_MACHINE,
                Text.legacy("&eAutomated Panning Machine"),
                Material.BOWL,
                actual(null, null, null, null, Material.OAK_TRAPDOOR, null, null, Material.CAULDRON, null),
                display(null, null, null, null, Material.OAK_TRAPDOOR, null, null, Material.CAULDRON, null),
                BlockFace.SELF,
                BlockFace.SELF,
                ManualMachineOperation.HAND_INPUT
        ));
    }

    private static SfxRecipe structureRecipe(ManualMachineDefinition machine) {
        return SfxRecipe.shaped("multiblock-structure", toRecipeMatrix(machine.displayPattern()), null);
    }

    private static List<SfxRecipeSlot> toRecipeMatrix(Material[] pattern) {
        List<SfxRecipeSlot> matrix = new ArrayList<>(9);
        for (Material material : pattern) {
            matrix.add(material == null ? e() : v(material));
        }
        return matrix;
    }

    private static SfxItemDefinition fallbackMachineItem(DefaultSfxItemRegistry registry, ManualMachineDefinition machine) {
        if (registry.category("sf:multiblocks").isEmpty()) {
            return null;
        }
        SfxItemDefinition definition = SfxItemDefinition.builder(machine.id(), machine.icon(), machine.name())
                .category("sf:multiblocks")
                .flag("legacy-sf")
                .build();
        registry.registerItem(definition);
        return definition;
    }

    private static SfxItemDefinition withAddedRecipe(SfxItemDefinition existing, SfxRecipe extra) {
        SfxItemDefinition.Builder builder = SfxItemDefinition.builder(existing.id(), existing.material(), existing.name())
                .lore(existing.lore())
                .category(existing.categoryId())
                .version(existing.version())
                .hidden(existing.hidden())
                .giveable(existing.giveable())
                .kind(existing.kind())
                .variant(existing.variant());
        if (existing.headTextureHash() != null) {
            builder.headTexture(existing.headTextureHash());
        }
        if (existing.colorRgb() != null) {
            builder.colorRgb(existing.colorRgb());
        }
        for (String flag : existing.flags()) {
            builder.flag(flag);
        }
        for (String itemFlag : existing.itemFlags()) {
            builder.itemFlag(itemFlag);
        }
        for (var enchantment : existing.enchantments().entrySet()) {
            builder.enchantment(enchantment.getKey(), enchantment.getValue());
        }
        builder.unbreakable(existing.unbreakable());
        for (SfxRecipe recipe : existing.recipes()) {
            builder.addRecipe(recipe);
        }
        builder.addRecipe(extra);
        return builder.build();
    }

    private static Material[] actual(Material a, Material b, Material c, Material d, Material e, Material f, Material g, Material h, Material i) {
        return new Material[]{a, b, c, d, e, f, g, h, i};
    }

    private static Material[] display(Material a, Material b, Material c, Material d, Material e, Material f, Material g, Material h, Material i) {
        return actual(a, b, c, d, e, f, g, h, i);
    }


    private static SfxRecipeSlot e() {
        return SfxRecipeSlot.empty();
    }

    private static SfxRecipeSlot v(Material material) {
        return SfxRecipeSlot.vanilla(material);
    }



}
