package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.electric.SfxElectricStack;

record SfxEnergyFuelMatch(int inputSlot, SfxElectricStack input, SfxElectricStack output, String key, int totalTenths) {
}
