package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxAndroidWoodcutterPolicy {
    boolean batchReplantBottomLayer(SfxAndroidWoodcutterContext context, boolean currentDecision);
}
