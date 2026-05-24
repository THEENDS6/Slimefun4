package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.internal.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Renders and wires the guide settings view outside the main guide controller.
 */
final class SfxGuideSettingsView {
    private SfxGuideSettingsView() {
    }

    static void open(DefaultSfxGuide guide, Player player, GuideMode mode, Navigation navigation) {
        GuidePreferences preferences = guide.preferences(player);
        GuideLayout layout = guide.effectiveLayout(preferences);
        GuideMode targetMode = mode == GuideMode.CHEAT ? GuideMode.SURVIVAL : GuideMode.CHEAT;
        boolean allowLayoutSwitch = guide.plugin.getConfig().getBoolean("guide.allow-layout-switching", true)
                && guide.plugin.getConfig().getBoolean("guide.sfx-layout-enabled", true);

        SfxMenu.Builder builder = SfxMenu.builder(guide.title(mode, guide.tr("guide.settings.title", "Guide Settings"))).rows(6);
        guide.paintSettingsBackground(builder, mode);

        builder.button(0, new SfxMenuButton(guide.backIcon(guide.tr("guide.actions.back-guide", "Back to Guide")), click -> guide.goBack(click.player(), mode)));
        builder.button(2, new SfxMenuButton(guide.modeInfoIcon(mode), click -> {
        }));
        builder.button(4, new SfxMenuButton(ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(guide.tr("guide.settings.title-item", "<green>Guide Settings</green>"))
                .lore(
                        guide.tr("guide.settings.description.1", "<gray>Configure how this guide behaves for you.</gray>"),
                        guide.tr("guide.settings.description.2", "<gray>The classic layout is the default restored experience.</gray>")
                )
                .build(), click -> {
        }));
        builder.button(6, new SfxMenuButton(ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name(guide.tr("guide.settings.help.name", "<aqua>Guide Help</aqua>"))
                .lore(
                        guide.tr("guide.settings.help.lore.1", "<gray>Shift + Right Click the guide book to open this menu.</gray>"),
                        guide.tr("guide.settings.help.lore.2", "<gray>Layout, history, and close behavior are per-player guide preferences.</gray>")
                )
                .build(), click -> {
        }));
        builder.button(8, new SfxMenuButton(guide.closeIcon(), click -> guide.closeGuide(click.player())));

        builder.button(19, guide.toggleButton(
                targetMode == GuideMode.CHEAT ? Material.COMMAND_BLOCK : Material.ENCHANTED_BOOK,
                guide.tr("guide.settings.mode.name", "<yellow>Guide Mode</yellow>"),
                targetMode == GuideMode.CHEAT
                        ? guide.tr("guide.settings.mode.cheat.lore", "<gray>Click to switch this book to the cheat guide.</gray>")
                        : guide.tr("guide.settings.mode.survival.lore", "<gray>Click to switch this book back to the survival guide.</gray>"),
                mode == GuideMode.CHEAT,
                    click -> guide.switchGuideBookMode(click.player(), targetMode)
        ));

        if (allowLayoutSwitch) {
            GuideLayout targetLayout = layout == GuideLayout.CLASSIC ? GuideLayout.SFX : GuideLayout.CLASSIC;
            builder.button(21, guide.toggleButton(
                    targetLayout == GuideLayout.CLASSIC ? Material.CRAFTING_TABLE : Material.SMITHING_TABLE,
                    guide.tr("guide.settings.layout.name", "<yellow>Guide Layout</yellow>"),
                    layout == GuideLayout.CLASSIC
                            ? guide.tr("guide.settings.layout.sfx", "<gray>Current: classic. Click to switch to the expanded SFX layout.</gray>")
                            : guide.tr("guide.settings.layout.classic", "<gray>Current: SFX. Click to switch to the restored classic layout.</gray>"),
                    layout == GuideLayout.SFX,
                    click -> {
                        preferences.setLayout(targetLayout);
                        guide.persistPreferences(click.player(), preferences, true);
                        guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                    }
            ));
        }

        builder.button(23, guide.toggleButton(
                Material.COMPARATOR,
                guide.tr("guide.settings.history.name", "<yellow>Nested Recipe History</yellow>"),
                guide.tr("guide.settings.history.lore", "<gray>Opening a recipe from another recipe remembers the previous page.</gray>"),
                preferences.recordHistory(),
                click -> {
                    preferences.setRecordHistory(!preferences.recordHistory());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(25, guide.toggleButton(
                Material.OAK_DOOR,
                guide.tr("guide.settings.close-behavior.name", "<yellow>Esc / E Behavior</yellow>"),
                guide.tr("guide.settings.close-behavior.lore", "<gray>Choose whether closing the guide returns to the previous page.</gray>"),
                preferences.closeReturns(),
                click -> {
                    preferences.setCloseReturns(!preferences.closeReturns());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(39, guide.toggleButton(
                Material.FIREWORK_ROCKET,
                guide.tr("guide.settings.fireworks.name", "<yellow>Research Fireworks</yellow>"),
                guide.tr("guide.settings.fireworks.lore", "<gray>Show a large firework when you finish researching an item.</gray>"),
                preferences.fireworks(),
                click -> {
                    preferences.setFireworks(!preferences.fireworks());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(41, guide.toggleButton(
                Material.REDSTONE_TORCH,
                guide.tr("guide.settings.unlock-animation.name", "<yellow>Unlock Animation</yellow>"),
                guide.tr("guide.settings.unlock-animation.lore", "<gray>Show the pondering progress in chat while researching an item.</gray>"),
                preferences.unlockAnimation(),
                click -> {
                    preferences.setUnlockAnimation(!preferences.unlockAnimation());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(43, guide.toggleButton(
                Material.RECOVERY_COMPASS,
                guide.tr("guide.settings.resume-last.name", "<yellow>Resume Last Page</yellow>"),
                guide.tr("guide.settings.resume-last.lore", "<gray>Reopen the guide at the last page you closed.</gray>"),
                preferences.reopenLastLocation(),
                click -> {
                    preferences.setReopenLastLocation(!preferences.reopenLastLocation());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(47, guide.toggleButton(
                Material.COMPASS,
                guide.tr("guide.settings.machine-ui.name", "<yellow>Machine UI Details</yellow>"),
                guide.tr("guide.settings.machine-ui.lore", "<gray>Show SFX extra machine details on top of the classic progress display.</gray>"),
                preferences.machineUiExtended(),
                click -> {
                    preferences.setMachineUiExtended(!preferences.machineUiExtended());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(51, guide.toggleButton(
                Material.NOTE_BLOCK,
                guide.tr("guide.settings.machine-sound.name", "<yellow>Machine Completion Sound</yellow>"),
                guide.tr("guide.settings.machine-sound.lore", "<gray>Play the machine completion sound only for you while viewing its UI.</gray>"),
                preferences.machineCompletionSound(),
                click -> {
                    preferences.setMachineCompletionSound(!preferences.machineCompletionSound());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(53, guide.toggleButton(
                Material.CLOCK,
                guide.tr("guide.settings.machine-smooth.name", "<yellow>Smooth Machine UI</yellow>"),
                guide.tr("guide.settings.machine-smooth.lore", "<gray>Enable per-tick machine UI refresh. Disable for a classic 10-tick display style.</gray>"),
                preferences.machineSmoothUi(),
                click -> {
                    preferences.setMachineSmoothUi(!preferences.machineSmoothUi());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(49, new SfxMenuButton(ItemBuilder.of(Material.NETHER_STAR)
                .name(guide.tr("guide.settings.current-layout.name", "<aqua>Current Layout</aqua>"))
                .lore(
                        tr(layout == GuideLayout.CLASSIC ? "guide.settings.current-layout.classic" : "guide.settings.current-layout.sfx",
                                layout == GuideLayout.CLASSIC
                                        ? "<gray>Classic layout is active.</gray>"
                                        : "<gray>SFX layout is active.</gray>"),
                        guide.tr("guide.settings.current-layout.mode", "<gray>Guide mode: {mode}</gray>")
                                .replace("{mode}", mode == GuideMode.CHEAT ? "Cheat" : "Survival")
                )
                .build(), click -> {
        }));

        guide.showMenu(player, builder, navigation);
    
    }
}
