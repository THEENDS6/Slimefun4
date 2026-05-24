package cc.theends6.sfx.internal.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public final class SfxModuleManager {
    private final Logger logger;
    private final List<SfxModule> modules = new ArrayList<>();

    public SfxModuleManager(Logger logger) {
        this.logger = logger;
    }

    public <T extends SfxModule> T register(T module) {
        modules.add(Objects.requireNonNull(module, "module"));
        return module;
    }

    public List<SfxModule> modules() {
        return Collections.unmodifiableList(modules);
    }

    public void loadAll() throws Exception {
        for (SfxModule module : modules) {
            module.load();
        }
    }

    public void enableAll() throws Exception {
        for (SfxModule module : modules) {
            module.enable();
        }
    }

    public void reloadAll() throws Exception {
        for (SfxModule module : modules) {
            module.reload();
        }
    }

    public void disableAllReverse() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            SfxModule module = modules.get(i);
            try {
                module.disable();
            } catch (Exception exception) {
                if (logger != null) {
                    logger.warning("Failed to disable SFX module " + module.name() + ": " + exception.getMessage());
                }
            }
        }
    }
}
