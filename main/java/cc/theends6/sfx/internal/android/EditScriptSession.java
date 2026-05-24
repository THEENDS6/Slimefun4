package cc.theends6.sfx.internal.android;

import java.util.UUID;

record EditScriptSession(UUID instanceId, int page, long scriptId, boolean force) {
}
