package cc.theends6.sfx.internal.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Logger;

public final class SfxShutdownStack {
    private record Entry(String name, Runnable action) {
    }

    private final Logger logger;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public SfxShutdownStack(Logger logger) {
        this.logger = logger;
    }

    public void push(String name, Runnable action) {
        entries.push(new Entry(name == null ? "unnamed" : name, Objects.requireNonNull(action, "action")));
    }

    public void runAll() {
        while (!entries.isEmpty()) {
            Entry entry = entries.pop();
            try {
                entry.action().run();
            } catch (Throwable throwable) {
                if (logger != null) {
                    logger.warning("Failed during SFX shutdown step " + entry.name() + ": " + throwable.getMessage());
                }
            }
        }
    }
}
