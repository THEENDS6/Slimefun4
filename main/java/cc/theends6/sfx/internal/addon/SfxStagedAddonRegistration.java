package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.addon.SfxAddonResources;
import cc.theends6.sfx.api.addon.SfxOwnedTask;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.api.container.SfxVirtualContainerType;
import cc.theends6.sfx.api.display.SfxDisplayRegistrar;
import cc.theends6.sfx.api.feature.SfxFeature;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;
import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousManualMachine;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineDefinition;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineRecipe;
import cc.theends6.sfx.api.override.SfxComponentOverrideRegistrar;
import cc.theends6.sfx.api.power.SfxPoweredItem;
import cc.theends6.sfx.api.randomtick.SfxRandomTickType;
import cc.theends6.sfx.api.registry.SfxDefinitionRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;


final class SfxStagedAddonRegistration {
    private final String owner;
    private final List<Runnable> declarations = new ArrayList<>();
    private final List<Runnable> runtimeResources = new ArrayList<>();
    private final Deque<AutoCloseable> pendingOwnedResources = new ArrayDeque<>();
    private boolean committed;

    SfxStagedAddonRegistration(String owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    SfxFeatureRegistrar features(SfxFeatureRegistrar delegate, FileConfiguration config) {
        return new StagedFeatures(delegate, config);
    }

    SfxBehaviorRegistrar behaviors(SfxBehaviorRegistrar delegate) {
        return stagedVoidInterface(SfxBehaviorRegistrar.class, delegate);
    }

    SfxComponentOverrideRegistrar overrides(SfxComponentOverrideRegistrar delegate) {
        return stagedVoidInterface(SfxComponentOverrideRegistrar.class, delegate);
    }

    SfxItemRegistry items(SfxItemRegistry delegate) {
        return new StagedItems(delegate);
    }

    SfxManualMachineRegistry machines(SfxManualMachineRegistry delegate) {
        return new StagedMachines(delegate);
    }

    SfxAddonResources resources(SfxAddonResources delegate) {
        return new StagedResources(delegate);
    }

    SfxAddonDomainRegistries.Views domains(SfxAddonDomainRegistries.Views delegate) {
        SfxDefinitionRegistry<SfxBlockType<?>> blocks = definitions(delegate.blocks());
        SfxDefinitionRegistry<SfxRandomTickType<?>> randomTicks = definitions(delegate.randomTicks());
        SfxDisplayRegistrar displays = new SfxDisplayRegistrar() {
            private final SfxDefinitionRegistry<cc.theends6.sfx.api.display.SfxDisplayCategory> categories =
                    definitions(delegate.displays().categories());
            private final SfxDefinitionRegistry<cc.theends6.sfx.api.display.SfxDisplayType> types =
                    definitions(delegate.displays().types());
            @Override public SfxDefinitionRegistry<cc.theends6.sfx.api.display.SfxDisplayCategory> categories() { return categories; }
            @Override public SfxDefinitionRegistry<cc.theends6.sfx.api.display.SfxDisplayType> types() { return types; }
        };
        return new SfxAddonDomainRegistries.Views(blocks, randomTicks, displays,
                definitions(delegate.containers()), definitions(delegate.continuousMachines()),
                definitions(delegate.poweredItems()));
    }

    synchronized void commit() {
        if (committed) return;
        for (Runnable action : List.copyOf(declarations)) action.run();
        for (Runnable action : List.copyOf(runtimeResources)) action.run();
        declarations.clear();
        runtimeResources.clear();
        pendingOwnedResources.clear();
        committed = true;
    }

    synchronized void rollbackPending() {
        if (committed) return;
        declarations.clear();
        runtimeResources.clear();
        while (!pendingOwnedResources.isEmpty()) {
            try {
                pendingOwnedResources.removeLast().close();
            } catch (Exception ignored) {
                
            }
        }
    }

    private synchronized void declare(Runnable action) {
        if (committed) action.run(); else declarations.add(action);
    }

    private synchronized void runtime(Runnable action) {
        if (committed) action.run(); else runtimeResources.add(action);
    }

    @SuppressWarnings("unchecked")
    private <T> T stagedVoidInterface(Class<T> contract, T delegate) {
        return (T) Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] {contract}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "staged(" + delegate + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> null;
                };
            }
            if (method.getReturnType() != Void.TYPE) {
                throw new IllegalStateException("Staged registrar method must return void: " + method);
            }
            Object[] snapshot = args == null ? new Object[0] : args.clone();
            declare(() -> invoke(delegate, method, snapshot));
            return null;
        });
    }

    private static void invoke(Object delegate, java.lang.reflect.Method method, Object[] args) {
        try {
            method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    <T> SfxDefinitionRegistry<T> definitions(SfxDefinitionRegistry<T> delegate) {
        return new SfxDefinitionRegistry<>() {
            private final Map<String, T> staged = new LinkedHashMap<>();

            @Override public synchronized void register(String id, T definition) {
                if (id == null || id.isBlank()) throw new IllegalArgumentException("Definition id must not be blank");
                if (staged.containsKey(id) || delegate.find(id).isPresent()) {
                    throw new IllegalStateException("Duplicate SFX definition id: " + id);
                }
                staged.put(id, Objects.requireNonNull(definition, "definition"));
                declare(() -> delegate.register(id, definition));
            }

            @Override public synchronized Optional<T> find(String id) {
                T local = staged.get(id);
                return local == null ? delegate.find(id) : Optional.of(local);
            }

            @Override public synchronized Collection<T> definitions() {
                List<T> values = new ArrayList<>(delegate.definitions());
                values.addAll(staged.values());
                return List.copyOf(values);
            }
        };
    }

    private final class StagedFeatures implements SfxFeatureRegistrar {
        private final SfxFeatureRegistrar delegate;
        private final FileConfiguration config;
        private final Map<String, SfxFeature> staged = new LinkedHashMap<>();

        private StagedFeatures(SfxFeatureRegistrar delegate, FileConfiguration config) {
            this.delegate = delegate;
            this.config = config;
        }

        @Override public synchronized void registerBoolean(String id, String path, boolean defaultEnabled) {
            if (id == null || id.isBlank() || path == null || path.isBlank()) {
                throw new IllegalArgumentException("Feature id and config path must not be blank");
            }
            if (staged.containsKey(id) || delegate.feature(id).isPresent()) {
                throw new IllegalStateException("Duplicate SFX feature id: " + id);
            }
            staged.put(id, new SfxFeature(id, owner, path, defaultEnabled, config.getBoolean(path, defaultEnabled)));
            declare(() -> delegate.registerBoolean(id, path, defaultEnabled));
        }

        @Override public synchronized Optional<SfxFeature> feature(String id) {
            SfxFeature local = staged.get(id);
            return local == null ? delegate.feature(id) : Optional.of(local);
        }

        @Override public synchronized Collection<SfxFeature> features() {
            List<SfxFeature> values = new ArrayList<>(delegate.features());
            values.addAll(staged.values());
            return List.copyOf(values);
        }

        @Override public boolean enabled(String id) { return feature(id).map(SfxFeature::enabled).orElse(false); }
    }

    private final class StagedItems implements SfxItemRegistry {
        private final SfxItemRegistry delegate;
        private final Map<String, SfxItemCategory> categories = new LinkedHashMap<>();
        private final Map<String, SfxItemDefinition> items = new LinkedHashMap<>();

        private StagedItems(SfxItemRegistry delegate) { this.delegate = delegate; }

        @Override public synchronized void registerCategory(SfxItemCategory category) {
            String id = category.id();
            if (categories.containsKey(id) || delegate.category(id).isPresent()) throw new IllegalArgumentException("Duplicate SFX category: " + id);
            categories.put(id, category);
            declare(() -> delegate.registerCategory(category));
        }

        @Override public synchronized void registerItem(SfxItemDefinition definition) {
            String id = definition.id();
            if (items.containsKey(id) || delegate.item(id).isPresent()) throw new IllegalArgumentException("Duplicate SFX item: " + id);
            if (definition.categoryId() != null && category(definition.categoryId()).isEmpty()) {
                throw new IllegalArgumentException("Unknown category '" + definition.categoryId() + "' for item " + id);
            }
            items.put(id, definition);
            declare(() -> delegate.registerItem(definition));
        }

        @Override public synchronized Optional<SfxItemCategory> category(String id) {
            SfxItemCategory local = categories.get(SfxItemCategory.normalizeId(id));
            return local == null ? delegate.category(id) : Optional.of(local);
        }

        @Override public synchronized Optional<SfxItemDefinition> item(String id) {
            SfxItemDefinition local = items.get(SfxItemDefinition.normalizeId(id));
            return local == null ? delegate.item(id) : Optional.of(local);
        }

        @Override public synchronized Collection<SfxItemCategory> categories() {
            List<SfxItemCategory> values = new ArrayList<>(delegate.categories()); values.addAll(categories.values()); return List.copyOf(values);
        }
        @Override public synchronized Collection<SfxItemDefinition> items() {
            List<SfxItemDefinition> values = new ArrayList<>(delegate.items()); values.addAll(items.values()); return List.copyOf(values);
        }
        @Override public synchronized Collection<SfxItemDefinition> visibleItemsInCategory(String categoryId) {
            List<SfxItemDefinition> values = new ArrayList<>(delegate.visibleItemsInCategory(categoryId));
            items.values().stream().filter(item -> !item.hidden() && Objects.equals(item.categoryId(), SfxItemCategory.normalizeId(categoryId))).forEach(values::add);
            return List.copyOf(values);
        }
    }

    private final class StagedMachines implements SfxManualMachineRegistry {
        private final SfxManualMachineRegistry delegate;
        private final Map<String, SfxManualMachineDefinition> machines = new LinkedHashMap<>();
        private final Map<String, List<SfxManualMachineRecipe>> recipes = new LinkedHashMap<>();

        private StagedMachines(SfxManualMachineRegistry delegate) { this.delegate = delegate; }

        @Override public synchronized void registerMachine(SfxManualMachineDefinition definition) {
            if (machines.containsKey(definition.id()) || delegate.machine(definition.id()).isPresent()) {
                throw new IllegalArgumentException("Duplicate SFX manual machine: " + definition.id());
            }
            machines.put(definition.id(), definition);
            declare(() -> delegate.registerMachine(definition));
        }
        @Override public synchronized void registerRecipe(SfxManualMachineRecipe recipe) {
            if (machine(recipe.machineId()).isEmpty()) throw new IllegalArgumentException("Unknown manual machine for recipe: " + recipe.machineId());
            recipes.computeIfAbsent(recipe.machineId(), ignored -> new ArrayList<>()).add(recipe);
            declare(() -> delegate.registerRecipe(recipe));
        }
        @Override public synchronized Optional<SfxManualMachineDefinition> machine(String id) {
            SfxManualMachineDefinition local = machines.get(SfxItemDefinition.normalizeId(id));
            return local == null ? delegate.machine(id) : Optional.of(local);
        }
        @Override public synchronized Collection<SfxManualMachineDefinition> machines() {
            List<SfxManualMachineDefinition> values = new ArrayList<>(delegate.machines()); values.addAll(machines.values()); return List.copyOf(values);
        }
        @Override public synchronized Collection<SfxManualMachineRecipe> recipesFor(String machineId) {
            List<SfxManualMachineRecipe> values = new ArrayList<>(delegate.recipesFor(machineId));
            values.addAll(recipes.getOrDefault(SfxItemDefinition.normalizeId(machineId), List.of())); return List.copyOf(values);
        }
    }

    private final class StagedResources implements SfxAddonResources {
        private final SfxAddonResources delegate;
        private StagedResources(SfxAddonResources delegate) { this.delegate = delegate; }

        @Override public <T extends Listener> T registerListener(T listener) {
            runtime(() -> delegate.registerListener(listener)); return listener;
        }
        @Override public SfxOwnedTask runGlobal(Runnable task) { return defer(() -> delegate.runGlobal(task)); }
        @Override public SfxOwnedTask runGlobalLater(long delay, Runnable task) { return defer(() -> delegate.runGlobalLater(delay, task)); }
        @Override public SfxOwnedTask runGlobalRepeating(long delay, long period, Runnable task) { return defer(() -> delegate.runGlobalRepeating(delay, period, task)); }
        @Override public SfxOwnedTask runRegion(Location location, Runnable task) { return defer(() -> delegate.runRegion(location, task)); }
        @Override public SfxOwnedTask runRegionLater(Location location, long delay, Runnable task) { return defer(() -> delegate.runRegionLater(location, delay, task)); }
        @Override public SfxOwnedTask runRegionRepeating(Location location, long delay, long period, Runnable task) { return defer(() -> delegate.runRegionRepeating(location, delay, period, task)); }
        @Override public SfxOwnedTask runEntity(Entity entity, Runnable task) { return defer(() -> delegate.runEntity(entity, task)); }
        @Override public SfxOwnedTask runAsync(Runnable task) { return defer(() -> delegate.runAsync(task)); }
        @Override public SfxOwnedTask runAsyncRepeating(Duration delay, Duration period, Runnable task) { return defer(() -> delegate.runAsyncRepeating(delay, period, task)); }

        @Override public synchronized <T extends AutoCloseable> T own(T resource) {
            Objects.requireNonNull(resource, "resource");
            if (committed) return delegate.own(resource);
            pendingOwnedResources.addLast(resource);
            runtime(() -> {
                pendingOwnedResources.remove(resource);
                delegate.own(resource);
            });
            return resource;
        }

        private SfxOwnedTask defer(java.util.function.Supplier<SfxOwnedTask> supplier) {
            DeferredTask handle = new DeferredTask();
            runtime(() -> handle.bind(supplier.get()));
            return handle;
        }
    }

    private static final class DeferredTask implements SfxOwnedTask {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile SfxOwnedTask delegate;
        private void bind(SfxOwnedTask task) {
            delegate = task;
            if (cancelled.get()) task.cancel();
        }
        @Override public void cancel() { cancelled.set(true); SfxOwnedTask task = delegate; if (task != null) task.cancel(); }
        @Override public boolean cancelled() { SfxOwnedTask task = delegate; return cancelled.get() || task != null && task.cancelled(); }
    }
}
