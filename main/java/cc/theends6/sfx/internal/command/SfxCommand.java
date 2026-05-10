package cc.theends6.sfx.internal.command;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
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

    public SfxCommand(JavaPlugin plugin, SfxApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "guide" -> openGuide(sender, GuideMode.SURVIVAL);
            case "cheatguide" -> openGuide(sender, GuideMode.CHEAT);
            case "book" -> giveGuideBook(sender, args.length >= 2 && args[1].equalsIgnoreCase("cheat") ? GuideMode.CHEAT : GuideMode.SURVIVAL);
            case "give" -> giveItem(sender, args);
            case "inspect" -> inspectItem(sender);
            case "list" -> listItems(sender, args.length >= 2 ? parseAmount(args[1]) - 1 : 0);
            case "reload" -> reload(sender);
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
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-cheat-guide", "<red>你没有打开作弊科技书的权限。</red>")));
            return;
        }
        if (mode == GuideMode.SURVIVAL && !player.hasPermission("sfx.command.guide")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-guide", "<red>你没有打开科技书的权限。</red>")));
            return;
        }
        api.guide().open(player, mode);
    }

    private void giveGuideBook(CommandSender sender, GuideMode mode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-book", "Only players can receive the SFX guide."));
            return;
        }
        if (mode == GuideMode.CHEAT && !player.hasPermission("sfx.command.cheatbook")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-cheat-book", "<red>你没有领取作弊科技书的权限。</red>")));
            return;
        }
        if (mode == GuideMode.SURVIVAL && !player.hasPermission("sfx.command.book")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-book", "<red>你没有领取科技书的权限。</red>")));
            return;
        }
        api.items().give(player, api.items().createGuideBook(mode));
        player.sendMessage(Text.prefixed(plugin, mode == GuideMode.CHEAT
                ? tr("command.book.received-cheat", "<green>已领取作弊科技书。</green>")
                : tr("command.book.received", "<green>已领取科技书。</green>")));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-give", "Only players can receive SFX items."));
            return;
        }
        if (!player.hasPermission("sfx.command.give")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-give", "<red>你没有直接获取 SFX 物品的权限。</red>")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.usage", "<red>用法: /slimefunx give <id> [amount]</red>")));
            return;
        }
        String itemId = args[1];
        int amount = args.length >= 3 ? parseAmount(args[2]) : 1;

        SfxItemDefinition definition = api.itemRegistry().item(itemId).orElse(null);
        if (definition == null) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.unknown", "<red>未知的 SFX 物品: </red><gray>{id}</gray>").replace("{id}", itemId)));
            return;
        }
        if (!definition.giveable()) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.not-giveable", "<red>这个 SFX 物品不能直接给予。</red>")));
            return;
        }

        int inserted = giveStacks(player.getInventory(), definition, amount);
        if (inserted <= 0) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.no-space", "<red>背包空间不足，未能给予任何物品。</red>")));
            return;
        }
        if (inserted < amount) {
            player.sendMessage(Text.prefixed(plugin, tr("command.give.partial", "<yellow>背包空间不足，仅给予：</yellow><gray>{item} x{amount}</gray>").replace("{item}", itemDisplayName(definition)).replace("{amount}", Integer.toString(inserted))));
            return;
        }
        player.sendMessage(Text.prefixed(plugin, tr("command.give.success", "<green>已给予：</green><gray>{item} x{amount}</gray>").replace("{item}", itemDisplayName(definition)).replace("{amount}", Integer.toString(inserted))));
    }

    private void inspectItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("command.errors.players-only-inspect", "Only players can inspect SFX items."));
            return;
        }
        if (!player.hasPermission("sfx.command.inspect")) {
            player.sendMessage(Text.prefixed(plugin, tr("command.errors.no-inspect", "<red>你没有检查 SFX PDC 标记的权限。</red>")));
            return;
        }
        SfxItemMarker marker = api.items().readMarker(player.getInventory().getItemInMainHand()).orElse(null);
        if (marker == null) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.inspect.no-marker", "<gray>手中物品没有 SFX PDC 身份标记。</gray>")));
            return;
        }
        sender.sendMessage(Text.mm(tr("command.inspect.header", "<green>SFX 物品标记</green>")));
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
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-list", "<red>你没有查看 SFX 物品列表的权限。</red>")));
            return;
        }
        List<SfxItemDefinition> definitions = api.itemRegistry().items().stream()
                .filter(item -> !item.hidden())
                .toList();
        int pageCount = Math.max(1, (int) Math.ceil(definitions.size() / (double) LIST_SLOTS.length));
        int safePage = Math.max(0, Math.min(page, pageCount - 1));

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm(tr("command.list.header", "<green>SFX 已注册可见物品：</green><gray>{count} 个，页码 {page} / {pages}</gray>").replace("{count}", Integer.toString(definitions.size())).replace("{page}", Integer.toString(safePage + 1)).replace("{pages}", Integer.toString(pageCount))));
            int from = safePage * LIST_SLOTS.length;
            int to = Math.min(definitions.size(), from + LIST_SLOTS.length);
            for (int i = from; i < to; i++) {
                SfxItemDefinition item = definitions.get(i);
                sender.sendMessage(Text.mm("<gray>- </gray><white>" + itemDisplayName(item) + "</white> <dark_gray>(" + item.id() + ")</dark_gray>"));
            }
            return;
        }

        SfxMenu.Builder builder = SfxMenu.builder(Text.mm(tr("command.list.menu-title", "<dark_green>SFX 物品列表</dark_green> <gray>{page} / {pages}</gray>").replace("{page}", Integer.toString(safePage + 1)).replace("{pages}", Integer.toString(pageCount)))).rows(6);
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
            int slot = LIST_SLOTS[i - from];
            builder.button(slot, new SfxMenuButton(listIcon(definition), click -> {
            }));
        }

        builder.button(46, new SfxMenuButton(ItemBuilder.of(Material.ARROW)
                .name(safePage > 0 ? tr("guide.pagination.prev.active", "<yellow>上一页</yellow>") : tr("guide.pagination.prev.inactive", "<dark_gray>上一页</dark_gray>"))
                .lore(tr("guide.pagination.page", "<gray>页码 {current} / {total}</gray>").replace("{current}", Integer.toString(safePage + 1)).replace("{total}", Integer.toString(pageCount)))
                .build(), click -> {
            if (safePage > 0) {
                listItems(click.player(), safePage - 1);
            }
        }));
        builder.button(49, new SfxMenuButton(ItemBuilder.of(Material.NETHER_STAR)
                .name(tr("command.list.registry-name", "<green>SFX 已注册物品</green>"))
                .lore(tr("command.list.registry-lore.0", "<gray>当前只展示玩家可见物品。</gray>"), tr("command.list.registry-lore.1", "<gray>共 {count} 个。</gray>").replace("{count}", Integer.toString(definitions.size())), "", tr("guide.actions.close", "<dark_gray>点击关闭。</dark_gray>"))
                .build(), click -> click.menus().close(click.player())));
        builder.button(52, new SfxMenuButton(ItemBuilder.of(Material.ARROW)
                .name(safePage + 1 < pageCount ? tr("guide.pagination.next.active", "<yellow>下一页</yellow>") : tr("guide.pagination.next.inactive", "<dark_gray>下一页</dark_gray>"))
                .lore(tr("guide.pagination.page", "<gray>页码 {current} / {total}</gray>").replace("{current}", Integer.toString(safePage + 1)).replace("{total}", Integer.toString(pageCount)))
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
            lore.add(Component.text(tr("command.list.registry-entry", "注册表展示项"), NamedTextColor.GRAY));
            meta.lore(lore.stream().map(Text::noItalic).toList());
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("sfx.command.reload")) {
            sender.sendMessage(Text.prefixed(plugin, tr("command.errors.no-reload", "<red>你没有重载 SFX 的权限。</red>")));
            return;
        }
        plugin.reloadConfig();
        if (plugin instanceof cc.theends6.sfx.SlimeFunXPlugin sfxPlugin) {
            sfxPlugin.localization().reload();
        }
        sender.sendMessage(Text.prefixed(plugin, tr("command.reload.success", "<green>SFX 配置与语言文件已重载。</green>")));
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Math.min(100000, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return 1;
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

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Text.mm(tr("command.help.header", "<green>SFX 命令</green>")));
        sender.sendMessage(Text.mm(tr("command.help.line.guide", "<gray>/{label} guide</gray> <dark_gray>- 打开普通科技书</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.book", "<gray>/{label} book [cheat]</gray> <dark_gray>- 领取科技书</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.cheatguide", "<gray>/{label} cheatguide</gray> <dark_gray>- 打开作弊科技书</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.give", "<gray>/{label} give <id> [amount]</gray> <dark_gray>- 直接获取 SFX 物品</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.inspect", "<gray>/{label} inspect</gray> <dark_gray>- 检查手中物品 PDC 标记</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.list", "<gray>/{label} list [page]</gray> <dark_gray>- 打开已注册物品列表</dark_gray>").replace("{label}", label)));
        sender.sendMessage(Text.mm(tr("command.help.line.reload", "<gray>/{label} reload</gray> <dark_gray>- 重载配置</dark_gray>").replace("{label}", label)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("guide", "cheatguide", "book", "give", "inspect", "list", "reload", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("book")) {
            return filter(List.of("cheat"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return filter(List.of("1", "2", "3", "4", "5"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(api.itemRegistry().items().stream().map(SfxItemDefinition::id).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "32", "64", "128", "256", "512", "1024"), args[2]);
        }
        return List.of();
    }

    private String tr(String key, String fallback) {
        if (plugin instanceof cc.theends6.sfx.SlimeFunXPlugin sfxPlugin) {
            return sfxPlugin.localization().text(key, fallback);
        }
        return fallback;
    }


    private String itemDisplayName(SfxItemDefinition definition) {
        if (plugin instanceof cc.theends6.sfx.SlimeFunXPlugin sfxPlugin) {
            return plainText(sfxPlugin.localization().itemName(definition.id(), definition.name()));
        }
        return plainText(definition.name());
    }

    private String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private List<String> filter(Collection<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(option);
            }
        }
        return result;
    }
}
