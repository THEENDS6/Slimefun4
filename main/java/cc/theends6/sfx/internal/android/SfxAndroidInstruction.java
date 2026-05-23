package cc.theends6.sfx.internal.android;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

public enum SfxAndroidInstruction {
    WAIT("Wait", Material.CLOCK, "2ee174f41e594e64ea3141c07daf7acf1fa045c230b2b0b0fb3da163db22f455", "<gray>Do nothing for one Android tick.</gray>"),
    TURN_LEFT("Turn Left", Material.ARROW, "a185c97dbb8353de652698d24b64327b793a3f32a98be67b719fbedab35e", "<gray>Rotate left.</gray>"),
    TURN_RIGHT("Turn Right", Material.ARROW, "31c0ededd7115fc1b23d51ce966358b27195daf26ebb6e45a66c34c69c34091", "<gray>Rotate right.</gray>"),
    GO_FORWARD("Go Forward", Material.LIME_DYE, "d9bf6db4aeda9d8822b9f736538e8c18b9a4844f84eb45504adfbfee87eb", "<gray>Move one block forward.</gray>"),
    GO_UP("Go Up", Material.LIME_DYE, "105a2cab8b68ea57e3af992a36e47c8ff9aa87cc8776281966f8c3cf31a38", "<gray>Move one block up.</gray>"),
    GO_DOWN("Go Down", Material.LIME_DYE, "c01586e39f6ffa63b4fb301b65ca7da8a92f7353aaab89d3886579125dfbaf9", "<gray>Move one block down.</gray>"),
    INTERFACE_ITEMS("Interface Items", Material.DISPENSER, "90a4dbf6625c42be57a8ba2c330954a76bdf22785540e87a5c9672685238ec", "<gray>Push output into an item interface ahead.</gray>"),
    INTERFACE_FUEL("Interface Fuel", Material.FURNACE, "2432f5282a50745b912be14deda581bd4a09b977a3c32d7e9578491fee8fa7", "<gray>Pull fuel from a fuel interface ahead.</gray>"),
    DIG_FORWARD("Dig Forward", Material.DIAMOND_PICKAXE, "b6ea2135838461534372f2da6c862d21cd5f3d2c7119f2bb674bbd42791", "<gray>Mine the block ahead.</gray>"),
    DIG_UP("Dig Up", Material.DIAMOND_PICKAXE, "2e6ce011ac9a7a75b2fcd408ad21a3ac1722f6e2eed8781cafd12552282b88", "<gray>Mine the block above.</gray>"),
    DIG_DOWN("Dig Down", Material.DIAMOND_PICKAXE, "8d862024108c785bc0ef7199ec77c402dbbfcc624e9f41f83d8aed8b39fd13", "<gray>Mine the block below.</gray>"),
    MOVE_AND_DIG_FORWARD("Move & Dig Forward", Material.NETHERITE_PICKAXE, "b6ea2135838461534372f2da6c862d21cd5f3d2c7119f2bb674bbd42791", "<gray>Mine ahead and move into the cleared block.</gray>"),
    MOVE_AND_DIG_UP("Move & Dig Up", Material.NETHERITE_PICKAXE, "2e6ce011ac9a7a75b2fcd408ad21a3ac1722f6e2eed8781cafd12552282b88", "<gray>Mine above and move up.</gray>"),
    MOVE_AND_DIG_DOWN("Move & Dig Down", Material.NETHERITE_PICKAXE, "8d862024108c785bc0ef7199ec77c402dbbfcc624e9f41f83d8aed8b39fd13", "<gray>Mine below and move down.</gray>"),
    FARM_FORWARD("Farm Forward", Material.WHEAT, "4de9a522c3d9e7d85f3d82c375dc37fecc856dbd801eb3bcedc1165198bf", "<gray>Harvest the mature crop ahead.</gray>"),
    FARM_DOWN("Farm Down", Material.WHEAT_SEEDS, "2d4296b333d25319af3f33051797f9e6d821cd19a014fb7137beb86a4e9e96", "<gray>Harvest the mature crop below.</gray>"),
    FARM_EXOTIC_FORWARD("Farm Exotic Forward", Material.GOLDEN_APPLE, "4de9a522c3d9e7d85f3d82c375dc37fecc856dbd801eb3bcedc1165198bf", "<gray>Advanced farmer exotic crop hook.</gray>"),
    FARM_EXOTIC_DOWN("Farm Exotic Down", Material.GOLDEN_CARROT, "2d4296b333d25319af3f33051797f9e6d821cd19a014fb7137beb86a4e9e96", "<gray>Advanced farmer exotic crop hook.</gray>"),
    CHOP_TREE("Chop Tree", Material.IRON_AXE, "64ba49384dba7b7acdb4f70e9361e6d57cbbcbf720cf4f16c2bb83e4557", "<gray>Cut one log from the tree ahead.</gray>"),
    CATCH_FISH("Catch Fish", Material.FISHING_ROD, "fd4fde511f4454101e4a2a72bc86f12985dfcda76b64bb24dc63a9fa9e3a3", "<gray>Fish in water below.</gray>"),
    ATTACK_MOBS_ANIMALS("Attack Mobs & Animals", Material.IRON_SWORD, "c7e6c40f68b775f2efcd7bd9916b327869dcf27e24c855d0a18e07ac04fe1", "<gray>Attack valid creatures ahead.</gray>"),
    ATTACK_MOBS("Attack Mobs", Material.ROTTEN_FLESH, "c7e6c40f68b775f2efcd7bd9916b327869dcf27e24c855d0a18e07ac04fe1", "<gray>Attack monsters ahead.</gray>"),
    ATTACK_ANIMALS("Attack Animals", Material.BEEF, "c7e6c40f68b775f2efcd7bd9916b327869dcf27e24c855d0a18e07ac04fe1", "<gray>Attack animals ahead.</gray>"),
    ATTACK_ANIMALS_ADULT("Attack Adult Animals", Material.LEATHER, "c7e6c40f68b775f2efcd7bd9916b327869dcf27e24c855d0a18e07ac04fe1", "<gray>Attack adult animals ahead.</gray>");

    private final String displayName;
    private final Material icon;
    private final String texture;
    private final String description;

    SfxAndroidInstruction(String displayName, Material icon, String texture, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.texture = texture;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String texture() {
        return texture;
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
