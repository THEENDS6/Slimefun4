package cc.theends6.sfx.internal.playerdata;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SfxPlayerProfile {
    private final UUID ownerId;
    private String lastKnownName;
    private final Set<String> unlockedResearches = new LinkedHashSet<>();
    private final Map<Integer, SfxBackpackRecord> backpacks = new LinkedHashMap<>();
    private boolean dirty;

    public SfxPlayerProfile(UUID ownerId, String lastKnownName) {
        this.ownerId = ownerId;
        this.lastKnownName = lastKnownName;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public synchronized String lastKnownName() {
        return lastKnownName;
    }

    public synchronized void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
        this.dirty = true;
    }

    public synchronized boolean hasUnlocked(String researchId) {
        return unlockedResearches.contains(researchId);
    }

    public synchronized void unlock(String researchId) {
        if (unlockedResearches.add(researchId)) {
            dirty = true;
        }
    }

    public synchronized Set<String> unlockedResearchesCopy() {
        return Set.copyOf(unlockedResearches);
    }

    public synchronized SfxBackpackRecord getBackpack(int id) {
        return backpacks.get(id);
    }

    public synchronized SfxBackpackRecord getOrCreateBackpack(int id, int size) {
        return backpacks.computeIfAbsent(id, ignored -> {
            dirty = true;
            return new SfxBackpackRecord(id, size, new org.bukkit.inventory.ItemStack[size]);
        });
    }

    public synchronized int nextBackpackId() {
        int next = 0;
        while (backpacks.containsKey(next)) {
            next++;
        }
        return next;
    }

    public synchronized Map<Integer, SfxBackpackRecord> backpacksCopy() {
        Map<Integer, SfxBackpackRecord> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, SfxBackpackRecord> entry : backpacks.entrySet()) {
            SfxBackpackRecord backpack = entry.getValue();
            copy.put(entry.getKey(), new SfxBackpackRecord(backpack.id(), backpack.size(), backpack.contentsCopy()));
        }
        return copy;
    }

    public synchronized void putBackpack(SfxBackpackRecord backpack) {
        backpacks.put(backpack.id(), backpack);
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void markDirty() {
        this.dirty = true;
    }

    public synchronized void markSaved() {
        this.dirty = false;
    }
}
