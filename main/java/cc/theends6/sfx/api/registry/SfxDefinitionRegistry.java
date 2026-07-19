package cc.theends6.sfx.api.registry;

import java.util.Collection;
import java.util.Optional;


public interface SfxDefinitionRegistry<T> {
    void register(String id, T definition);

    Optional<T> find(String id);

    Collection<T> definitions();
}
