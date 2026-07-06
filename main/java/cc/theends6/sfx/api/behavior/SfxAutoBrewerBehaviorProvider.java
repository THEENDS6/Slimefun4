package cc.theends6.sfx.api.behavior;

public interface SfxAutoBrewerBehaviorProvider {
    int blazeSlot();

    int progressSlot();

    int ingredientSlot();

    int fuelDisplaySlot();

    int[] potionSlots();

    int blazeFuelTicks();

    int maxBlazeFuelTicks();

    int autoRefillThresholdTicks();

    boolean validInput(SfxAutoBrewerInputContext context);
}
