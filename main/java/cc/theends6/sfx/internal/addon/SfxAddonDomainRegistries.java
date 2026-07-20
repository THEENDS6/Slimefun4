package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.api.container.SfxVirtualContainerType;
import cc.theends6.sfx.api.display.SfxDisplayCategory;
import cc.theends6.sfx.api.display.SfxDisplayRegistrar;
import cc.theends6.sfx.api.display.SfxDisplayType;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousManualMachine;
import cc.theends6.sfx.api.power.SfxPoweredItem;
import cc.theends6.sfx.api.randomtick.SfxRandomTickType;
import cc.theends6.sfx.api.registry.SfxDefinitionRegistry;

final class SfxAddonDomainRegistries {
    private final DefaultSfxDefinitionRegistry<SfxBlockType<?>> blocks = new DefaultSfxDefinitionRegistry<>();
    private final DefaultSfxDefinitionRegistry<SfxRandomTickType<?>> randomTicks = new DefaultSfxDefinitionRegistry<>();
    private final DefaultSfxDefinitionRegistry<SfxDisplayCategory> displayCategories = new DefaultSfxDefinitionRegistry<>();
    private final DefaultSfxDefinitionRegistry<SfxDisplayType> displayTypes = new DefaultSfxDefinitionRegistry<>();
    private final DefaultSfxDefinitionRegistry<SfxVirtualContainerType> containers = new DefaultSfxDefinitionRegistry<>();
    private final DefaultSfxDefinitionRegistry<SfxContinuousManualMachine> continuousMachines = new DefaultSfxDefinitionRegistry<>();
    private final DefaultSfxDefinitionRegistry<SfxPoweredItem> poweredItems = new DefaultSfxDefinitionRegistry<>();

    Views views(String owner) {
        return new Views(blocks.view(owner), randomTicks.view(owner), new SfxDisplayRegistrar() {
            @Override public SfxDefinitionRegistry<SfxDisplayCategory> categories() { return displayCategories.view(owner); }
            @Override public SfxDefinitionRegistry<SfxDisplayType> types() { return displayTypes.view(owner); }
        }, containers.view(owner), continuousMachines.view(owner), poweredItems.view(owner));
    }

    void removeOwner(String owner) {
        blocks.removeOwner(owner);
        randomTicks.removeOwner(owner);
        displayCategories.removeOwner(owner);
        displayTypes.removeOwner(owner);
        containers.removeOwner(owner);
        continuousMachines.removeOwner(owner);
        poweredItems.removeOwner(owner);
    }

    void clear() {
        blocks.clear(); randomTicks.clear(); displayCategories.clear(); displayTypes.clear();
        containers.clear(); continuousMachines.clear(); poweredItems.clear();
    }

    java.util.Optional<SfxBlockType<?>> block(String id) { return blocks.find(id); }
    java.util.Collection<SfxRandomTickType<?>> randomTickTypes() { return randomTicks.definitions(); }
    java.util.Optional<SfxDisplayCategory> displayCategory(String id) { return displayCategories.find(id); }
    java.util.Optional<SfxDisplayType> displayType(String id) { return displayTypes.find(id); }
    java.util.Optional<SfxVirtualContainerType> containerType(String id) { return containers.find(id); }
    java.util.Optional<SfxContinuousManualMachine> continuousMachine(String id) { return continuousMachines.find(id); }
    java.util.Optional<SfxPoweredItem> poweredItem(String id) { return poweredItems.find(id); }

    record Views(SfxDefinitionRegistry<SfxBlockType<?>> blocks,
                 SfxDefinitionRegistry<SfxRandomTickType<?>> randomTicks,
                 SfxDisplayRegistrar displays,
                 SfxDefinitionRegistry<SfxVirtualContainerType> containers,
                 SfxDefinitionRegistry<SfxContinuousManualMachine> continuousMachines,
                 SfxDefinitionRegistry<SfxPoweredItem> poweredItems) {}
}
