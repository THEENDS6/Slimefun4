package cc.theends6.sfx.internal.android;

import java.util.List;
import java.util.UUID;

public record SfxAndroidScriptRecord(
        long id,
        SfxAndroidType androidType,
        UUID authorId,
        String authorName,
        String name,
        List<SfxAndroidInstruction> body,
        SfxAndroidScriptVisibility visibility,
        int downloads,
        int positiveVotes,
        int negativeVotes,
        long createdAt,
        long updatedAt
) {
    public SfxAndroidScriptRecord {
        body = List.copyOf(body);
    }
}
