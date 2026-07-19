package cc.theends6.sfx.api.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;
import cc.theends6.sfx.api.override.SfxComponentOverrideRegistrar;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.api.cargo.SfxCargoRegistrar;
import cc.theends6.sfx.api.container.SfxVirtualContainerType;
import cc.theends6.sfx.api.display.SfxDisplayRegistrar;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousManualMachine;
import cc.theends6.sfx.api.power.SfxPoweredItem;
import cc.theends6.sfx.api.randomtick.SfxRandomTickType;
import cc.theends6.sfx.api.registry.SfxDefinitionRegistry;
import cc.theends6.sfx.api.world.SfxProtectionService;
import cc.theends6.sfx.api.world.SfxWorldActionService;

public interface SfxAddonContext {
    SfxApi api();

    SfxFeatureRegistrar features();

    SfxBehaviorRegistrar behaviors();

    
    SfxComponentOverrideRegistrar overrides();

    
    SfxAddonResources resources();

    default SfxScheduler scheduler() { return resources(); }

    default SfxItemRegistry items() { return api().itemRegistry(); }

    SfxDefinitionRegistry<SfxBlockType<?>> blocks();

    SfxDefinitionRegistry<SfxRandomTickType<?>> randomTicks();

    SfxDisplayRegistrar displays();

    SfxDefinitionRegistry<SfxVirtualContainerType> containers();

    SfxDefinitionRegistry<SfxContinuousManualMachine> continuousMachines();

    default SfxManualMachineRegistry machines() { return api().manualMachines(); }

    SfxDefinitionRegistry<SfxPoweredItem> power();

    default SfxCargoRegistrar cargo() { return behaviors()::registerCargoNode; }

    default SfxComponentOverrideRegistrar components() { return overrides(); }

    default SfxWorldActionService worldActions() { return api().worldActions(); }

    default SfxProtectionService protection() { return api().protection(); }

    
    File dataDirectory();

    
    FileConfiguration config();

    boolean configBoolean(String path, boolean fallback);

    int configInt(String path, int fallback);

    double configDouble(String path, double fallback);

    String configString(String path, String fallback);
}
