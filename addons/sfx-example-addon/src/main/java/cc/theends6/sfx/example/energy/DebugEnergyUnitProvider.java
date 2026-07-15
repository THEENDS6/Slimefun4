package cc.theends6.sfx.example.energy;

import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.energy.runtime.SfxDynamicEnergyGeneratorProvider;
import cc.theends6.sfx.api.energy.runtime.SfxEnergyComponentDefinition;
import cc.theends6.sfx.api.energy.runtime.SfxEnergyGeneratorAccess;
import cc.theends6.sfx.api.energy.runtime.SfxEnergyNodeState;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import cc.theends6.sfx.api.machine.runtime.SfxMachineStatusKey;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;


public final class DebugEnergyUnitProvider implements SfxDynamicEnergyGeneratorProvider {
    private static final int STOPPED = 0;
    private static final int GENERATING = 1;
    private static final int CONSUMING = 2;

    private final int defaultRate;
    private final int defaultCapacity;
    private final int maxRate;
    private final int maxCapacity;
    private final SfxAddonContext context;

    public DebugEnergyUnitProvider(SfxAddonContext context) {
        this.context = context;
        maxRate = clamp(context.configInt("debug-energy-unit.max-rate", 1_000_000), 1, Integer.MAX_VALUE);
        maxCapacity = clamp(context.configInt("debug-energy-unit.max-capacity", 1_000_000_000), 1, Integer.MAX_VALUE);
        defaultRate = clamp(context.configInt("debug-energy-unit.default-rate", 100), 1, maxRate);
        defaultCapacity = clamp(context.configInt("debug-energy-unit.default-capacity", 100_000), 1, maxCapacity);
    }

    @Override
    public int potentialGeneration(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition,
                                   SfxEnergyNodeState state, Location location) {
        validate(state);
        return mode(state) == GENERATING ? rate(state) : 0;
    }

    @Override
    public int generate(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition,
                        SfxEnergyNodeState state, Location location, SfxEnergyGeneratorAccess access) {
        validate(state);
        return mode(state) == GENERATING ? rate(state) : 0;
    }

    @Override
    public int requestedConsumption(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition,
                                    SfxEnergyNodeState state, Location location) {
        validate(state);
        if (mode(state) != CONSUMING) {
            return 0;
        }
        return Math.min(rate(state), Math.max(0, capacity(state) - state.storedEnergy()));
    }

    @Override
    public int acceptEnergy(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition,
                            SfxEnergyNodeState state, Location location, int offered, SfxEnergyGeneratorAccess access) {
        validate(state);
        int accepted = Math.min(Math.max(0, offered), Math.max(0, capacity(state) - state.storedEnergy()));
        if (accepted > 0) {
            state.storedEnergy(state.storedEnergy() + accepted);
            access.markDirty();
        }
        return accepted;
    }

    @Override
    public boolean managesStoredEnergy() {
        return true;
    }

    @Override
    public boolean excludeFromAutoPause(cc.theends6.sfx.api.block.SfxBlockInstanceRecord instance,
                                        SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        return true;
    }

    @Override
    public boolean handleMenuClick(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition,
                                   SfxEnergyNodeState state, int rawSlot, ClickType clickType) {
        validate(state);
        int step = clickType.isShiftClick() ? 1000 : clickType.isRightClick() ? 10 : 1;
        switch (rawSlot) {
            case 4 -> state.specialData((mode(state) + 1) % 3);
            case 20 -> state.specialData2(clamp(rate(state) - step, 1, maxRate));
            case 24 -> state.specialData2(clamp(rate(state) + step, 1, maxRate));
            case 30 -> {
                state.specialData3(clamp(capacity(state) - step * 100, 1, maxCapacity));
                state.storedEnergy(Math.min(state.storedEnergy(), capacity(state)));
            }
            case 32 -> state.specialData3(clampLong((long) capacity(state) + step * 100L, 1, maxCapacity));
            case 38 -> state.storedEnergy(0);
            case 42 -> state.storedEnergy(capacity(state));
            default -> { return false; }
        }
        validate(state);
        return true;
    }

    @Override
    public boolean handleMenuClick(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition,
                                   SfxEnergyNodeState state, Location location, Player player, int rawSlot,
                                   ClickType clickType, SfxEnergyGeneratorAccess access) {
        if (rawSlot == 13 || rawSlot == 31) {
            boolean rateInput = rawSlot == 13;
            player.closeInventory();
            player.sendMessage(rateInput
                    ? "§e请输入新的功率（1-" + maxRate + "），输入 cancel 取消。"
                    : "§e请输入新的容量（1-" + maxCapacity + "），输入 cancel 取消。");
            String owner = "sfx-example:debug-energy-" + (rateInput ? "rate" : "capacity");
            context.api().chatInput().await(player, owner, Duration.ofSeconds(30), input -> {
                if (input.equalsIgnoreCase("cancel")) return;
                try {
                    long parsed = Long.parseLong(input.trim());
                    if (rateInput) {
                        state.specialData2(clampLong(parsed, 1, maxRate));
                    } else {
                        state.specialData3(clampLong(parsed, 1, maxCapacity));
                        state.storedEnergy(Math.min(state.storedEnergy(), capacity(state)));
                    }
                    validate(state);
                    access.markDirty();
                    player.sendMessage("§aDEBUG 电力单元参数已更新。重新打开方块即可查看。");
                } catch (NumberFormatException exception) {
                    player.sendMessage("§c无效整数，参数未修改。");
                }
            }, () -> player.sendMessage("§7输入已超时，参数未修改。"));
            return true;
        }
        return handleMenuClick(plugin, items, definition, state, rawSlot, clickType);
    }

    @Override
    public Map<Integer, SfxMachineDisplayItem> displayItems(JavaPlugin plugin, SfxItems items,
                                                            SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        validate(state);
        Map<String, Object> values = Map.of(
                "mode", switch (mode(state)) { case GENERATING -> "GEN"; case CONSUMING -> "LOAD"; default -> "STOP"; },
                "rate", rate(state), "stored", state.storedEnergy(), "capacity", capacity(state));
        return Map.of(
                4, item(Material.COMPARATOR, "example-energy.mode.name", "example-energy.mode.lore", values, mode(state) != STOPPED),
                13, item(Material.NAME_TAG, "example-energy.rate-exact.name", "example-energy.rate.lore", values, false),
                20, item(Material.RED_DYE, "example-energy.rate-down.name", "example-energy.rate.lore", values, false),
                24, item(Material.LIME_DYE, "example-energy.rate-up.name", "example-energy.rate.lore", values, false),
                30, item(Material.REDSTONE, "example-energy.capacity-down.name", "example-energy.capacity.lore", values, false),
                31, item(Material.NAME_TAG, "example-energy.capacity-exact.name", "example-energy.capacity.lore", values, false),
                32, item(Material.REDSTONE_BLOCK, "example-energy.capacity-up.name", "example-energy.capacity.lore", values, false),
                38, item(Material.BUCKET, "example-energy.empty.name", "example-energy.storage.lore", values, false),
                42, new SfxMachineDisplayItem(Material.LAVA_BUCKET, "example-energy.fill.name",
                        List.of("example-energy.storage.lore"), values, false, state.storedEnergy(), capacity(state)));
    }

    @Override
    public SfxMachineStatusKey status(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition,
                                      SfxEnergyNodeState state, Location location, SfxEnergyGeneratorAccess access) {
        validate(state);
        return mode(state) == STOPPED ? SfxMachineStatusKey.PAUSED : SfxMachineStatusKey.WORKING;
    }

    private SfxMachineDisplayItem item(Material material, String name, String lore, Map<String, Object> values, boolean glint) {
        return new SfxMachineDisplayItem(material, name, List.of(lore), values, glint, 0, 0);
    }

    private void validate(SfxEnergyNodeState state) {
        if (state.specialData2() <= 0) state.specialData2(defaultRate);
        if (state.specialData3() <= 0) state.specialData3(defaultCapacity);
        state.specialData(Math.min(CONSUMING, Math.max(STOPPED, state.specialData())));
        state.specialData2(clamp(state.specialData2(), 1, maxRate));
        state.specialData3(clamp(state.specialData3(), 1, maxCapacity));
        state.storedEnergy(Math.min(state.storedEnergy(), capacity(state)));
    }

    private int mode(SfxEnergyNodeState state) { return state.specialData(); }
    private int rate(SfxEnergyNodeState state) { return state.specialData2(); }
    private int capacity(SfxEnergyNodeState state) { return state.specialData3(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static int clampLong(long value, int min, int max) { return (int) Math.max(min, Math.min((long) max, value)); }
}
