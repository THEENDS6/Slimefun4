package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

record SfxEnergyNodeRef(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
}
