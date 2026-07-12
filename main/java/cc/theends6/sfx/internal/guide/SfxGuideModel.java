package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.util.ItemBuilder;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

enum Navigation {
    ROOT,
    OPEN,
    REPLACE
}

enum GuideLayout {
    CLASSIC,
    SFX;

    static GuideLayout from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CLASSIC;
        }
        return "sfx".equalsIgnoreCase(raw) ? SFX : CLASSIC;
    }
}

enum GuideRecipeOrigin {
    SFX,
    VANILLA
}

record GuideRecipePage(
        int index,
        GuideRecipeOrigin origin,
        String sourceId,
        String sourceFamily,
        String sourceName,
        String machineTargetId,
        ItemStack sourceIcon,
        List<SfxRecipeSlot> matrix,
        Component note,
        int outputAmount,
        boolean cycleMaterialVariants
) {
    static GuideRecipePage noRecipe() {
        return new GuideRecipePage(0, GuideRecipeOrigin.SFX, "no-recipe", "no-recipe", "No Recipe", null,
                ItemBuilder.of(Material.BARRIER).name("<red>No Recipe</red>").build(),
                Collections.nCopies(9, SfxRecipeSlot.empty()), null, 1, false);
    }

    boolean hasRecipe() {
        return !"no-recipe".equals(sourceId);
    }

    GuideMode mode() {
        return GuideMode.SURVIVAL;
    }

    Material outputMaterial() {
        return null;
    }
}

final class GuidePreferences {
    private GuideLayout layout;
    private boolean recordHistory;
    private boolean closeReturns;
    private boolean fireworks;
    private boolean unlockAnimation;
    private boolean reopenLastLocation;
    private boolean machineUiExtended;
    private boolean machineCompletionSound;
    private boolean machineSmoothUi;
    private GuideLocation lastLocation;

    GuidePreferences(
            GuideLayout layout,
            boolean recordHistory,
            boolean closeReturns,
            boolean fireworks,
            boolean unlockAnimation,
            boolean reopenLastLocation,
            boolean machineUiExtended,
            boolean machineCompletionSound,
            boolean machineSmoothUi,
            GuideLocation lastLocation
    ) {
        this.layout = layout;
        this.recordHistory = recordHistory;
        this.closeReturns = closeReturns;
        this.fireworks = fireworks;
        this.unlockAnimation = unlockAnimation;
        this.reopenLastLocation = reopenLastLocation;
        this.machineUiExtended = machineUiExtended;
        this.machineCompletionSound = machineCompletionSound;
        this.machineSmoothUi = machineSmoothUi;
        this.lastLocation = lastLocation;
    }

    GuideLayout layout() {
        return layout;
    }

    void setLayout(GuideLayout layout) {
        this.layout = layout;
    }

    boolean recordHistory() {
        return recordHistory;
    }

    void setRecordHistory(boolean recordHistory) {
        this.recordHistory = recordHistory;
    }

    boolean closeReturns() {
        return closeReturns;
    }

    void setCloseReturns(boolean closeReturns) {
        this.closeReturns = closeReturns;
    }

    boolean fireworks() {
        return fireworks;
    }

    void setFireworks(boolean fireworks) {
        this.fireworks = fireworks;
    }

    boolean unlockAnimation() {
        return unlockAnimation;
    }

    void setUnlockAnimation(boolean unlockAnimation) {
        this.unlockAnimation = unlockAnimation;
    }

    boolean reopenLastLocation() {
        return reopenLastLocation;
    }

    void setReopenLastLocation(boolean reopenLastLocation) {
        this.reopenLastLocation = reopenLastLocation;
    }

    boolean machineUiExtended() {
        return machineUiExtended;
    }

    void setMachineUiExtended(boolean machineUiExtended) {
        this.machineUiExtended = machineUiExtended;
    }

    boolean machineCompletionSound() {
        return machineCompletionSound;
    }

    void setMachineCompletionSound(boolean machineCompletionSound) {
        this.machineCompletionSound = machineCompletionSound;
    }

    boolean machineSmoothUi() {
        return machineSmoothUi;
    }

    void setMachineSmoothUi(boolean machineSmoothUi) {
        this.machineSmoothUi = machineSmoothUi;
    }

    GuideLocation lastLocation() {
        return lastLocation;
    }

    void setLastLocation(GuideLocation lastLocation) {
        this.lastLocation = lastLocation;
    }
}

record GuideLocation(GuideMode mode, GuideLocationKind kind, int page, String categoryId, String itemId, Material material, int recipeIndex) {
    static GuideLocation main(GuideMode mode, int page) {
        return new GuideLocation(mode, GuideLocationKind.MAIN, page, null, null, null, 0);
    }

    static GuideLocation category(GuideMode mode, String categoryId, int page) {
        return new GuideLocation(mode, GuideLocationKind.CATEGORY, page, categoryId, null, null, 0);
    }

    static GuideLocation recipe(GuideMode mode, String itemId, int recipeIndex) {
        return new GuideLocation(mode, GuideLocationKind.RECIPE, 0, null, itemId, null, recipeIndex);
    }

    static GuideLocation vanilla(GuideMode mode, Material material, int recipeIndex) {
        return new GuideLocation(mode, GuideLocationKind.VANILLA, 0, null, null, material, recipeIndex);
    }
}

enum SfxDisplayLayout {
    NONE,
    COMPACT_LIST,
    PAIRED_GRID
}

enum GuideLocationKind {
    MAIN,
    CATEGORY,
    RECIPE,
    VANILLA
}

record DisplayPage(List<DisplayEntry> entries, int page, int pageCount) {
}

record DisplayEntry(
        ItemStack primaryIcon,
        ItemStack secondaryIcon,
        String label,
        int priority,
        ClickHandler primaryHandler,
        ClickHandler secondaryHandler,
        DisplayEntryKind kind
) {
    static DisplayEntry single(ItemStack icon, String label, int priority, ClickHandler handler) {
        return single(icon, label, priority, handler, DisplayEntryKind.RELATED);
    }

    static DisplayEntry single(ItemStack icon, String label, int priority, ClickHandler handler, DisplayEntryKind kind) {
        return new DisplayEntry(icon, null, label, priority, handler, null, kind);
    }

    static DisplayEntry paired(ItemStack topIcon, ItemStack bottomIcon, String label, int priority, ClickHandler topHandler, ClickHandler bottomHandler) {
        return paired(topIcon, bottomIcon, label, priority, topHandler, bottomHandler, DisplayEntryKind.RELATED);
    }

    static DisplayEntry paired(ItemStack topIcon, ItemStack bottomIcon, String label, int priority, ClickHandler topHandler, ClickHandler bottomHandler, DisplayEntryKind kind) {
        return new DisplayEntry(topIcon, bottomIcon, label, priority, topHandler, bottomHandler, kind);
    }

    boolean paired() {
        return secondaryIcon != null;
    }
}

record Cell(ItemStack icon, ClickHandler handler) {
}

@FunctionalInterface
interface ClickHandler {
    void accept(cc.theends6.sfx.api.menu.SfxMenuClickContext click);
}

@FunctionalInterface
interface OutputAction {
    void accept(Player player, ClickType clickType);
}

@FunctionalInterface
interface RecipePageOpener {
    void open(Player player, int recipeIndex, Navigation navigation);
}

@FunctionalInterface
interface PlayerAction {
    void accept(Player player);
}

enum DisplayEntryKind {
    EXECUTOR,
    MACHINE_RECIPE,
    FUEL,
    RELATED
}

@FunctionalInterface
interface PageAction {
    void accept(Player player, int page);
}
