package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.addon.SfxAddonRandomTickService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;


public final class SfxChunkRuntimeCoordinator implements Listener {
    private final SfxBlockDataService blockData;
    private final SfxBlockPersistenceListener persistence;
    private final SfxBasicMachineBlockListener basic;
    private final SfxAndroidService android;
    private final SfxPlaceableBlockListener placeable;
    private final SfxAddonRandomTickService randomTick;
    private final SfxElectricMachineService electric;
    private final SfxConfigurableMachineService configurable;
    private final SfxEnergyService energy;
    private final SfxCargoService cargo;
    private final SfxInfusedHopperService infusedHopper;

    public SfxChunkRuntimeCoordinator(
            SfxBlockDataService blockData,
            SfxBlockPersistenceListener persistence,
            SfxBasicMachineBlockListener basic,
            SfxAndroidService android,
            SfxPlaceableBlockListener placeable,
            SfxAddonRandomTickService randomTick,
            SfxElectricMachineService electric,
            SfxConfigurableMachineService configurable,
            SfxEnergyService energy,
            SfxCargoService cargo,
            SfxInfusedHopperService infusedHopper) {
        this.blockData = blockData;
        this.persistence = persistence;
        this.basic = basic;
        this.android = android;
        this.placeable = placeable;
        this.randomTick = randomTick;
        this.electric = electric;
        this.configurable = configurable;
        this.energy = energy;
        this.cargo = cargo;
        this.infusedHopper = infusedHopper;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        blockData.attachChunk(chunk);
        blockData.reconcileChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
        basic.onChunkLoad(chunk);
        android.onChunkLoad(chunk);
        placeable.handleChunkLoad(chunk);
        electric.onChunkLoad(chunk);
        configurable.onChunkLoad(chunk);
        energy.onChunkLoad(chunk);
        cargo.onChunkLoad(chunk);
        randomTick.onChunkLoad(chunk);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        basic.onChunkUnload(chunk);
        android.onChunkUnload(chunk);
        placeable.handleChunkUnload(chunk);
        electric.onChunkUnload(chunk);
        configurable.onChunkUnload(chunk);
        energy.onChunkUnload(chunk);
        cargo.onChunkUnload(chunk);
        infusedHopper.onChunkUnload(chunk);
        persistence.handleChunkUnload(chunk);
        randomTick.onChunkUnload(chunk);
        blockData.detachChunk(chunk);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        placeable.handleWorldUnload(event.getWorld());
    }
}
