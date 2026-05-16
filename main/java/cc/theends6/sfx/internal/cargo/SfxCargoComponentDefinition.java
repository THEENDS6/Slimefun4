package cc.theends6.sfx.internal.cargo;

public record SfxCargoComponentDefinition(String id, SfxCargoComponentType type) {
    public boolean isTopologyBackbone() {
        return type == SfxCargoComponentType.MANAGER || type == SfxCargoComponentType.CONNECTOR;
    }

    public boolean isController() {
        return type == SfxCargoComponentType.MANAGER;
    }

    public boolean isTerminal() {
        return type == SfxCargoComponentType.INPUT_NODE
                || type == SfxCargoComponentType.ADVANCED_INPUT_NODE
                || type == SfxCargoComponentType.OUTPUT_NODE
                || type == SfxCargoComponentType.ADVANCED_OUTPUT_NODE;
    }

    public boolean isInput() {
        return type == SfxCargoComponentType.INPUT_NODE || type == SfxCargoComponentType.ADVANCED_INPUT_NODE;
    }

    public boolean isOutput() {
        return type == SfxCargoComponentType.OUTPUT_NODE || type == SfxCargoComponentType.ADVANCED_OUTPUT_NODE;
    }

    public boolean isCargoMachine() {
        return type == SfxCargoComponentType.TRASH_CAN
                || type == SfxCargoComponentType.REACTOR_ACCESS_PORT
                || type == SfxCargoComponentType.VANILLA_AUTO_CRAFTER
                || type == SfxCargoComponentType.ENHANCED_AUTO_CRAFTER
                || type == SfxCargoComponentType.ARMOR_AUTO_CRAFTER;
    }
}
