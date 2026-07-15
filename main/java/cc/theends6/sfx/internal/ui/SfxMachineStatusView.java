package cc.theends6.sfx.internal.ui;

import cc.theends6.sfx.api.machine.runtime.SfxDurabilityBarMode;

import cc.theends6.sfx.api.machine.runtime.*;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class SfxMachineStatusView {
    private final SfxMachineStatusKey status;
    private final Material material;
    private final ItemStack icon;
    private final Component name;
    private final int progressCurrent;
    private final int progressTotal;
    private final boolean showProgress;
    private final SfxDurabilityBarMode durabilityBarMode;
    private final int timeLeftTicks;
    private final int storedEnergy;
    private final int energyCapacity;
    private final boolean showEnergy;
    private final Integer energyPerTick;
    private final Integer generatedPerTick;
    private final boolean includeDefaultStatusLore;
    private final List<Component> statusLore;
    private final List<Component> extraLore;

    private SfxMachineStatusView(Builder builder) {
        this.status = builder.status;
        this.material = builder.material;
        this.icon = builder.icon == null ? null : builder.icon.clone();
        this.name = builder.name;
        this.progressCurrent = builder.progressCurrent;
        this.progressTotal = builder.progressTotal;
        this.showProgress = builder.showProgress;
        this.durabilityBarMode = builder.durabilityBarMode;
        this.timeLeftTicks = builder.timeLeftTicks;
        this.storedEnergy = builder.storedEnergy;
        this.energyCapacity = builder.energyCapacity;
        this.showEnergy = builder.showEnergy;
        this.energyPerTick = builder.energyPerTick;
        this.generatedPerTick = builder.generatedPerTick;
        this.includeDefaultStatusLore = builder.includeDefaultStatusLore;
        this.statusLore = List.copyOf(builder.statusLore);
        this.extraLore = List.copyOf(builder.extraLore);
    }

    public SfxMachineStatusKey status() { return status; }
    public Material material() { return material; }
    public ItemStack icon() { return icon == null ? null : icon.clone(); }
    public Component name() { return name; }
    public int progressCurrent() { return progressCurrent; }
    public int progressTotal() { return progressTotal; }
    public boolean showProgress() { return showProgress; }
    public boolean showDurability() { return durabilityBarMode != SfxDurabilityBarMode.NONE; }
    public SfxDurabilityBarMode durabilityBarMode() { return durabilityBarMode; }
    public int timeLeftTicks() { return timeLeftTicks; }
    public int storedEnergy() { return storedEnergy; }
    public int energyCapacity() { return energyCapacity; }
    public boolean showEnergy() { return showEnergy; }
    public Integer energyPerTick() { return energyPerTick; }
    public Integer generatedPerTick() { return generatedPerTick; }
    public boolean includeDefaultStatusLore() { return includeDefaultStatusLore; }
    public List<Component> statusLore() { return statusLore; }
    public List<Component> extraLore() { return extraLore; }

    public static Builder builder(SfxMachineStatusKey status) {
        return new Builder(status, null, null, null);
    }

    public static Builder builder(Material material, Component name) {
        return new Builder(SfxMachineStatusKey.CUSTOM, material, null, name);
    }

    public static Builder builder(ItemStack icon, Component name) {
        return new Builder(SfxMachineStatusKey.CUSTOM, icon == null ? null : icon.getType(), icon, name);
    }

    public static final class Builder {
        private final SfxMachineStatusKey status;
        private Material material;
        private ItemStack icon;
        private Component name;
        private int progressCurrent;
        private int progressTotal;
        private boolean showProgress;
        private SfxDurabilityBarMode durabilityBarMode = SfxDurabilityBarMode.NONE;
        private int timeLeftTicks = -1;
        private int storedEnergy;
        private int energyCapacity;
        private boolean showEnergy;
        private Integer energyPerTick;
        private Integer generatedPerTick;
        private boolean includeDefaultStatusLore = true;
        private final List<Component> statusLore = new ArrayList<>();
        private final List<Component> extraLore = new ArrayList<>();

        private Builder(SfxMachineStatusKey status, Material material, ItemStack icon, Component name) {
            this.status = status == null ? SfxMachineStatusKey.CUSTOM : status;
            this.material = material;
            this.icon = icon == null ? null : icon.clone();
            this.name = name;
        }

        public Builder material(Material material) {
            this.material = material;
            return this;
        }

        public Builder icon(ItemStack icon) {
            this.icon = icon == null ? null : icon.clone();
            if (icon != null) {
                this.material = icon.getType();
            }
            return this;
        }

        public Builder name(Component name) {
            this.name = name;
            return this;
        }

        public Builder progress(int current, int total, int timeLeftTicks, boolean showDurability) {
            return progress(current, total, timeLeftTicks, showDurability ? SfxDurabilityBarMode.AUTO : SfxDurabilityBarMode.NONE);
        }

        public Builder progress(int current, int total, int timeLeftTicks, SfxDurabilityBarMode durabilityBarMode) {
            this.progressCurrent = Math.max(0, current);
            this.progressTotal = Math.max(0, total);
            this.timeLeftTicks = timeLeftTicks;
            this.showProgress = total > 0;
            this.durabilityBarMode = durabilityBarMode == null ? SfxDurabilityBarMode.NONE : durabilityBarMode;
            return this;
        }

        public Builder energy(int storedEnergy, int energyCapacity) {
            this.storedEnergy = Math.max(0, storedEnergy);
            this.energyCapacity = Math.max(0, energyCapacity);
            this.showEnergy = true;
            return this;
        }

        public Builder hideEnergy() {
            this.showEnergy = false;
            this.energyPerTick = null;
            this.generatedPerTick = null;
            return this;
        }

        public Builder consumption(Integer energyPerTick) {
            this.energyPerTick = energyPerTick == null || energyPerTick < 0 ? null : energyPerTick;
            return this;
        }

        public Builder generation(Integer generatedPerTick) {
            this.generatedPerTick = generatedPerTick == null || generatedPerTick < 0 ? null : generatedPerTick;
            return this;
        }

        public Builder includeDefaultStatusLore(boolean includeDefaultStatusLore) {
            this.includeDefaultStatusLore = includeDefaultStatusLore;
            return this;
        }

        public Builder statusLore(Component component) {
            if (component != null && !Component.empty().equals(component)) {
                statusLore.add(component);
            }
            return this;
        }

        public Builder statusLore(List<Component> components) {
            if (components != null) {
                for (Component component : components) {
                    statusLore(component);
                }
            }
            return this;
        }

        public Builder overrideStatusLore(Component component) {
            this.includeDefaultStatusLore = false;
            this.statusLore.clear();
            return statusLore(component);
        }

        public Builder overrideStatusLore(List<Component> components) {
            this.includeDefaultStatusLore = false;
            this.statusLore.clear();
            return statusLore(components);
        }

        public Builder extraLore(Component component) {
            if (component != null && !Component.empty().equals(component)) {
                extraLore.add(component);
            }
            return this;
        }

        public Builder extraLore(List<Component> components) {
            if (components != null) {
                for (Component component : components) {
                    extraLore(component);
                }
            }
            return this;
        }

        public SfxMachineStatusView build() {
            if (material == null) {
                material = SfxMachineStatusDefaults.material(status);
            }
            if (material == null) {
                material = Material.BLACK_STAINED_GLASS_PANE;
            }
            if (name == null && status == SfxMachineStatusKey.CUSTOM) {
                name = Component.text(" ");
            }
            return new SfxMachineStatusView(this);
        }
    }
}
