package cc.theends6.sfx.internal.machine;

/** Declarative capabilities used by the shared SFX machine pipeline. */
public enum SfxMachineCapability {
    HAS_GUI,
    HAS_INPUT,
    HAS_OUTPUT,
    USES_ENERGY,
    PRODUCES_ENERGY,
    USES_RECIPE,
    USES_PROGRESS,
    USES_EXTERNAL_CONTAINER,
    USES_VANILLA_BLOCK_INVENTORY,
    MUTATES_WORLD,
    SPAWNS_ENTITY,
    USES_GPS,
    USES_FUEL_BUFFER,
    USES_REACTOR_SAFETY,
    USES_MULTIBLOCK,
    HAS_CUSTOM_STATUS,
    HAS_VISUAL_EFFECTS,
    HAS_PROXY_PANEL,
    SCRIPTED,
    MOVES_BLOCK,
    AREA_EFFECT,
    ITEM_META_TRANSFORM,
    HAND_INPUT,
    TOPOLOGY_NODE,
    STORAGE_ENDPOINT
}
