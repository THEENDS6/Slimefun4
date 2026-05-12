package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;

record SfxEnergyNodeRef(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
}
