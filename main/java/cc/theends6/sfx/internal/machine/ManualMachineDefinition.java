package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

public final class ManualMachineDefinition {
    private static final Set<Tag<Material>> SUPPORTED_MATERIAL_TAGS = new HashSet<>();

    static {
        SUPPORTED_MATERIAL_TAGS.add(Tag.LOGS);
        SUPPORTED_MATERIAL_TAGS.add(Tag.WOODEN_FENCES);
        SUPPORTED_MATERIAL_TAGS.add(Tag.WOODEN_SLABS);
        SUPPORTED_MATERIAL_TAGS.add(Tag.WOODEN_TRAPDOORS);
        SUPPORTED_MATERIAL_TAGS.add(Tag.FIRE);
    }

    private final String id;
    private final Component name;
    private final Material icon;
    private final Material[] pattern;
    private final Material[] displayPattern;
    private final BlockFace triggerFace;
    private final BlockFace inventoryFace;
    private final ManualMachineOperation operation;
    private final boolean symmetric;
    private final boolean deployable;
    private final Set<String> tags;
    private final Set<String> acceptedRecipeTypes;

    public ManualMachineDefinition(String id, Component name, Material icon, Material[] pattern, BlockFace triggerFace, BlockFace inventoryFace, ManualMachineOperation operation) {
        this(id, name, icon, pattern, pattern, triggerFace, inventoryFace, operation, true);
    }

    public ManualMachineDefinition(String id, Component name, Material icon, Material[] pattern, Material[] displayPattern, BlockFace triggerFace, BlockFace inventoryFace, ManualMachineOperation operation) {
        this(id, name, icon, pattern, displayPattern, triggerFace, inventoryFace, operation, true);
    }

    public ManualMachineDefinition(String id, Component name, Material icon, Material[] pattern, Material[] displayPattern, BlockFace triggerFace, BlockFace inventoryFace, ManualMachineOperation operation, boolean deployable) {
        this(id, name, icon, pattern, displayPattern, triggerFace, inventoryFace, operation, deployable, Set.of(), Set.of());
    }

    public ManualMachineDefinition(String id, Component name, Material icon, Material[] pattern, Material[] displayPattern, BlockFace triggerFace, BlockFace inventoryFace, ManualMachineOperation operation, boolean deployable, Set<String> tags) {
        this(id, name, icon, pattern, displayPattern, triggerFace, inventoryFace, operation, deployable, tags, Set.of());
    }

    public ManualMachineDefinition(String id, Component name, Material icon, Material[] pattern, Material[] displayPattern, BlockFace triggerFace, BlockFace inventoryFace, ManualMachineOperation operation, boolean deployable, Set<String> tags, Set<String> acceptedRecipeTypes) {
        if (pattern == null || pattern.length != 9) {
            throw new IllegalArgumentException("Manual multiblock pattern must contain exactly 9 material slots.");
        }
        if (displayPattern == null || displayPattern.length != 9) {
            throw new IllegalArgumentException("Manual multiblock display pattern must contain exactly 9 material slots.");
        }
        if (triggerFace != BlockFace.SELF && triggerFace != BlockFace.UP && triggerFace != BlockFace.DOWN) {
            throw new IllegalArgumentException("Manual multiblock trigger face must be SELF, UP or DOWN.");
        }
        if (inventoryFace != BlockFace.SELF && inventoryFace != BlockFace.UP && inventoryFace != BlockFace.DOWN) {
            throw new IllegalArgumentException("Manual multiblock inventory face must be SELF, UP or DOWN.");
        }
        this.id = SfxItemDefinition.normalizeId(id);
        this.name = Objects.requireNonNull(name, "name");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.pattern = Arrays.copyOf(pattern, pattern.length);
        this.displayPattern = Arrays.copyOf(displayPattern, displayPattern.length);
        this.triggerFace = Objects.requireNonNull(triggerFace, "triggerFace");
        this.inventoryFace = Objects.requireNonNull(inventoryFace, "inventoryFace");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.symmetric = isSymmetric(pattern);
        this.deployable = deployable;
        this.tags = normalizeTags(tags, this.id, this.operation);
        this.acceptedRecipeTypes = normalizeRecipeTypes(acceptedRecipeTypes);
    }

    public String id() {
        return id;
    }

    public Component name() {
        return name;
    }

    public Material icon() {
        return icon;
    }

    public Material[] pattern() {
        return Arrays.copyOf(pattern, pattern.length);
    }

    public Material[] displayPattern() {
        return Arrays.copyOf(displayPattern, displayPattern.length);
    }

    public BlockFace triggerFace() {
        return triggerFace;
    }

    public BlockFace inventoryFace() {
        return inventoryFace;
    }

    public ManualMachineOperation operation() {
        return operation;
    }

    public boolean deployable() {
        return deployable;
    }

    public Set<String> tags() {
        return tags;
    }

    public Set<String> acceptedRecipeTypes() {
        return acceptedRecipeTypes;
    }

    public boolean acceptsRecipeType(String recipeType) {
        return recipeType != null && acceptedRecipeTypes.contains(SfxItemDefinition.normalizeId(recipeType));
    }

    public boolean hasTags(Set<String> requiredTags) {
        return requiredTags == null || tags.containsAll(normalizeTags(requiredTags, null, null));
    }

    public Material triggerMaterial() {
        return pattern[4];
    }

    public List<ItemStack> structureKit() {
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (Material material : pattern) {
            if (material == null || material == Material.AIR) {
                continue;
            }
            counts.merge(material, 1, Integer::sum);
        }
        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            result.add(new ItemStack(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public Block centerBlock(Block clickedBlock) {
        return clickedBlock.getRelative(triggerFace);
    }

    public Block inventoryBlock(Block clickedBlock) {
        return centerBlock(clickedBlock).getRelative(inventoryFace);
    }

    public boolean matches(Block clickedBlock) {
        if (clickedBlock == null) {
            return false;
        }
        return comparePattern(centerBlock(clickedBlock));
    }

    private boolean comparePattern(Block center) {
        if (!compareVertical(center, pattern[1], pattern[4], pattern[7])) {
            return false;
        }
        BlockFace[] directions = symmetric
                ? new BlockFace[]{BlockFace.NORTH, BlockFace.EAST}
                : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

        for (BlockFace direction : directions) {
            Block leftColumn = center.getRelative(direction);
            Block rightColumn = center.getRelative(direction.getOppositeFace());
            if (compareVertical(leftColumn, pattern[0], pattern[3], pattern[6])
                    && compareVertical(rightColumn, pattern[2], pattern[5], pattern[8])) {
                return true;
            }
        }
        return false;
    }

    private boolean compareVertical(Block block, Material top, Material middle, Material bottom) {
        return compareMaterial(block.getRelative(BlockFace.UP).getType(), top)
                && compareMaterial(block.getType(), middle)
                && compareMaterial(block.getRelative(BlockFace.DOWN).getType(), bottom);
    }

    private boolean compareMaterial(Material actual, Material expected) {
        if (expected == null) {
            return true;
        }
        if (actual == expected) {
            return true;
        }
        for (Tag<Material> tag : SUPPORTED_MATERIAL_TAGS) {
            if (tag.isTagged(expected) && tag.isTagged(actual)) {
                return true;
            }
        }
        if (expected == Material.PISTON && actual == Material.MOVING_PISTON) {
            return true;
        }
        return false;
    }

    private static boolean isSymmetric(Material[] pattern) {
        return pattern[0] == pattern[2]
                && pattern[3] == pattern[5]
                && pattern[6] == pattern[8];
    }

    private static Set<String> normalizeTags(Set<String> raw, String id, ManualMachineOperation operation) {
        Set<String> result = new LinkedHashSet<>();
        if (id != null && !id.isBlank()) {
            result.add(normalizeTag(id));
            int colon = id.indexOf(':');
            if (colon >= 0 && colon + 1 < id.length()) {
                result.add(normalizeTag(id.substring(colon + 1)));
            }
        }
        if (operation != null) {
            result.add(normalizeTag(operation.name()));
        }
        result.add("manual");
        if (raw != null) {
            for (String tag : raw) {
                if (tag != null && !tag.isBlank()) {
                    result.add(normalizeTag(tag));
                }
            }
        }
        return Set.copyOf(result);
    }

    private static String normalizeTag(String raw) {
        return raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeRecipeTypes(Set<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw != null) {
            for (String recipeType : raw) {
                if (recipeType != null && !recipeType.isBlank()) {
                    result.add(SfxItemDefinition.normalizeId(recipeType));
                }
            }
        }
        return Set.copyOf(result);
    }
}
