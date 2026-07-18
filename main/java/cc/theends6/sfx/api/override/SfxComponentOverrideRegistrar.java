package cc.theends6.sfx.api.override;

public interface SfxComponentOverrideRegistrar {
    



    <T> void replace(SfxComponentOverrideTarget<T> target, T implementation);
}
