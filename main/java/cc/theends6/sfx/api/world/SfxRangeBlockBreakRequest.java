package cc.theends6.sfx.api.world;

import cc.theends6.sfx.api.container.SfxTransactionReservation;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


public record SfxRangeBlockBreakRequest(Player actor, List<Location> locations, ItemStack tool, boolean drops,
                                        IntFunction<Optional<SfxTransactionReservation>> resourceReservation) {
    public SfxRangeBlockBreakRequest {
        if (actor == null) throw new IllegalArgumentException("actor must not be null");
        if (locations == null) throw new IllegalArgumentException("locations must not be null");
        locations = locations.stream().map(location -> location == null ? null : location.clone()).toList();
        tool = tool == null ? null : tool.clone();
        if (resourceReservation == null) throw new IllegalArgumentException("resourceReservation must not be null");
    }

    @Override public List<Location> locations() {
        return locations.stream().map(location -> location == null ? null : location.clone()).toList();
    }

    @Override public ItemStack tool() { return tool == null ? null : tool.clone(); }

    public static SfxRangeBlockBreakRequest unmetered(Player actor, List<Location> locations,
                                                       ItemStack tool, boolean drops) {
        return new SfxRangeBlockBreakRequest(actor, locations, tool, drops,
                ignored -> Optional.of(SfxTransactionReservation.noop()));
    }
}
