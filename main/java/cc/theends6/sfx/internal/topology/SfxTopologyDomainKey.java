package cc.theends6.sfx.internal.topology;

import java.util.Objects;

public record SfxTopologyDomainKey(String namespace, String key) {
    public SfxTopologyDomainKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
    }

    public static SfxTopologyDomainKey of(String namespace, String key) {
        return new SfxTopologyDomainKey(namespace, key);
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }
}
