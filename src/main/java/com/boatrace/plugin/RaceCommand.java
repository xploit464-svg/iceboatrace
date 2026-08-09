package com.boatrace.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RaceCommand implements CommandExecutor, TabCompleter {

    private final BoatRacePlugin plugin;
    private final RaceManager raceManager;

    public RaceCommand(BoatRacePlugin plugin, RaceManager raceManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            plugin.getRaceGui().openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "join" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /race join <name>")); return true; }
                Race race = raceManager.get(args[1]);
                if (race == null) { player.sendMessage(Component.text("No race called '" + args[1] + "'.")); return true; }
                player.sendMessage(Component.text(race.joinQueue(player)));
            }
            case "leave" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /race leave <name>")); return true; }
                Race race = raceManager.get(args[1]);
                if (race == null) { player.sendMessage(Component.text("No race called '" + args[1] + "'.")); return true; }
                player.sendMessage(Component.text(race.leaveQueue(player)));
            }
            case "list" -> handleList(player);
            case "stats" -> plugin.getStatsManager().sendStats(player);
            case "leaderboard" -> handleLeaderboard(player, args);
            case "leavespectate" -> plugin.getRaceGui().stopSpectating(player);
            case "leavequeue" -> handleLeaveQueue(player);
            case "spectate" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /race spectate <name>")); return true; }
                Race race = raceManager.get(args[1]);
                if (race == null) { player.sendMessage(Component.text("No race called '" + args[1] + "'.")); return true; }
                plugin.getRaceGui().startSpectating(player, race);
            }
            case "help" -> sendUsage(player);
            default -> sendUsage(player);
        }

        return true;
    }

    private void handleLeaveQueue(Player player) {
        boolean left = false;
        for (Race race : raceManager.getAllRaces().values()) {
            String result = race.leaveQueue(player);
            if (!result.startsWith("You're not")) {
                player.sendMessage(Component.text(result));
                left = true;
            }
        }
        if (!left) player.sendMessage(Component.text("You're not in any queue."));
    }

    private void handleList(Player player) {
        Map<String, Race> races = raceManager.getAllRaces();
        if (races.isEmpty()) {
            player.sendMessage(Component.text("No races have been set up yet."));
            return;
        }
        player.sendMessage(Component.text("--- Races ---"));
        for (Race race : races.values()) {
            if (!race.isEnabled()) continue;
            player.sendMessage(Component.text(race.getDisplayName() + ": " + race.getQueueSize() + "/" + race.getMaxPlayers()
                    + " (" + race.getStatus() + ")"));
        }
    }

    private void handleLeaderboard(Player player, String[] args) {
        if (args.length < 3 || (!args[2].equalsIgnoreCase("wins") && !args[2].equalsIgnoreCase("fastest"))) {
            player.sendMessage(Component.text("Usage: /race leaderboard <track> <wins|fastest>"));
            return;
        }
        String raceName = args[1];
        List<String> lines = args[2].equalsIgnoreCase("wins")
                ? plugin.getStatsManager().getWinsLeaderboard(10)
                : plugin.getStatsManager().getFastestLeaderboard(raceName, 10);

        player.sendMessage(Component.text("--- " + raceName + " Leaderboard (" + args[2] + ") ---"));
        if (lines.isEmpty()) {
            player.sendMessage(Component.text("No data yet."));
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            player.sendMessage(Component.text((i + 1) + ". " + lines.get(i)));
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("--- BoatRace Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/race            - opens the race menu", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/race join <name>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/race leave <name>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/race leavequeue           - leaves whichever queue you're in", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/race spectate <name>", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/race leavespectate", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/race list", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/race stats", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/race leaderboard <name> <wins|fastest>", NamedTextColor.AQUA));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.addAll(List.of("join", "leave", "list", "stats", "leaderboard", "leavespectate", "leavequeue", "spectate", "help", "gui"));
        } else if (args.length == 2) {
            String first = args[0].toLowerCase();
            if (first.equals("join") || first.equals("leave") || first.equals("leaderboard") || first.equals("spectate")) {
                options.addAll(raceManager.getAllRaces().keySet());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("leaderboard")) {
            options.addAll(List.of("wins", "fastest"));
        }

        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(args[args.length - 1], options, matches);
        return matches;
    }
}
