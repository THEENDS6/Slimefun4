package cc.theends6.sfx.internal.block;

import java.util.UUID;
import org.bukkit.Location;

record ActiveCrucibleProcess(UUID token, Location outputLocation, boolean water) {
}
