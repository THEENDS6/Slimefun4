package cc.theends6.sfx.api.permission;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;










public final class SfxActionActor {
    
    public enum Kind { PLAYER, OWNER, MACHINE, SYSTEM }

    private static final SfxActionActor SYSTEM = new SfxActionActor(Kind.SYSTEM, null, null, null);

    private final Kind kind;
    private final UUID ownerId;
    private final Player onlinePlayer;
    private final UUID machineInstanceId;

    private SfxActionActor(Kind kind, UUID ownerId, Player onlinePlayer, UUID machineInstanceId) {
        this.kind = kind;
        this.ownerId = ownerId;
        this.onlinePlayer = onlinePlayer;
        this.machineInstanceId = machineInstanceId;
    }

    
    public static SfxActionActor player(Player player) {
        Objects.requireNonNull(player, "player");
        return new SfxActionActor(Kind.PLAYER, player.getUniqueId(), player, null);
    }

    
    public static SfxActionActor owner(UUID ownerId, Player onlineOrNull) {
        Objects.requireNonNull(ownerId, "ownerId");
        return new SfxActionActor(Kind.OWNER, ownerId, onlineOrNull, null);
    }

    




    public static SfxActionActor machine(UUID machineInstanceId, UUID ownerId, Player onlineOwnerOrNull) {
        return new SfxActionActor(Kind.MACHINE, ownerId, onlineOwnerOrNull, machineInstanceId);
    }

    
    public static SfxActionActor system() {
        return SYSTEM;
    }

    public Kind kind() {
        return kind;
    }

    
    public UUID ownerId() {
        return ownerId;
    }

    
    public Player onlinePlayer() {
        return onlinePlayer;
    }

    
    public UUID machineInstanceId() {
        return machineInstanceId;
    }

    public boolean hasOnlinePlayer() {
        return onlinePlayer != null && onlinePlayer.isOnline();
    }

    @Override
    public String toString() {
        return "SfxActionActor[" + kind + ", owner=" + ownerId
                + (machineInstanceId != null ? ", machine=" + machineInstanceId : "")
                + (hasOnlinePlayer() ? ", online" : "") + "]";
    }
}
