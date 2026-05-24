package cc.theends6.sfx.internal.android;

import java.util.List;
import java.util.UUID;

record UploadSession(UUID instanceId, SfxAndroidType type, List<SfxAndroidInstruction> body, SfxAndroidScriptVisibility visibility) {
    UploadSession {
        body = List.copyOf(body);
    }
}
