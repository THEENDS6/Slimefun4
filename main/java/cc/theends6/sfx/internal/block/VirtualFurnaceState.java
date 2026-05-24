package cc.theends6.sfx.internal.block;

import org.bukkit.inventory.ItemStack;

final class VirtualFurnaceState {
    private ItemStack smelting;
    private ItemStack fuel;
    private ItemStack result;
    private int burnTimeRemaining;
    private int burnTimeTotal;
    private int cookProgress;
    private int visualTick;
    private int cookTimeTotal = 200;
    private long lastLogicTick;
    private boolean sleeping;
    private boolean initialized;
    private boolean externalDirty = true;
    private boolean mirrorDirty;
    private Boolean lastLit;
    private String inputKey;

    ItemStack smelting() {
        return cloneSlotInternal(smelting);
    }

    void smelting(ItemStack smelting) {
        this.smelting = cloneSlotInternal(smelting);
    }

    ItemStack fuel() {
        return cloneSlotInternal(fuel);
    }

    void fuel(ItemStack fuel) {
        this.fuel = cloneSlotInternal(fuel);
    }

    ItemStack result() {
        return cloneSlotInternal(result);
    }

    void result(ItemStack result) {
        this.result = cloneSlotInternal(result);
    }

    int burnTimeRemaining() {
        return burnTimeRemaining;
    }

    void burnTimeRemaining(int burnTimeRemaining) {
        this.burnTimeRemaining = Math.max(0, burnTimeRemaining);
    }

    void burnTimeTotal(int burnTimeTotal) {
        this.burnTimeTotal = Math.max(0, burnTimeTotal);
    }

    int cookTimeTotal() {
        return Math.max(1, cookTimeTotal);
    }

    void cookTimeTotal(int cookTimeTotal) {
        this.cookTimeTotal = Math.max(1, cookTimeTotal);
    }

    int cookProgress() {
        return cookProgress;
    }

    void cookProgress(int cookProgress) {
        this.cookProgress = Math.max(0, cookProgress);
    }

    int visualTick() {
        return visualTick;
    }

    void visualTick(int visualTick) {
        this.visualTick = visualTick;
    }

    long lastLogicTick() {
        return lastLogicTick;
    }

    void lastLogicTick(long lastLogicTick) {
        this.lastLogicTick = lastLogicTick;
    }

    boolean sleeping() {
        return sleeping;
    }

    void sleeping(boolean sleeping) {
        this.sleeping = sleeping;
    }

    boolean initialized() {
        return initialized;
    }

    void initialized(boolean initialized) {
        this.initialized = initialized;
    }

    boolean externalDirty() {
        return externalDirty;
    }

    void externalDirty(boolean externalDirty) {
        this.externalDirty = externalDirty;
    }

    boolean mirrorDirty() {
        return mirrorDirty;
    }

    void mirrorDirty(boolean mirrorDirty) {
        this.mirrorDirty = mirrorDirty;
    }

    Boolean lastLit() {
        return lastLit;
    }

    void lastLit(boolean lastLit) {
        this.lastLit = lastLit;
    }

    String inputKey() {
        return inputKey;
    }

    void inputKey(String inputKey) {
        this.inputKey = inputKey;
    }

    private static ItemStack cloneSlotInternal(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }
}
