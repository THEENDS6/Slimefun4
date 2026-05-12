package cc.theends6.sfx.internal.block;

import java.util.UUID;
import org.bukkit.Location;

record CruciblePlan(int inputAmount, boolean water) {
}

record FurnaceStats(int processingSpeed, int fuelEfficiency, int fortuneLevel) {
}

record ActiveCrucibleProcess(UUID token, Location outputLocation, boolean water) {
}
