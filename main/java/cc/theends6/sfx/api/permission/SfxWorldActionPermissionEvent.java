package cc.theends6.sfx.api.permission;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;














public final class SfxWorldActionPermissionEvent extends Event implements Cancellable {
    
    public enum Result { PASS, ALLOW, DENY }

    private static final HandlerList HANDLERS = new HandlerList();

    private final SfxWorldActionType actionType;
    private final SfxActionActor actor;
    private final Location location;
    private final Block block;
    private final Entity entity;
    private final EntityType spawnType;
    private final ItemStack item;
    private Result result = Result.PASS;

    public SfxWorldActionPermissionEvent(SfxWorldActionType actionType, SfxActionActor actor, Location location,
                                         Block block, Entity entity, EntityType spawnType, ItemStack item) {
        this.actionType = actionType;
        this.actor = actor;
        this.location = location;
        this.block = block;
        this.entity = entity;
        this.spawnType = spawnType;
        this.item = item;
    }

    public SfxWorldActionType actionType() {
        return actionType;
    }

    public SfxActionActor actor() {
        return actor;
    }

    
    public UUID actorId() {
        return actor.ownerId();
    }

    
    public Player actorPlayer() {
        return actor.onlinePlayer();
    }

    public Location location() {
        return location;
    }

    
    public Block block() {
        return block;
    }

    
    public Entity entity() {
        return entity;
    }

    
    public EntityType spawnType() {
        return spawnType;
    }

    
    public ItemStack item() {
        return item;
    }

    public Result result() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result == null ? Result.PASS : result;
    }

    @Override
    public boolean isCancelled() {
        return result == Result.DENY;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.result = cancelled ? Result.DENY : Result.PASS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
