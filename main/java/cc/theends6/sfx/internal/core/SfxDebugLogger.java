package cc.theends6.sfx.internal.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class SfxDebugLogger {
    private final Logger logger;
    private final Set<String> enabledChannels = ConcurrentHashMap.newKeySet();

    public SfxDebugLogger(Logger logger) {
        this.logger = logger;
    }

    public void enable(String channel) {
        if (channel != null && !channel.isBlank()) {
            enabledChannels.add(channel.toLowerCase());
        }
    }

    public void disable(String channel) {
        if (channel != null) {
            enabledChannels.remove(channel.toLowerCase());
        }
    }

    public boolean enabled(String channel) {
        return channel != null && enabledChannels.contains(channel.toLowerCase());
    }

    public void debug(String channel, String message) {
        if (enabled(channel) && logger != null) {
            logger.info("[SFX Debug:" + channel + "] " + message);
        }
    }
}
