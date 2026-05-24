package cc.theends6.sfx.internal.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        for (SfxModule module : orderedModules()) {
            module.load();
        }
    }

    public void enableAll() throws Exception {
        for (SfxModule module : orderedModules()) {
            module.enable();
        }
    }

    public void reloadAll() throws Exception {
        for (SfxModule module : orderedModules()) {
            module.reload();
        }
    }

    public void disableAllReverse() {
        List<SfxModule> ordered = orderedModulesLenient();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            SfxModule module = ordered.get(i);
            try {
                module.disable();
            } catch (Exception exception) {
                if (logger != null) {
                    logger.warning("Failed to disable SFX module " + module.name() + ": " + exception.getMessage());
                }
            }
        }
    }

    private List<SfxModule> orderedModulesLenient() {
        try {
            return orderedModules();
        } catch (Exception exception) {
            if (logger != null) {
                logger.warning("Falling back to registration order for SFX module shutdown: " + exception.getMessage());
            }
            return List.copyOf(modules);
        }
    }

    private List<SfxModule> orderedModules() throws Exception {
        Map<String, SfxModule> byName = new LinkedHashMap<>();
        for (SfxModule module : modules) {
            SfxModule duplicate = byName.putIfAbsent(module.name(), module);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate SFX module name: " + module.name());
            }
        }
        List<SfxModule> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (SfxModule module : modules) {
            visit(module, byName, visiting, visited, ordered);
        }
        return ordered;
    }

    private void visit(SfxModule module, Map<String, SfxModule> byName, Set<String> visiting, Set<String> visited, List<SfxModule> ordered) {
        String name = module.name();
        if (visited.contains(name)) {
            return;
        }
        if (!visiting.add(name)) {
            throw new IllegalStateException("Cycle in SFX module dependencies at " + name);
        }
        for (String dependencyName : module.dependsOn()) {
            if (dependencyName == null || dependencyName.isBlank()) {
                continue;
            }
            SfxModule dependency = byName.get(dependencyName);
            if (dependency == null) {
                throw new IllegalStateException("SFX module " + name + " depends on missing module " + dependencyName);
            }
            visit(dependency, byName, visiting, visited, ordered);
        }
        visiting.remove(name);
        visited.add(name);
        ordered.add(module);
    }
}
