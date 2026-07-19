package cc.theends6.sfx.api.container;

import java.util.Objects;
import java.util.function.Function;
import org.bukkit.Location;

public record SfxVirtualContainerType(String id,
                                      Function<Location, SfxVirtualItemContainer> itemFactory,
                                      Function<Location, SfxVirtualFluidContainer> fluidFactory) {
    public SfxVirtualContainerType {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Container type id must not be blank");
        if (itemFactory == null && fluidFactory == null) {
            throw new IllegalArgumentException("Container type must expose an item or fluid capability");
        }
    }
}
