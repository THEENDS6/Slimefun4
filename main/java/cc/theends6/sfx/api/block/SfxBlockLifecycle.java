package cc.theends6.sfx.api.block;

import org.bukkit.Material;

public interface SfxBlockLifecycle<S> {
    default void onPlace(SfxBlockEventContext<S> context) {}
    default void onLoad(SfxBlockEventContext<S> context) {}
    default void onUnload(SfxBlockEventContext<S> context) {}
    default void onBreak(SfxBlockEventContext<S> context) {}
    default void onExplosion(SfxBlockEventContext<S> context) { onBreak(context); }
    default void onPistonMove(SfxBlockEventContext<S> context) {}
    default void onFluidBreak(SfxBlockEventContext<S> context) { onBreak(context); }
    default void onPhysicsUpdate(SfxBlockEventContext<S> context) {}
    default void onNeighborUpdate(SfxBlockEventContext<S> context) {}
    default void onInteract(SfxBlockEventContext<S> context) {}
    default SfxBlockTransformDecision onVanillaTransform(SfxBlockEventContext<S> context,
                                                         Material from, Material to) {
        return SfxBlockTransformDecision.allow();
    }
}
