package cc.theends6.sfx.example;

import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.block.SfxCyclingBlockDefinition;
import cc.theends6.sfx.api.cargo.SfxCargoNodeDefinition;
import cc.theends6.sfx.api.cargo.SfxCargoNodeKind;
import cc.theends6.sfx.example.fish.DebugFishModule;
import cc.theends6.sfx.example.energy.DebugEnergyUnitProvider;
import cc.theends6.sfx.example.cargo.DebugTransportDispatcherProvider;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;







public final class SfxExampleAddon implements SfxAddon {
    private DebugFishModule debugFish;
    private boolean permissionOwned;
    @Override
    public String id() {
        return ExampleIds.ADDON;
    }

    @Override
    public String name() {
        return "SFX Example Addon";
    }

    @Override
    public void onRegister(SfxAddonContext context) {
        if (Bukkit.getPluginManager().getPermission(ExamplePermissions.DEBUG) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(
                    ExamplePermissions.DEBUG,
                    "Allows viewing and operating SFX Example debug content",
                    PermissionDefault.OP));
            permissionOwned = true;
        }
        context.features().registerBoolean(ExampleIds.FEATURE, "enabled", true);
        context.behaviors().registerEnergyGeneratorProvider(ExampleIds.DEBUG_ENERGY_UNIT,
                ignored -> new DebugEnergyUnitProvider(context));
        context.behaviors().registerCargoNode(new SfxCargoNodeDefinition(
                ExampleIds.DEBUG_TRANSPORT_DISPATCHER,
                SfxCargoNodeKind.MANAGER,
                context.configInt("debug-transport-dispatcher.default-range-x", 8),
                context.configInt("debug-transport-dispatcher.default-range-y", 4),
                context.configInt("debug-transport-dispatcher.default-range-z", 8),
                true,
                new DebugTransportDispatcherProvider(context)
        ));

        
        
        context.behaviors().registerCyclingBlock(new SfxCyclingBlockDefinition(
                ExampleIds.MAGIC_SLIME_BLOCK,
                List.of(Material.SLIME_BLOCK, Material.HONEY_BLOCK),
                context.configInt("magic-slime-block.interval-ticks", 20)
        ));
        debugFish = context.resources().own(new DebugFishModule(context));
    }

    @Override
    public void onDisable() {
        if (debugFish != null) {
            debugFish.close();
            debugFish = null;
        }
        if (permissionOwned) {
            Bukkit.getPluginManager().removePermission(ExamplePermissions.DEBUG);
            permissionOwned = false;
        }
    }
}
