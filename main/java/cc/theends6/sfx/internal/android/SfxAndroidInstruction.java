package cc.theends6.sfx.internal.android;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

public enum SfxAndroidInstruction {
    WAIT("Wait", Material.CLOCK, "<gray>Do nothing for one Android tick.</gray>"),
    TURN_LEFT("Turn Left", Material.ARROW, "<gray>Rotate left.</gray>"),
    TURN_RIGHT("Turn Right", Material.ARROW, "<gray>Rotate right.</gray>"),
    GO_FORWARD("Go Forward", Material.LIME_DYE, "<gray>Move one block forward.</gray>"),
    GO_UP("Go Up", Material.LIME_DYE, "<gray>Move one block up.</gray>"),
    GO_DOWN("Go Down", Material.LIME_DYE, "<gray>Move one block down.</gray>"),
    INTERFACE_ITEMS("Interface Items", Material.DISPENSER, "<gray>Push output into an item interface ahead.</gray>"),
    INTERFACE_FUEL("Interface Fuel", Material.FURNACE, "<gray>Pull fuel from a fuel interface ahead.</gray>"),
    DIG_FORWARD("Dig Forward", Material.DIAMOND_PICKAXE, "<gray>Mine the block ahead.</gray>"),
    DIG_UP("Dig Up", Material.DIAMOND_PICKAXE, "<gray>Mine the block above.</gray>"),
    DIG_DOWN("Dig Down", Material.DIAMOND_PICKAXE, "<gray>Mine the block below.</gray>"),
    MOVE_AND_DIG_FORWARD("Move & Dig Forward", Material.NETHERITE_PICKAXE, "<gray>Mine ahead and move into the cleared block.</gray>"),
    MOVE_AND_DIG_UP("Move & Dig Up", Material.NETHERITE_PICKAXE, "<gray>Mine above and move up.</gray>"),
    MOVE_AND_DIG_DOWN("Move & Dig Down", Material.NETHERITE_PICKAXE, "<gray>Mine below and move down.</gray>"),
    FARM_FORWARD("Farm Forward", Material.WHEAT, "<gray>Harvest the mature crop ahead.</gray>"),
    FARM_DOWN("Farm Down", Material.WHEAT_SEEDS, "<gray>Harvest the mature crop below.</gray>"),
    FARM_EXOTIC_FORWARD("Farm Exotic Forward", Material.GOLDEN_APPLE, "<gray>Advanced farmer exotic crop hook.</gray>"),
    FARM_EXOTIC_DOWN("Farm Exotic Down", Material.GOLDEN_CARROT, "<gray>Advanced farmer exotic crop hook.</gray>"),
    CHOP_TREE("Chop Tree", Material.IRON_AXE, "<gray>Cut one log from the tree ahead.</gray>"),
    CATCH_FISH("Catch Fish", Material.FISHING_ROD, "<gray>Fish in water below.</gray>"),
    ATTACK_MOBS_ANIMALS("Attack Mobs & Animals", Material.IRON_SWORD, "<gray>Attack valid creatures ahead.</gray>"),
    ATTACK_MOBS("Attack Mobs", Material.ROTTEN_FLESH, "<gray>Attack monsters ahead.</gray>"),
    ATTACK_ANIMALS("Attack Animals", Material.BEEF, "<gray>Attack animals ahead.</gray>"),
    ATTACK_ANIMALS_ADULT("Attack Adult Animals", Material.LEATHER, "<gray>Attack adult animals ahead.</gray>");

    private final String displayName;
    private final Material icon;
    private final String description;

    SfxAndroidInstruction(String displayName, Material icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public boolean validFor(SfxAndroidType type) {
        if (type == null) {
            return false;
        }
        return switch (this) {
            case WAIT, TURN_LEFT, TURN_RIGHT, INTERFACE_ITEMS, INTERFACE_FUEL -> true;
            case GO_FORWARD, GO_UP, GO_DOWN -> type.canMove();
            case DIG_FORWARD, DIG_UP, DIG_DOWN, MOVE_AND_DIG_FORWARD, MOVE_AND_DIG_UP, MOVE_AND_DIG_DOWN -> type.function() == SfxAndroidType.AndroidFunction.MINING;
            case FARM_FORWARD, FARM_DOWN -> type.function() == SfxAndroidType.AndroidFunction.FARMING;
            case FARM_EXOTIC_FORWARD, FARM_EXOTIC_DOWN -> type == SfxAndroidType.ADVANCED_FARMER;
            case CHOP_TREE -> type.function() == SfxAndroidType.AndroidFunction.WOODCUTTING;
            case CATCH_FISH -> type.function() == SfxAndroidType.AndroidFunction.FISHING;
            case ATTACK_MOBS_ANIMALS, ATTACK_MOBS, ATTACK_ANIMALS, ATTACK_ANIMALS_ADULT -> type.function() == SfxAndroidType.AndroidFunction.SLAUGHTERING;
        };
    }

    public static List<SfxAndroidInstruction> validForType(SfxAndroidType type) {
        List<SfxAndroidInstruction> result = new ArrayList<>();
        for (SfxAndroidInstruction instruction : values()) {
            if (instruction.validFor(type)) {
                result.add(instruction);
            }
        }
        return result;
    }
}
