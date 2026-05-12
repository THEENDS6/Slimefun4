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
    private String guideLayoutMode;
    private boolean guideRecordHistory = true;
    private boolean guideCloseReturns = true;
    private boolean guideFireworks = true;
    private boolean guideUnlockAnimation = true;
    private boolean guideReopenLastLocation;
    private String guideLastLocation;
    private boolean machineUiExtended = true;
    private boolean machineCompletionSound = true;
    private boolean machineSmoothUi = true;
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

    public synchronized boolean unlockAll(Iterable<String> researchIds) {
        boolean changed = false;
        for (String researchId : researchIds) {
            if (researchId != null && unlockedResearches.add(researchId)) {
                changed = true;
            }
        }
        if (changed) {
            dirty = true;
        }
        return changed;
    }

    public synchronized void clearUnlockedResearches() {
        if (!unlockedResearches.isEmpty()) {
            unlockedResearches.clear();
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

    public synchronized SfxBackpackRecord ensureBackpackSized(int id, int size) {
        SfxBackpackRecord backpack = getOrCreateBackpack(id, size);
        if (backpack.size() != size) {
            backpack.setSize(size);
            dirty = true;
        }
        return backpack;
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
            copy.put(entry.getKey(), new SfxBackpackRecord(backpack.id(), backpack.size(), backpack.contentsCopy(), backpack.updatedAt()));
        }
        return copy;
    }

    public synchronized void putBackpack(SfxBackpackRecord backpack) {
        backpacks.put(backpack.id(), backpack);
    }

    public synchronized String guideLayoutMode() {
        return guideLayoutMode;
    }

    public synchronized void setGuideLayoutMode(String guideLayoutMode) {
        if (java.util.Objects.equals(this.guideLayoutMode, guideLayoutMode)) {
            return;
        }
        this.guideLayoutMode = guideLayoutMode;
        this.dirty = true;
    }

    public synchronized boolean guideRecordHistory() {
        return guideRecordHistory;
    }

    public synchronized void setGuideRecordHistory(boolean guideRecordHistory) {
        if (this.guideRecordHistory == guideRecordHistory) {
            return;
        }
        this.guideRecordHistory = guideRecordHistory;
        this.dirty = true;
    }

    public synchronized boolean guideCloseReturns() {
        return guideCloseReturns;
    }

    public synchronized void setGuideCloseReturns(boolean guideCloseReturns) {
        if (this.guideCloseReturns == guideCloseReturns) {
            return;
        }
        this.guideCloseReturns = guideCloseReturns;
        this.dirty = true;
    }

    public synchronized boolean guideFireworks() {
        return guideFireworks;
    }

    public synchronized void setGuideFireworks(boolean guideFireworks) {
        if (this.guideFireworks == guideFireworks) {
            return;
        }
        this.guideFireworks = guideFireworks;
        this.dirty = true;
    }

    public synchronized boolean guideUnlockAnimation() {
        return guideUnlockAnimation;
    }

    public synchronized void setGuideUnlockAnimation(boolean guideUnlockAnimation) {
        if (this.guideUnlockAnimation == guideUnlockAnimation) {
            return;
        }
        this.guideUnlockAnimation = guideUnlockAnimation;
        this.dirty = true;
    }

    public synchronized boolean guideReopenLastLocation() {
        return guideReopenLastLocation;
    }

    public synchronized void setGuideReopenLastLocation(boolean guideReopenLastLocation) {
        if (this.guideReopenLastLocation == guideReopenLastLocation) {
            return;
        }
        this.guideReopenLastLocation = guideReopenLastLocation;
        this.dirty = true;
    }

    public synchronized String guideLastLocation() {
        return guideLastLocation;
    }

    public synchronized void setGuideLastLocation(String guideLastLocation) {
        if (java.util.Objects.equals(this.guideLastLocation, guideLastLocation)) {
            return;
        }
        this.guideLastLocation = guideLastLocation;
        this.dirty = true;
    }

    public synchronized boolean machineUiExtended() {
        return machineUiExtended;
    }

    public synchronized void setMachineUiExtended(boolean machineUiExtended) {
        if (this.machineUiExtended == machineUiExtended) {
            return;
        }
        this.machineUiExtended = machineUiExtended;
        this.dirty = true;
    }

    public synchronized boolean machineCompletionSound() {
        return machineCompletionSound;
    }

    public synchronized void setMachineCompletionSound(boolean machineCompletionSound) {
        if (this.machineCompletionSound == machineCompletionSound) {
            return;
        }
        this.machineCompletionSound = machineCompletionSound;
        this.dirty = true;
    }

    public synchronized boolean machineSmoothUi() {
        return machineSmoothUi;
    }

    public synchronized void setMachineSmoothUi(boolean machineSmoothUi) {
        if (this.machineSmoothUi == machineSmoothUi) {
            return;
        }
        this.machineSmoothUi = machineSmoothUi;
        this.dirty = true;
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
