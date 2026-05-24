package cc.theends6.sfx.internal.command;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.internal.playerdata.SfxBackpackRecord;
import cc.theends6.sfx.internal.playerdata.SfxPlayerProfile;
import cc.theends6.sfx.internal.research.SfxResearchDefinition;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.Text;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxCommand implements CommandExecutor, TabCompleter {
    private static final int[] LIST_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private final JavaPlugin plugin;
    private final SfxApi api;
    private final SfxCommandTabCompleter tabCompleter;

    public SfxCommand(JavaPlugin plugin, SfxApi api) {
        this.plugin = plugin;
        this.api = api;
        this.tabCompleter = new SfxCommandTabCompleter(plugin, api);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "guide" -> openGuide(sender, GuideMode.SURVIVAL);
            case "cheatguide" -> openGuide(sender, GuideMode.CHEAT);
            case "book" -> giveGuideBook(sender, args.length >= 2 && args[1].equalsIgnoreCase("cheat") ? GuideMode.CHEAT : GuideMode.SURVIVAL);
            case "give" -> giveItem(sender, args);
            case "research" -> handleResearch(sender, args);
            case "backpack" -> handleBackpack(sender, args);
            case "inspect" -> inspectItem(sender);
            case "list" -> listItems(sender, args.length >= 2 ? parsePositive(args[1], 1) - 1 : 0);
            case "reload" -> reload(sender, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void openGuide(CommandSender sender, GuideMode mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-guide", "Only players can open the SFX guide."));
            return;
        }
        if (mode == GuideMode.CHEAT && !player.hasPermission("sfx.command.cheatguide")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-cheat-guide", "<red>You do not have permission to open the cheat guide.</red>")));
            return;
        }
        if (mode == GuideMode.SURVIVAL && !player.hasPermission("sfx.command.guide")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-guide", "<red>You do not have permission to open the guide.</red>")));
            return;
        }
        api.guide().open(player, mode);
    }

    private void giveGuideBook(CommandSender sender, GuideMode mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-book", "Only players can receive the SFX guide book."));
            return;
        }
        if (mode == GuideMode.CHEAT && !player.hasPermission("sfx.command.cheatbook")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-cheat-book", "<red>You do not have permission to receive the cheat guide book.</red>")));
            return;
        }
        if (mode == GuideMode.SURVIVAL && !player.hasPermission("sfx.command.book")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-book", "<red>You do not have permission to receive the guide book.</red>")));
            return;
        }
        api.items().give(player, api.items().createGuideBook(mode));
        player.sendMessage(Text.prefixed(plugin, mode == GuideMode.CHEAT
                ? tr("command.book.received-cheat", "<green>You received a cheat guide book.</green>")
                : tr("command.book.received", "<green>You received a guide book.</green>")));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-give", "Only players can receive SFX items."));
            return;
        }
        if (!player.hasPermission("sfx.command.give")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-give", "<red>You do not have permission to receive SFX items directly.</red>")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.usage", "<red>Usage: /slimefunx give <id> [amount]</red>")));
            return;
        }
        String itemId = args[1];
        int amount = args.length >= 3 ? parsePositive(args[2], 1) : 1;
        SfxItemDefinition definition = api.itemRegistry().item(itemId).orElse(null);
        if (definition == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.unknown", "<red>Unknown SFX item: {id}</red>").replace("{id}", itemId)));
            return;
        }
        if (!definition.giveable()) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.not-giveable", "<red>This item cannot be given directly.</red>")));
            return;
        }
        int inserted = giveStacks(player.getInventory(), definition, amount);
        if (inserted <= 0) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.no-space", "<red>Your inventory does not have enough free space.</red>")));
            return;
        }
        if (inserted < amount) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.partial", "<yellow>Inserted {amount}x {item}. Inventory became full.</yellow>")
                    .replace("{amount}", Integer.toString(inserted))
                    .replace("{item}", itemDisplayName(definition))));
            return;
        }
        player.sendMessage(Text.prefixed(plugin, tr("command.give.success", "<green>Received {amount}x {item}.</green>")
                .replace("{amount}", Integer.toString(inserted))
                .replace("{item}", itemDisplayName(definition))));
    }

    private void handleResearch(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sfx.command.research")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-research", "<red>You do not have permission to manage researches.</red>")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.research.usage", "<red>Usage: /slimefunx research <player|uuid> <all|reset|research-id></red>")));
            return;
        }
        SlimeFunXPlugin sfx = sfxPlugin();
        if (sfx == null) {
            sender.sendMessage("SFX plugin context unavailable.");
            return;
        }
        ResolvedPlayerRef target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.player.unknown", "<red>Unknown player or UUID.</red>")));
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        sfx.playerDataService().request(target.offlinePlayer(), profile -> {
            SfxResearchService researches = sfx.researchService();
            switch (action) {
                case "all" -> {
                    researches.grantAll(profile);
                    sender.sendMessage(Text.prefixed(plugin, tr("command.research.success-all", "<green>Unlocked all researches for {player}.</green>").replace("{player}", target.displayName())));
                }
                case "reset" -> {
                    researches.resetAll(profile);
                    sender.sendMessage(Text.prefixed(plugin, tr("command.research.success-reset", "<green>Reset all researches for {player}.</green>").replace("{player}", target.displayName())));
                }
                default -> {
                    Optional<SfxResearchDefinition> research = researches.researchById(action);
                    if (research.isEmpty()) {
                        sender.sendMessage(Text.prefixed(plugin, tr("command.research.unknown", "<red>Unknown research: {id}</red>").replace("{id}", action)));
                        return;
                    }
                    boolean changed = researches.grant(profile, research.get());
                    sender.sendMessage(Text.prefixed(plugin, (changed
                            ? tr("command.research.success-one", "<green>Unlocked research {id} for {player}.</green>")
                            : tr("command.research.already", "<yellow>{player} already had research {id}.</yellow>"))
                            .replace("{id}", research.get().id())
                            .replace("{player}", target.displayName())));
                }
            }
        });
    }

    private void handleBackpack(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sfx.command.backpack")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-backpack", "<red>You do not have permission to manage backpacks.</red>")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.backpack.usage", "<red>Usage: /slimefunx backpack <list|open|give> <player|uuid> [id] [size]</red>")));
            return;
        }
        SlimeFunXPlugin sfx = sfxPlugin();
        if (sfx == null) {
            sender.sendMessage("SFX plugin context unavailable.");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        ResolvedPlayerRef target = resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.player.unknown", "<red>Unknown player or UUID.</red>")));
            return;
        }
        switch (action) {
            case "list" -> listBackpacks(sender, sfx, target);
            case "open" -> openBackpack(sender, sfx, target, args);
            case "give" -> giveBackpack(sender, sfx, target, args);
            default -> sender.sendMessage(Text.prefixed(plugin, tr("command.backpack.usage", "<red>Usage: /slimefunx backpack <list|open|give> <player|uuid> [id] [size]</red>")));
        }
    }

    private void listBackpacks(CommandSender sender, SlimeFunXPlugin sfx, ResolvedPlayerRef target) {
        sfx.playerDataService().request(target.offlinePlayer(), profile -> {
            sender.sendMessage(Text.prefixed(plugin, tr("command.backpack.list.header", "<green>Backpacks for {player}</green>").replace("{player}", target.displayName())));
            if (profile.backpacksCopy().isEmpty()) {
                sender.sendMessage(Text.mm(tr("command.backpack.list.empty", "<gray>No backpacks found.</gray>")));
                return;
            }
            for (SfxBackpackRecord backpack : profile.backpacksCopy().values()) {
                sender.sendMessage(Text.mm(tr("command.backpack.list.entry", "<gray>- ID {id} | size {size} | updated {updated}</gray>")
                        .replace("{id}", Integer.toString(backpack.id()))
                        .replace("{size}", Integer.toString(backpack.size()))
                        .replace("{updated}", formatTimestamp(backpack.updatedAt()))));
            }
        });
    }

    private void openBackpack(CommandSender sender, SlimeFunXPlugin sfx, ResolvedPlayerRef target, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-backpack-open", "Only players can open backpacks directly."));
            return;
        }
        if (args.length < 4) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.open.usage", "<red>Usage: /slimefunx backpack open <player|uuid> <id></red>")));
            return;
        }
        Integer backpackId = parseNonNegativeInt(args[3]);
        if (backpackId == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.invalid-id", "<red>Invalid backpack id.</red>")));
            return;
        }
        sfx.backpackListener().openBackpackForAdmin(player, target.offlinePlayer().getUniqueId(), target.displayName(), backpackId);
    }

    private void giveBackpack(CommandSender sender, SlimeFunXPlugin sfx, ResolvedPlayerRef target, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-backpack-give", "Only players can receive backpack items."));
            return;
        }
        if (args.length < 4) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.give.usage", "<red>Usage: /slimefunx backpack give <player|uuid> <id> [size|tier]</red>")));
            return;
        }
        Integer backpackId = parseNonNegativeInt(args[3]);
        if (backpackId == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.invalid-id", "<red>Invalid backpack id.</red>")));
            return;
        }
        Integer size = args.length >= 5 ? parseBackpackSizeArg(args[4]) : null;
        if (args.length >= 5 && size == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.invalid-size", "<red>Use backpack size 9/18/27/36/45/54 or tier 1-6.</red>")));
            return;
        }
        sfx.backpackListener().giveBackpackItem(player, target.offlinePlayer().getUniqueId(), target.displayName(), backpackId, size);
        player.sendMessage(Text.prefixed(plugin, tr("command.backpack.give.sent", "<green>Prepared backpack item for {player} #{id}.</green>")
                .replace("{player}", target.displayName())
                .replace("{id}", Integer.toString(backpackId))));
    }

    private void inspectItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-inspect", "Only players can inspect SFX items."));
            return;
        }
        if (!player.hasPermission("sfx.command.inspect")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-inspect", "<red>You do not have permission to inspect SFX item markers.</red>")));
            return;
        }
        SfxItemMarker marker = api.items().readMarker(player.getInventory().getItemInMainHand()).orElse(null);
        if (marker == null) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.inspect.no-marker", "<gray>No SFX item marker was found in your main hand.</gray>")));
            return;
        }
        sender.sendMessage(Text.mm(tr("command.inspect.header", "<green>SFX Item Marker</green>")));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-id", "<gray>item_id: </gray><white>{value}</white>").replace("{value}", marker.itemId())));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-version", "<gray>item_version: </gray><white>{value}</white>").replace("{value}", Integer.toString(marker.itemVersion()))));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-schema", "<gray>item_schema: </gray><white>{value}</white>").replace("{value}", Integer.toString(marker.schemaVersion()))));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-variant", "<gray>item_variant: </gray><white>{value}</white>").replace("{value}", marker.variant())));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-kind", "<gray>item_kind: </gray><white>{value}</white>").replace("{value}", marker.kind().pdcValue())));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-flags", "<gray>item_flags: </gray><white>{value}</white>").replace("{value}", marker.flagsAsString())));
        api.items().readGuideMode(player.getInventory().getItemInMainHand())
                .ifPresent(mode -> sender.sendMessage(Text.mm(tr("command.inspect.field.guide-mode", "<gray>guide_mode: </gray><white>{value}</white>").replace("{value}", mode.pdcValue()))));
    }

    private void listItems(CommandSender sender, int page) {
        if (!sender.hasPermission("sfx.command.list")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-list", "<red>You do not have permission to view the SFX item list.</red>")));
            return;
        }
        List<SfxItemDefinition> definitions = api.itemRegistry().items().stream().filter(item -> !item.hidden()).toList();
        int pageCount = Math.max(1, (int) Math.ceil(definitions.size() / (double) LIST_SLOTS.length));
        int safePage = Math.max(0, Math.min(page, pageCount - 1));

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm(tr("command.list.header", "<green>SFX Item Registry</green><gray> {count} items | page {page}/{pages}</gray>")
                    .replace("{count}", Integer.toString(definitions.size()))
                    .replace("{page}", Integer.toString(safePage + 1))
                    .replace("{pages}", Integer.toString(pageCount))));
            int from = safePage * LIST_SLOTS.length;
            int to = Math.min(definitions.size(), from + LIST_SLOTS.length);
            for (int i = from; i < to; i++) {
                SfxItemDefinition item = definitions.get(i);
                sender.sendMessage(Text.mm("<gray>- </gray><white>" + itemDisplayName(item) + "</white> <dark_gray>(" + item.id() + ")</dark_gray>"));
            }
            return;
        }

        SfxMenu.Builder builder = SfxMenu.builder(Text.mm(tr("command.list.menu-title", "<dark_green>SFX Item Registry</dark_green> <gray>{page}/{pages}</gray>")
                .replace("{page}", Integer.toString(safePage + 1))
                .replace("{pages}", Integer.toString(pageCount)))).rows(6);
        ItemStack pane = ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
        for (int i = 0; i < 9; i++) {
            builder.button(i, new SfxMenuButton(pane, click -> {
            }));
        }
        for (int i = 45; i < 54; i++) {
            builder.button(i, new SfxMenuButton(pane, click -> {
            }));
        }
        int from = safePage * LIST_SLOTS.length;
        int to = Math.min(definitions.size(), from + LIST_SLOTS.length);
        for (int i = from; i < to; i++) {
            SfxItemDefinition definition = definitions.get(i);
            builder.button(LIST_SLOTS[i - from], new SfxMenuButton(listIcon(definition), click -> {
            }));
        }
        builder.button(46, new SfxMenuButton(ItemBuilder.of(Material.ARROW)
                .name(safePage > 0 ? tr("guide.pagination.prev.active", "<yellow>Previous Page</yellow>") : tr("guide.pagination.prev.inactive", "<dark_gray>Previous Page</dark_gray>"))
                .lore(tr("guide.pagination.page", "<gray>Page {current} / {total}</gray>").replace("{current}", Integer.toString(safePage + 1)).replace("{total}", Integer.toString(pageCount)))
                .build(), click -> {
            if (safePage > 0) {
                listItems(click.player(), safePage - 1);
            }
        }));
        builder.button(49, new SfxMenuButton(ItemBuilder.of(Material.NETHER_STAR)
                .name(tr("command.list.registry-name", "<green>SFX Item Registry</green>"))
                .lore(tr("command.list.registry-lore.0", "<gray>Browse all currently registered visible items.</gray>"),
                        tr("command.list.registry-lore.1", "<gray>Total: {count}</gray>").replace("{count}", Integer.toString(definitions.size())),
                        "",
                        tr("guide.actions.close", "<dark_gray>Click to close.</dark_gray>"))
                .build(), click -> click.menus().close(click.player())));
        builder.button(52, new SfxMenuButton(ItemBuilder.of(Material.ARROW)
                .name(safePage + 1 < pageCount ? tr("guide.pagination.next.active", "<yellow>Next Page</yellow>") : tr("guide.pagination.next.inactive", "<dark_gray>Next Page</dark_gray>"))
                .lore(tr("guide.pagination.page", "<gray>Page {current} / {total}</gray>").replace("{current}", Integer.toString(safePage + 1)).replace("{total}", Integer.toString(pageCount)))
                .build(), click -> {
            if (safePage + 1 < pageCount) {
                listItems(click.player(), safePage + 1);
            }
        }));
        api.menus().open(player, builder.build());
    }

    private ItemStack listIcon(SfxItemDefinition definition) {
        ItemStack icon = api.items().create(definition, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Component.text(tr("command.list.registry-entry", "Registry Entry"), NamedTextColor.GRAY));
            meta.lore(lore.stream().map(Text::noItalic).toList());
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void reload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sfx.command.reload")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-reload", "<red>You do not have permission to reload SFX.</red>")));
            return;
        }
        if (plugin instanceof SlimeFunXPlugin sfxPlugin) {
            if (args.length >= 2 && (args[1].equalsIgnoreCase("runtime") || args[1].equalsIgnoreCase("all"))) {
                sfxPlugin.reloadAllContent();
                sender.sendMessage(Text.prefixed(plugin, tr("command.reload.success-all", "<green>SFX runtime reloaded: listeners, services, machine framework, registries, recipes, researches, and menus were rebuilt.</green>")));
                return;
            }
            plugin.reloadConfig();
            sfxPlugin.localization().reload();
            sfxPlugin.legacyItemBehaviorConfig().reload();
        } else {
            plugin.reloadConfig();
        }
        sender.sendMessage(Text.prefixed(plugin, tr("command.reload.success", "<green>SFX configuration and language files were reloaded.</green>")));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Text.mm(tr("command.help.header", "<green>SFX Commands</green>")));
        sender.sendMessage(Text.mm(tr("command.help.line.guide", "<gray>/{label} guide</gray> <dark_gray>- open the survival guide</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.book", "<gray>/{label} book [cheat]</gray> <dark_gray>- receive a guide book</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.cheatguide", "<gray>/{label} cheatguide</gray> <dark_gray>- open the cheat guide</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.give", "<gray>/{label} give <id> [amount]</gray> <dark_gray>- receive an SFX item</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.research", "<gray>/{label} research <player> <all|reset|id></gray> <dark_gray>- manage researches</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.backpack", "<gray>/{label} backpack <list|open|give> ...</gray> <dark_gray>- manage backpacks</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.inspect", "<gray>/{label} inspect</gray> <dark_gray>- inspect the SFX item in your main hand</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.list", "<gray>/{label} list [page]</gray> <dark_gray>- list visible SFX items</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.reload", "<gray>/{label} reload [config|runtime]</gray> <dark_gray>- reload config or rebuild the SFX runtime</dark_gray>").replace("{label}", label)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabCompleter.complete(sender, command, alias, args);
    }

    private SlimeFunXPlugin sfxPlugin() {
        return plugin instanceof SlimeFunXPlugin sfx ? sfx : null;
    }

    private String tr(String key, String fallback) {
        SlimeFunXPlugin sfx = sfxPlugin();
        return sfx == null ? fallback : sfx.localization().text(key, fallback);
    }

    private String itemDisplayName(SfxItemDefinition definition) {
        SlimeFunXPlugin sfx = sfxPlugin();
        return sfx == null ? plainText(definition.name()) : plainText(sfx.localization().itemName(definition.id(), definition.name()));
    }

    private String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private Integer parseNonNegativeInt(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value < 0 ? null : value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int parsePositive(String raw, int fallback) {
        try {
            return Math.max(1, Math.min(100000, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Integer parseBackpackSizeArg(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return switch (value) {
                case 1 -> 9;
                case 2 -> 18;
                case 3 -> 27;
                case 4 -> 36;
                case 5 -> 45;
                case 6 -> 54;
                case 9, 18, 27, 36, 45, 54 -> value;
                default -> null;
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int giveStacks(Inventory inventory, SfxItemDefinition definition, int amount) {
        int given = 0;
        int maxStack = definition.material() == Material.PLAYER_HEAD ? 64 : Math.max(1, definition.material().getMaxStackSize());
        while (given < amount) {
            int batch = Math.min(maxStack, amount - given);
            ItemStack stack = api.items().create(definition, batch);
            var leftovers = inventory.addItem(stack);
            int inserted = batch;
            for (ItemStack leftover : leftovers.values()) {
                if (leftover != null && !leftover.getType().isAir()) {
                    inserted -= Math.max(0, leftover.getAmount());
                }
            }
            if (inserted <= 0) {
                break;
            }
            given += inserted;
            if (inserted < batch) {
                break;
            }
        }
        return given;
    }

    private ResolvedPlayerRef resolvePlayer(String raw) {
        Player online = Bukkit.getPlayerExact(raw);
        if (online != null) {
            return new ResolvedPlayerRef(online, online.getName());
        }
        try {
            UUID uuid = UUID.fromString(raw);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            return new ResolvedPlayerRef(offline, offline.getName() == null ? uuid.toString() : offline.getName());
        } catch (IllegalArgumentException ignored) {
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(raw)) {
                return new ResolvedPlayerRef(offline, offline.getName());
            }
        }
        return null;
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0L) {
            return "-";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).toString().replace('T', ' ');
    }


}
