package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.internal.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;




final class SfxGuideSettingsView {
    private SfxGuideSettingsView() {
    }

    static void open(DefaultSfxGuide guide, Player player, GuideMode mode, Navigation navigation) {
        GuidePreferences preferences = guide.preferences(player);
        GuideLayout layout = guide.effectiveLayout(preferences);
        GuideMode targetMode = mode == GuideMode.CHEAT ? GuideMode.SURVIVAL : GuideMode.CHEAT;
        boolean allowLayoutSwitch = guide.plugin.getConfig().getBoolean("guide.allow-layout-switching", true)
                && guide.plugin.getConfig().getBoolean("guide.sfx-layout-enabled", true);

        SfxMenu.Builder builder = SfxMenu.builder(guide.title(mode, guide.tr("guide.settings.title"))).rows(6);
        guide.paintSettingsBackground(builder, mode);

        builder.button(0, new SfxMenuButton(guide.backIcon(guide.tr("guide.actions.back-guide")), click -> guide.goBack(click.player(), mode)));
        builder.button(2, new SfxMenuButton(guide.modeInfoIcon(mode), click -> {
        }));
        builder.button(4, new SfxMenuButton(ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(guide.tr("guide.settings.title-item"))
                .lore(
                        guide.tr("guide.settings.description.1"),
                        guide.tr("guide.settings.description.2")
                )
                .build(), click -> {
        }));
        builder.button(6, new SfxMenuButton(ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name(guide.tr("guide.settings.help.name"))
                .lore(
                        guide.tr("guide.settings.help.lore.1"),
                        guide.tr("guide.settings.help.lore.2")
                )
                .build(), click -> {
        }));
        builder.button(8, new SfxMenuButton(guide.closeIcon(), click -> guide.closeGuide(click.player())));

        builder.button(19, guide.toggleButton(
                targetMode == GuideMode.CHEAT ? Material.COMMAND_BLOCK : Material.ENCHANTED_BOOK,
                guide.tr("guide.settings.mode.name"),
                targetMode == GuideMode.CHEAT
                        ? guide.tr("guide.settings.mode.cheat.lore")
                        : guide.tr("guide.settings.mode.survival.lore"),
                mode == GuideMode.CHEAT,
                    click -> guide.switchGuideBookMode(click.player(), targetMode)
        ));

        if (allowLayoutSwitch) {
            GuideLayout targetLayout = layout == GuideLayout.CLASSIC ? GuideLayout.SFX : GuideLayout.CLASSIC;
            builder.button(21, guide.toggleButton(
                    targetLayout == GuideLayout.CLASSIC ? Material.CRAFTING_TABLE : Material.SMITHING_TABLE,
                    guide.tr("guide.settings.layout.name"),
                    layout == GuideLayout.CLASSIC
                            ? guide.tr("guide.settings.layout.sfx")
                            : guide.tr("guide.settings.layout.classic"),
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
                guide.tr("guide.settings.history.name"),
                guide.tr("guide.settings.history.lore"),
                preferences.recordHistory(),
                click -> {
                    preferences.setRecordHistory(!preferences.recordHistory());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(25, guide.toggleButton(
                Material.OAK_DOOR,
                guide.tr("guide.settings.close-behavior.name"),
                guide.tr("guide.settings.close-behavior.lore"),
                preferences.closeReturns(),
                click -> {
                    preferences.setCloseReturns(!preferences.closeReturns());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(39, guide.toggleButton(
                Material.FIREWORK_ROCKET,
                guide.tr("guide.settings.fireworks.name"),
                guide.tr("guide.settings.fireworks.lore"),
                preferences.fireworks(),
                click -> {
                    preferences.setFireworks(!preferences.fireworks());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(41, guide.toggleButton(
                Material.REDSTONE_TORCH,
                guide.tr("guide.settings.unlock-animation.name"),
                guide.tr("guide.settings.unlock-animation.lore"),
                preferences.unlockAnimation(),
                click -> {
                    preferences.setUnlockAnimation(!preferences.unlockAnimation());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(43, guide.toggleButton(
                Material.RECOVERY_COMPASS,
                guide.tr("guide.settings.resume-last.name"),
                guide.tr("guide.settings.resume-last.lore"),
                preferences.reopenLastLocation(),
                click -> {
                    preferences.setReopenLastLocation(!preferences.reopenLastLocation());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(47, guide.toggleButton(
                Material.COMPASS,
                guide.tr("guide.settings.machine-ui.name"),
                guide.tr("guide.settings.machine-ui.lore"),
                preferences.machineUiExtended(),
                click -> {
                    preferences.setMachineUiExtended(!preferences.machineUiExtended());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(51, guide.toggleButton(
                Material.NOTE_BLOCK,
                guide.tr("guide.settings.machine-sound.name"),
                guide.tr("guide.settings.machine-sound.lore"),
                preferences.machineCompletionSound(),
                click -> {
                    preferences.setMachineCompletionSound(!preferences.machineCompletionSound());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(53, guide.toggleButton(
                Material.CLOCK,
                guide.tr("guide.settings.machine-smooth.name"),
                guide.tr("guide.settings.machine-smooth.lore"),
                preferences.machineSmoothUi(),
                click -> {
                    preferences.setMachineSmoothUi(!preferences.machineSmoothUi());
                    guide.persistPreferences(click.player(), preferences, true);
                    guide.openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(49, new SfxMenuButton(ItemBuilder.of(Material.NETHER_STAR)
                .name(guide.tr("guide.settings.current-layout.name"))
                .lore(
                        guide.tr(layout == GuideLayout.CLASSIC ? "guide.settings.current-layout.classic" : "guide.settings.current-layout.sfx"),
                        guide.tr("guide.settings.current-layout.mode")
                                .replace("{mode}", mode == GuideMode.CHEAT ? "Cheat" : "Survival")
                )
                .build(), click -> {
        }));

        guide.showMenu(player, builder, navigation);
    
    }
}
