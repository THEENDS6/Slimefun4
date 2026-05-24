package cc.theends6.sfx.internal.android;

import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import org.bukkit.Location;

record MoveIntent(SfxBlockInstanceRecord instance, Location from, Location to, SfxAndroidInstruction instruction, boolean clearsTargetBeforeMove) {
}
