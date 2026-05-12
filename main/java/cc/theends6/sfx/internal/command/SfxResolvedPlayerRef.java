package cc.theends6.sfx.internal.command;

import org.bukkit.OfflinePlayer;

record ResolvedPlayerRef(OfflinePlayer offlinePlayer, String displayName) {
}
