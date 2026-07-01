package cc.theends6.sfx;




final class SfxPluginPreflight {
    private SfxPluginPreflight() {
    }

    static boolean prepare(SlimeFunXPlugin plugin) {
        if (plugin.packetEventsUnavailable || !plugin.packetEventsLoaded) {
            if (!plugin.packetEventsUnavailable) {
                plugin.logPacketEventsStartupFailure(new IllegalStateException("PacketEvents was not initialized during onLoad"));
            }
            plugin.getLogger().severe("SlimeFunX is disabling because PacketEvents is not available or failed to initialize.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return false;
        }
        if (plugin.machineRuntime != null) {
            plugin.machineRuntime.clear();
        }
        if (plugin.packetEventsLoaded) {
            try {
                if (plugin.packetEventsApiBoolean("isTerminated")) {
                    throw new IllegalStateException("PacketEvents API is terminated");
                }
                if (!plugin.packetEventsApiBoolean("isLoaded")) {
                    throw new IllegalStateException("PacketEvents API is not loaded");
                }
                if (!plugin.packetEventsApiBoolean("isInitialized")) {
                    throw new IllegalStateException("PacketEvents API is not initialized");
                }
            } catch (Throwable throwable) {
                plugin.logPacketEventsStartupFailure(throwable);
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return false;
            }
        }
        return true;
    }
}
