package cc.theends6.sfx.internal.block;

import java.util.UUID;

public record SfxAnchorRef(SfxBlockAnchorKey key, UUID instanceId, String typeId) {
}
