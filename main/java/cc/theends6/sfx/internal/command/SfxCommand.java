package cc.theends6.sfx.internal.command;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.feature.SfxFeature;
import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.internal.playerdata.SfxBackpackRecord;
import cc.theends6.sfx.internal.playerdata.SfxPlayerProfile;
import cc.theends6.sfx.internal.research.SfxResearchDefinition;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.template.SfxTemplateCompileReport;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.api.text.Text;
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
            case "template", "templates" -> handleTemplate(sender, args);
            case "addon", "addons" -> handleAddon(sender, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void openGuide(CommandSender sender, GuideMode mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-guide"));
            return;
        }
        if (mode == GuideMode.CHEAT && !player.hasPermission("sfx.command.cheatguide")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-cheat-guide")));
            return;
        }
        if (mode == GuideMode.SURVIVAL && !player.hasPermission("sfx.command.guide")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-guide")));
            return;
        }
        api.guide().open(player, mode);
    }

    private void giveGuideBook(CommandSender sender, GuideMode mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-book"));
            return;
        }
        if (mode == GuideMode.CHEAT && !player.hasPermission("sfx.command.cheatbook")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-cheat-book")));
            return;
        }
        if (mode == GuideMode.SURVIVAL && !player.hasPermission("sfx.command.book")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-book")));
            return;
        }
        api.items().give(player, api.items().createGuideBook(mode));
        player.sendMessage(Text.prefixed(plugin, mode == GuideMode.CHEAT
                ? tr("command.book.received-cheat")
                : tr("command.book.received")));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-give"));
            return;
        }
        if (!player.hasPermission("sfx.command.give")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-give")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.usage")));
            return;
        }
        String itemId = args[1];
        int amount = args.length >= 3 ? parsePositive(args[2], 1) : 1;
        SfxItemDefinition definition = api.itemRegistry().item(itemId).orElse(null);
        if (definition == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.unknown").replace("{id}", itemId)));
            return;
        }
        if (!definition.giveable()) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.not-giveable")));
            return;
        }
        if (!api.items().canUse(player, definition.id())
                || (definition.permission() != null && !player.hasPermission(definition.permission()))) {
            player.sendMessage(Text.prefixed(plugin, tr("messages.no-item-permission")));
            return;
        }
        int inserted = giveStacks(player.getInventory(), definition, amount);
        if (inserted <= 0) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.no-space")));
            return;
        }
        if (inserted < amount) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.partial")
                    .replace("{amount}", Integer.toString(inserted))
                    .replace("{item}", itemDisplayName(definition))));
            return;
        }
        player.sendMessage(Text.prefixed(plugin, tr("command.give.success")
                .replace("{amount}", Integer.toString(inserted))
                .replace("{item}", itemDisplayName(definition))));
    }

    private void handleResearch(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sfx.command.research")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-research")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.research.usage")));
            return;
        }
        SlimeFunXPlugin sfx = sfxPlugin();
        if (sfx == null) {
            sender.sendMessage("SFX plugin context unavailable.");
            return;
        }
        ResolvedPlayerRef target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.player.unknown")));
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        sfx.playerDataService().request(target.offlinePlayer(), profile -> {
            SfxResearchService researches = sfx.researchService();
            switch (action) {
                case "all" -> {
                    researches.grantAll(profile);
                    sender.sendMessage(Text.prefixed(plugin, tr("command.research.success-all").replace("{player}", target.displayName())));
                }
                case "reset" -> {
                    researches.resetAll(profile);
                    sender.sendMessage(Text.prefixed(plugin, tr("command.research.success-reset").replace("{player}", target.displayName())));
                }
                default -> {
                    Optional<SfxResearchDefinition> research = researches.researchById(action);
                    if (research.isEmpty()) {
                        sender.sendMessage(Text.prefixed(plugin, tr("command.research.unknown").replace("{id}", action)));
                        return;
                    }
                    boolean changed = researches.grant(profile, research.get());
                    sender.sendMessage(Text.prefixed(plugin, (changed
                            ? tr("command.research.success-one")
                            : tr("command.research.already"))
                            .replace("{id}", research.get().id())
                            .replace("{player}", target.displayName())));
                }
            }
        });
    }

    private void handleBackpack(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sfx.command.backpack")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-backpack")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.backpack.usage")));
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
            sender.sendMessage(Text.prefixed(plugin, tr("command.player.unknown")));
            return;
        }
        switch (action) {
            case "list" -> listBackpacks(sender, sfx, target);
            case "open" -> openBackpack(sender, sfx, target, args);
            case "give" -> giveBackpack(sender, sfx, target, args);
            default -> sender.sendMessage(Text.prefixed(plugin, tr("command.backpack.usage")));
        }
    }

    private void listBackpacks(CommandSender sender, SlimeFunXPlugin sfx, ResolvedPlayerRef target) {
        sfx.playerDataService().request(target.offlinePlayer(), profile -> {
            sender.sendMessage(Text.prefixed(plugin, tr("command.backpack.list.header").replace("{player}", target.displayName())));
            if (profile.backpacksCopy().isEmpty()) {
                sender.sendMessage(Text.mm(tr("command.backpack.list.empty")));
                return;
            }
            for (SfxBackpackRecord backpack : profile.backpacksCopy().values()) {
                sender.sendMessage(Text.mm(tr("command.backpack.list.entry")
                        .replace("{id}", Integer.toString(backpack.id()))
                        .replace("{size}", Integer.toString(backpack.size()))
                        .replace("{updated}", formatTimestamp(backpack.updatedAt()))));
            }
        });
    }

    private void openBackpack(CommandSender sender, SlimeFunXPlugin sfx, ResolvedPlayerRef target, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-backpack-open"));
            return;
        }
        if (args.length < 4) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.open.usage")));
            return;
        }
        Integer backpackId = parseNonNegativeInt(args[3]);
        if (backpackId == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.invalid-id")));
            return;
        }
        sfx.backpackListener().openBackpackForAdmin(player, target.offlinePlayer().getUniqueId(), target.displayName(), backpackId);
    }

    private void giveBackpack(CommandSender sender, SlimeFunXPlugin sfx, ResolvedPlayerRef target, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-backpack-give"));
            return;
        }
        if (args.length < 4) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.give.usage")));
            return;
        }
        Integer backpackId = parseNonNegativeInt(args[3]);
        if (backpackId == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.invalid-id")));
            return;
        }
        Integer size = args.length >= 5 ? parseBackpackSizeArg(args[4]) : null;
        if (args.length >= 5 && size == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.backpack.invalid-size")));
            return;
        }
        sfx.backpackListener().giveBackpackItem(player, target.offlinePlayer().getUniqueId(), target.displayName(), backpackId, size);
        player.sendMessage(Text.prefixed(plugin, tr("command.backpack.give.sent")
                .replace("{player}", target.displayName())
                .replace("{id}", Integer.toString(backpackId))));
    }

    private void inspectItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-inspect"));
            return;
        }
        if (!player.hasPermission("sfx.command.inspect")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-inspect")));
            return;
        }
        SfxItemMarker marker = api.items().readMarker(player.getInventory().getItemInMainHand()).orElse(null);
        if (marker == null) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.inspect.no-marker")));
            return;
        }
        sender.sendMessage(Text.mm(tr("command.inspect.header")));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-id").replace("{value}", marker.itemId())));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-version").replace("{value}", Integer.toString(marker.itemVersion()))));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-schema").replace("{value}", Integer.toString(marker.schemaVersion()))));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-variant").replace("{value}", marker.variant())));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-kind").replace("{value}", marker.kind().pdcValue())));
        sender.sendMessage(Text.mm(tr("command.inspect.field.item-flags").replace("{value}", marker.flagsAsString())));
        api.items().readGuideMode(player.getInventory().getItemInMainHand())
                .ifPresent(mode -> sender.sendMessage(Text.mm(tr("command.inspect.field.guide-mode").replace("{value}", mode.pdcValue()))));
    }

    private void listItems(CommandSender sender, int page) {
        if (!sender.hasPermission("sfx.command.list")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-list")));
            return;
        }
        List<SfxItemDefinition> definitions = api.itemRegistry().items().stream().filter(item -> !item.hidden()).toList();
        int pageCount = Math.max(1, (int) Math.ceil(definitions.size() / (double) LIST_SLOTS.length));
        int safePage = Math.max(0, Math.min(page, pageCount - 1));

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm(tr("command.list.header")
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

        SfxMenu.Builder builder = SfxMenu.builder(Text.mm(tr("command.list.menu-title")
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
                .name(safePage > 0 ? tr("guide.pagination.prev.active") : tr("guide.pagination.prev.inactive"))
                .lore(tr("guide.pagination.page").replace("{current}", Integer.toString(safePage + 1)).replace("{total}", Integer.toString(pageCount)))
                .build(), click -> {
            if (safePage > 0) {
                listItems(click.player(), safePage - 1);
            }
        }));
        builder.button(49, new SfxMenuButton(ItemBuilder.of(Material.NETHER_STAR)
                .name(tr("command.list.registry-name"))
                .lore(tr("command.list.registry-lore.0"),
                        tr("command.list.registry-lore.1").replace("{count}", Integer.toString(definitions.size())),
                        "",
                        tr("guide.actions.close"))
                .build(), click -> click.menus().close(click.player())));
        builder.button(52, new SfxMenuButton(ItemBuilder.of(Material.ARROW)
                .name(safePage + 1 < pageCount ? tr("guide.pagination.next.active") : tr("guide.pagination.next.inactive"))
                .lore(tr("guide.pagination.page").replace("{current}", Integer.toString(safePage + 1)).replace("{total}", Integer.toString(pageCount)))
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
            lore.add(Component.text(tr("command.list.registry-entry"), NamedTextColor.GRAY));
            meta.lore(lore.stream().map(Text::noItalic).toList());
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void reload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sfx.command.reload")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-reload")));
            return;
        }
        if (plugin instanceof SlimeFunXPlugin sfxPlugin) {
            if (args.length >= 2 && (args[1].equalsIgnoreCase("runtime") || args[1].equalsIgnoreCase("all"))) {
                boolean ok = sfxPlugin.reloadAllContent();
                sender.sendMessage(Text.prefixed(plugin, ok
                        ? tr("command.reload.success-all")
                        : tr("command.reload.failed-all")));
                return;
            }
            plugin.reloadConfig();
            sfxPlugin.localization().reload();
            sfxPlugin.legacyItemBehaviorConfig().reload();
        } else {
            plugin.reloadConfig();
        }
        sender.sendMessage(Text.prefixed(plugin, tr("command.reload.success")));
    }

    private void handleTemplate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sfx.command.reload")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-reload")));
            return;
        }
        SlimeFunXPlugin sfx = sfxPlugin();
        if (sfx == null) {
            sender.sendMessage("SFX plugin context unavailable.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.template.usage")));
            return;
        }
        if (args[1].equalsIgnoreCase("reload")) {
            boolean ok = sfx.reloadAllContent();
            sender.sendMessage(Text.prefixed(plugin, ok
                    ? tr("command.template.reload-success")
                    : tr("command.reload.failed-all")));
            return;
        }
        if (!args[1].equalsIgnoreCase("compile")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.template.usage")));
            return;
        }
        try {
            SfxTemplateCompileReport report = sfx.compileContentTemplates();
            sender.sendMessage(Text.prefixed(plugin, tr("command.template.compile-success")
                    .replace("{sources}", Integer.toString(report.sourceFiles()))
                    .replace("{outputs}", Integer.toString(report.outputFiles()))));
            for (String warning : report.warnings()) {
                sender.sendMessage(Text.prefixed(plugin, tr("command.template.warning").replace("{warning}", warning)));
            }
        } catch (RuntimeException ex) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.template.compile-failed").replace("{message}", ex.getMessage())));
        }
    }

    private void handleAddon(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sfx.command.reload")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-reload")));
            return;
        }
        SlimeFunXPlugin sfx = sfxPlugin();
        if (sfx == null || sfx.addonManager() == null) {
            sender.sendMessage(Text.prefixed(plugin, "SFX addon manager is not loaded."));
            return;
        }
        if (args.length >= 2 && !args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(Text.prefixed(plugin, "Usage: /sfx addon list"));
            return;
        }
        sender.sendMessage(Text.mm("<green>SFX addons:</green>"));
        sfx.addonManager().loadedAddons().forEach(addon ->
                sender.sendMessage(Text.mm("<gray>- </gray><white>" + addon.id() + "</white> <dark_gray>(" + addon.name() + ")</dark_gray>")));
        sender.sendMessage(Text.mm("<green>SFX addon features:</green>"));
        for (SfxFeature feature : api.features().features()) {
            String state = feature.enabled() ? "<green>enabled</green>" : "<red>disabled</red>";
            sender.sendMessage(Text.mm("<gray>- </gray><white>" + feature.id() + "</white> " + state
                    + " <dark_gray>owner=" + feature.addonId() + " config=" + feature.configPath() + "</dark_gray>"));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Text.mm(tr("command.help.header")));
        sender.sendMessage(Text.mm(tr("command.help.line.guide").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.book").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.cheatguide").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.give").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.research").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.backpack").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.inspect").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.list").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.reload").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.template").replace("{label}", label)));
        sender.sendMessage(Text.mm("<gray>/" + label + " addon list</gray> <dark_gray>- addon diagnostics</dark_gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabCompleter.complete(sender, command, alias, args);
    }

    private SlimeFunXPlugin sfxPlugin() {
        return plugin instanceof SlimeFunXPlugin sfx ? sfx : null;
    }

    private String tr(String key) {
        SlimeFunXPlugin sfx = sfxPlugin();
        return sfx == null ? key : sfx.localization().text(key);
    }

    private String itemDisplayName(SfxItemDefinition definition) {
        SlimeFunXPlugin sfx = sfxPlugin();
        return sfx == null ? definition.nameKey() : plainText(sfx.localization().itemName(definition.id()));
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
