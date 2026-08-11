package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;

import cc.theends6.sfx.api.block.*;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.internal.machine.SfxMachinePhaseContext;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.api.machine.runtime.SfxMachineTickContext;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;




final class SfxEnhancedFurnaceTickController {
    private SfxEnhancedFurnaceTickController() {
    }

    static SfxMachinePhaseResult tick(SfxBasicMachineBlockListener service, SfxMachinePhaseContext phaseContext) {
        Block block = phaseContext.attachment("basic.block", Block.class).orElse(null);
        SfxBlockAnchorKey key = phaseContext.attachment("basic.furnaceKey", SfxBlockAnchorKey.class).orElse(null);
        FurnaceStats stats = phaseContext.attachment("basic.furnaceStats", FurnaceStats.class).orElse(null);
        VirtualFurnaceState state = phaseContext.attachment("basic.furnaceState", VirtualFurnaceState.class).orElse(null);
        SfxMachineTickContext context = phaseContext.tickContext();
        if (block == null || key == null || stats == null || state == null || context == null) {
            return SfxMachinePhaseResult.cont();
        }
        if (block.getType() != Material.FURNACE) {
            service.enhancedFurnaces.remove(key);
            service.forgetEnhancedFurnace(key);
            service.virtualFurnaces.remove(key);
            service.viewedFurnaces.remove(key);
            phaseContext.put("basic.furnace.status", cc.theends6.sfx.internal.machine.SfxMachineStatus.ERROR);
            return SfxMachinePhaseResult.failed("enhanced furnace block missing");
        }
        if (!state.initialized() || state.externalDirty()) {
            service.hydrateVirtualFurnaceFromWorld(block, state);
        }
        if (!state.initialized()) {
            phaseContext.put("basic.furnace.status", cc.theends6.sfx.internal.machine.SfxMachineStatus.ERROR);
            return SfxMachinePhaseResult.failed("virtual furnace not initialized");
        }
        state.sleeping(false);

        int elapsed = Math.max(1, context.elapsedTicksInt());
        int cookTime = service.currentCookTime(state);
        boolean forceVisual = false;
        boolean outputBlocked = false;

        for (int tick = 0; tick < elapsed; tick++) {
            ItemStack input = state.smelting();
            VirtualFurnaceRecipe recipe = service.resolveFurnaceRecipe(input).orElse(null);
            if (recipe == null || input == null || input.getType().isAir()) {
                if (state.cookProgress() != 0 || state.inputKey() != null) {
                    forceVisual = true;
                }
                state.cookProgress(0);
                state.inputKey(null);
                service.burnOneVirtualFuelTick(state);
                continue;
            }

            cookTime = recipe.cookingTime();
            String inputKey = service.inputKey(input);
            if (!inputKey.equals(state.inputKey())) {
                state.inputKey(inputKey);
                state.cookProgress(0);
                forceVisual = true;
            }

            ItemStack result = service.applyEnhancedFurnaceFortune(recipe.result(), input.getType(), stats);
            boolean canSmelt = service.canFitResult(state, result);
            if (!canSmelt) {
                outputBlocked = true;
                if (state.cookProgress() != 0) {
                    state.cookProgress(0);
                    forceVisual = true;
                }
                service.burnOneVirtualFuelTick(state);
                continue;
            }

            if (state.burnTimeRemaining() <= 0) {
                ItemStack fuel = state.fuel();
                int burnTicks = service.enhancedFuelTicks(fuel, stats);
                if (burnTicks <= 0) {
                    if (state.cookProgress() != 0) {
                        state.cookProgress(0);
                        forceVisual = true;
                    }
                    break;
                }
                service.consumeFuel(state, fuel);
                state.burnTimeRemaining(burnTicks);
                state.burnTimeTotal(burnTicks);
                forceVisual = true;
            }

            if (state.burnTimeRemaining() > 0) {
                service.burnOneVirtualFuelTick(state);
                state.cookProgress(state.cookProgress() + Math.max(1, stats.processingSpeed()));
                if (state.cookProgress() >= cookTime) {
                    service.consumeSmeltingInput(state, input);
                    service.pushFurnaceResult(state, result);
                    state.cookProgress(0);
                    ItemStack next = state.smelting();
                    state.inputKey(next == null || next.getType().isAir() ? null : service.inputKey(next));
                    forceVisual = true;
                }
            }
        }

        if (!context.hasViewers() && state.burnTimeRemaining() <= 0 && !service.canStartOrContinueVirtualSmelting(state, stats)) {
            state.sleeping(true);
        }
        phaseContext.put("basic.furnace.forceVisual", forceVisual);
        phaseContext.put("basic.furnace.cookTime", cookTime);
        cc.theends6.sfx.internal.machine.SfxMachineStatus furnaceStatus = SfxBasicMachineFrameworkBridge.furnaceStatus(state.initialized(), state.cookProgress() > 0, outputBlocked, state.burnTimeRemaining() > 0);
        phaseContext.put("basic.furnace.status", furnaceStatus);
        return outputBlocked
                ? SfxMachinePhaseResult.blocked(cc.theends6.sfx.internal.machine.SfxMachineStatus.OUTPUT_FULL, "enhanced furnace output blocked")
                : SfxMachinePhaseResult.complete(furnaceStatus, "enhanced furnace tick executed through framework effect");
    
    }
}
