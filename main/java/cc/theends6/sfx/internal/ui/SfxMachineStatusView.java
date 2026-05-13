package cc.theends6.sfx.internal.ui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class SfxMachineStatusView {
    private final Material material;
    private final ItemStack icon;
    private final Component name;
    private final int progressCurrent;
    private final int progressTotal;
    private final boolean showProgress;
    private final boolean showDurability;
    private final int timeLeftTicks;
    private final int storedEnergy;
    private final int energyCapacity;
    private final Integer energyPerTick;
    private final Integer generatedPerTick;
    private final List<Component> statusLore;
    private final List<Component> extraLore;

    private SfxMachineStatusView(Builder builder) {
        this.material = builder.material;
        this.icon = builder.icon == null ? null : builder.icon.clone();
        this.name = builder.name;
        this.progressCurrent = builder.progressCurrent;
        this.progressTotal = builder.progressTotal;
        this.showProgress = builder.showProgress;
        this.showDurability = builder.showDurability;
        this.timeLeftTicks = builder.timeLeftTicks;
        this.storedEnergy = builder.storedEnergy;
        this.energyCapacity = builder.energyCapacity;
        this.energyPerTick = builder.energyPerTick;
        this.generatedPerTick = builder.generatedPerTick;
        this.statusLore = List.copyOf(builder.statusLore);
        this.extraLore = List.copyOf(builder.extraLore);
    }

    public Material material() { return material; }
    public ItemStack icon() { return icon == null ? null : icon.clone(); }
    public Component name() { return name; }
    public int progressCurrent() { return progressCurrent; }
    public int progressTotal() { return progressTotal; }
    public boolean showProgress() { return showProgress; }
    public boolean showDurability() { return showDurability; }
    public int timeLeftTicks() { return timeLeftTicks; }
    public int storedEnergy() { return storedEnergy; }
    public int energyCapacity() { return energyCapacity; }
    public Integer energyPerTick() { return energyPerTick; }
    public Integer generatedPerTick() { return generatedPerTick; }
    public List<Component> statusLore() { return statusLore; }
    public List<Component> extraLore() { return extraLore; }

    public static Builder builder(Material material, Component name) {
        return new Builder(material, null, name);
    }

    public static Builder builder(ItemStack icon, Component name) {
        return new Builder(icon == null ? null : icon.getType(), icon, name);
    }

    public static final class Builder {
        private final Material material;
    private final ItemStack icon;
        private final Component name;
        private int progressCurrent;
        private int progressTotal;
        private boolean showProgress;
        private boolean showDurability;
        private int timeLeftTicks;
        private int storedEnergy;
        private int energyCapacity;
        private Integer energyPerTick;
        private Integer generatedPerTick;
        private final List<Component> statusLore = new ArrayList<>();
        private final List<Component> extraLore = new ArrayList<>();

        private Builder(Material material, ItemStack icon, Component name) {
            Material resolved = material == null ? Material.BLACK_STAINED_GLASS_PANE : material;
            this.material = resolved;
            this.icon = icon == null ? null : icon.clone();
            this.name = name == null ? Component.text(" ") : name;
        }

        public Builder progress(int current, int total, int timeLeftTicks, boolean showDurability) {
            this.progressCurrent = Math.max(0, current);
            this.progressTotal = Math.max(0, total);
            this.timeLeftTicks = timeLeftTicks;
            this.showProgress = total > 0;
            this.showDurability = showDurability;
            return this;
        }

        public Builder energy(int storedEnergy, int energyCapacity) {
            this.storedEnergy = Math.max(0, storedEnergy);
            this.energyCapacity = Math.max(0, energyCapacity);
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
            return new SfxMachineStatusView(this);
        }
    }
}
