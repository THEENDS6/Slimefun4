package cc.theends6.sfx.api.behavior;

import java.util.List;

@FunctionalInterface
public interface SfxRechargeableItemProvider {
    List<SfxRechargeableItemDefinition> definitions();
}
