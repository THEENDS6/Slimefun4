package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;

record SfxEnergyFuelMatch(int inputSlot, SfxElectricStack input, SfxElectricStack output, String key, int totalTenths) {
}
