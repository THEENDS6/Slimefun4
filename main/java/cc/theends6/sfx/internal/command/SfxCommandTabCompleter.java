package cc.theends6.sfx.internal.command;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.internal.research.SfxResearchDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxCommandTabCompleter {
    private final JavaPlugin plugin;
    private final SfxApi api;

    SfxCommandTabCompleter(JavaPlugin plugin, SfxApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    List<String> complete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("guide", "cheatguide", "book", "give", "research", "backpack", "inspect", "list", "reload", "template", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("book")) {
            return filter(List.of("cheat"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return filter(List.of("config", "runtime", "all"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates"))) {
            return filter(List.of("compile", "reload"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("research")) {
            return filter(knownPlayerNames(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("research")) {
            List<String> options = new ArrayList<>(List.of("all", "reset"));
            SlimeFunXPlugin sfx = sfxPlugin();
            if (sfx != null) {
                options.addAll(sfx.researchService().allResearches().stream().map(SfxResearchDefinition::id).toList());
            }
            return filter(options, args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("backpack")) {
            return filter(List.of("list", "open", "give"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("backpack")) {
            return filter(knownPlayerNames(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("backpack")) {
            return filter(List.of("0", "1", "2", "3", "4", "5"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("backpack") && args[1].equalsIgnoreCase("give")) {
            return filter(List.of("1", "2", "3", "4", "5", "6", "9", "18", "27", "36", "45", "54"), args[4]);
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

    private SlimeFunXPlugin sfxPlugin() {
        return plugin instanceof SlimeFunXPlugin sfx ? sfx : null;
    }

    private List<String> knownPlayerNames() {
        List<String> values = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            values.add(player.getName());
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && !values.contains(player.getName())) {
                values.add(player.getName());
            }
        }
        return values;
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
