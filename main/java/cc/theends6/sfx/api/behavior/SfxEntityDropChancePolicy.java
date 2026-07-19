package cc.theends6.sfx.api.behavior;


@FunctionalInterface
public interface SfxEntityDropChancePolicy {
    double chance(SfxEntityDropContext context, double currentChance);
}
