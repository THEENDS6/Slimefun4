package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;

import cc.theends6.sfx.api.block.*;

import java.util.UUID;

public record SfxAnchorRef(SfxBlockAnchorKey key, UUID instanceId, String typeId) {
}
