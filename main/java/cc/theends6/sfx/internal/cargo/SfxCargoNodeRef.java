package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;

record SfxCargoNodeRef(SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition, SfxCargoNodeState state, SfxCargoEndpoint endpoint) {
    SfxCargoNodeRef(SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition, SfxCargoNodeState state) {
        this(instance, definition, state, null);
    }

    SfxCargoNodeRef withEndpoint(SfxCargoEndpoint endpoint) {
        return new SfxCargoNodeRef(instance, definition, state, endpoint);
    }

    int priority() {
        return definition.type() == SfxCargoComponentType.ADVANCED_OUTPUT_NODE ? state.priority : 1;
    }
}
