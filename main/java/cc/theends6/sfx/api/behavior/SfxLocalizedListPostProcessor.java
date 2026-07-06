package cc.theends6.sfx.api.behavior;

import java.util.List;

@FunctionalInterface
public interface SfxLocalizedListPostProcessor {
    List<String> apply(SfxLocalizedListContext context, List<String> currentValues);
}
