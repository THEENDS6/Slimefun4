package cc.theends6.sfx.api.block;

import java.util.Objects;
import java.util.function.BiFunction;

public record SfxBlockStateSchema<S>(int version, SfxBlockStateCodec<S> codec,
                                     BiFunction<Integer, byte[], byte[]> migration) {
    public SfxBlockStateSchema {
        if (version < 1) throw new IllegalArgumentException("State schema version must be at least 1");
        Objects.requireNonNull(codec, "codec");
        migration = migration == null ? (oldVersion, payload) -> payload : migration;
    }

    public S decode(int storedVersion, byte[] payload) {
        byte[] source = storedVersion == version ? payload : migration.apply(storedVersion, payload);
        return codec.decode(source == null ? new byte[0] : source);
    }
}
