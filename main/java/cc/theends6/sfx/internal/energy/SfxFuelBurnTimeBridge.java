package cc.theends6.sfx.internal.energy;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

final class SfxFuelBurnTimeBridge {
    private final Method asNmsCopy;
    private final Method vanillaBurnTimes;
    private final Method registryAccess;
    private final Method getServer;
    private final Method burnDuration;

    private SfxFuelBurnTimeBridge(
            Method asNmsCopy,
            Method vanillaBurnTimes,
            Method registryAccess,
            Method getServer,
            Method burnDuration
    ) {
        this.asNmsCopy = asNmsCopy;
        this.vanillaBurnTimes = vanillaBurnTimes;
        this.registryAccess = registryAccess;
        this.getServer = getServer;
        this.burnDuration = burnDuration;
    }

    static SfxFuelBurnTimeBridge create() {
        try {
            Class<?> craftServer = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Class<?> minecraftServer = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> fuelValues = Class.forName("net.minecraft.world.level.block.entity.FuelValues");
            Class<?> holderLookupProvider = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Class<?> featureFlagSet = Class.forName("net.minecraft.world.flag.FeatureFlagSet");
            Class<?> nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");

            Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            Method getServer = craftServer.getMethod("getServer");
            Method registryAccess = minecraftServer.getMethod("registryAccess");
            Method vanillaBurnTimes = fuelValues.getMethod("vanillaBurnTimes", holderLookupProvider, featureFlagSet);
            Method burnDuration = fuelValues.getMethod("burnDuration", nmsItemStack);
            return new SfxFuelBurnTimeBridge(asNmsCopy, vanillaBurnTimes, registryAccess, getServer, burnDuration);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to bind vanilla fuel burn time bridge", exception);
        }
    }

    int burnTicks(ItemStack stack) {
        try {
            Object craftServerInstance = Bukkit.getServer();
            Object minecraftServerInstance = getServer.invoke(craftServerInstance);
            Object registry = registryAccess.invoke(minecraftServerInstance);
            Object worldData = minecraftServerInstance.getClass().getMethod("getWorldData").invoke(minecraftServerInstance);
            Object dataConfiguration = worldData.getClass().getMethod("getDataConfiguration").invoke(worldData);
            Object featureFlags = dataConfiguration.getClass().getMethod("enabledFeatures").invoke(dataConfiguration);
            Object fuelValues = vanillaBurnTimes.invoke(null, registry, featureFlags);
            Object nmsStack = asNmsCopy.invoke(null, stack);
            return (int) burnDuration.invoke(fuelValues, nmsStack);
        } catch (ReflectiveOperationException exception) {
            return 0;
        }
    }
}
