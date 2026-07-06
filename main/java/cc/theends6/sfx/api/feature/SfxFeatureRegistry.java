package cc.theends6.sfx.api.feature;

import java.util.Collection;
import java.util.Optional;

public interface SfxFeatureRegistry {
    Optional<SfxFeature> feature(String id);

    Collection<SfxFeature> features();

    boolean enabled(String id);
}
