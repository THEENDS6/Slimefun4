package cc.theends6.sfx.api.block;

public interface SfxBlockStateCodec<S> {
    byte[] encode(S state);

    S decode(byte[] payload);
}
