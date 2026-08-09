package com.boatrace.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BoatCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> GLOBAL_SUBCOMMANDS = Set.of("create", "delete", "list", "info", "reload", "clone", "rename", "hologram");
    private static final List<String> RACE_ACTIONS = List.of(
            "setlobby", "setwaiting", "setfinish", "setspawn", "setspectator", "setminplayers", "setmaxplayers",
            "setcountdown", "setlaps", "setqueuetimeout", "setboat", "setlane", "removelane", "listlanes", "checkpoint",
            "setdescription", "setdifficulty", "setauthor", "setweather", "settime", "checklanes",
            "start", "stop", "forcestart", "forcestop", "reset", "enable", "disable", "validate"
    );
    private static final List<String> WEATHER_TYPES = List.of("clear", "rain", "thunder", "none");
    private static final List<String> TIME_PRESETS = List.of("day", "noon", "night", "midnight", "none");
    private static final List<String> BOAT_TYPES = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo");

    private final BoatRacePlugin plugin;
    private final RaceManager raceManager;

    public BoatCommand(BoatRacePlugin plugin, RaceManager raceManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boatrace.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String first = args[0].toLowerCase();

        if (GLOBAL_SUBCOMMANDS.contains(first)) {
            handleGlobal(sender, first, args);
            return true;
        }

        // Otherwise args[0] is a race name, args[1] is the action on that race
        String raceName = args[0];
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }
        handleRaceAction(sender, raceName, args[1].toLowerCase(), args);
        return true;
    }

    // ---- /boatrace create|delete|list|info|reload|clone|hologram ----

    private void handleGlobal(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "create" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /boatrace create <name>")); return; }
                Race r = raceManager.create(args[1]);
                sender.sendMessage(Component.text(r != null ? "Created race '" + args[1] + "'." : "A race named '" + args[1] + "' already exists."));
            }
            case "delete" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /boatrace delete <name>")); return; }
                boolean ok = raceManager.delete(args[1]);
                sender.sendMessage(Component.text(ok ? "Deleted race '" + args[1] + "'." : "No race called '" + args[1] + "'."));
            }
            case "list" -> {
                if (raceManager.getAllRaces().isEmpty()) {
                    sender.sendMessage(Component.text("No races have been created yet."));
                    return;
                }
                sender.sendMessage(Component.text("--- Races ---"));
                for (Race r : raceManager.getAllRaces().values()) {
                    sender.sendMessage(Component.text(r.getName() + " (" + r.getStatus() + ", " + (r.isEnabled() ? "enabled" : "disabled")
                            + ") - " + r.getQueueSize() + "/" + r.getMaxPlayers()));
                }
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /boatrace info <name>")); return; }
                Race r = raceManager.get(args[1]);
                if (r == null) { sender.sendMessage(Component.text("No race called '" + args[1] + "'.")); return; }
                sendInfo(sender, r);
            }
            case "reload" -> {
                raceManager.load();
                sender.sendMessage(Component.text("Config reloaded. " + raceManager.getAllRaces().size() + " race(s) loaded."));
            }
            case "clone" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace clone <old> <new>")); return; }
                Race r = raceManager.clone(args[1], args[2]);
                sender.sendMessage(Component.text(r != null ? "Cloned '" + args[1] + "' into '" + args[2] + "'."
                        : "Couldn't clone - check the source race exists and the new name is free."));
            }
            case "rename" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace rename <old> <new>")); return; }
                boolean ok = raceManager.rename(args[1], args[2]);
                sender.sendMessage(Component.text(ok ? "Renamed '" + args[1] + "' to '" + args[2] + "'."
                        : "Couldn't rename - check the race exists and the new name is free."));
            }
            case "hologram" -> handleHologram(sender, args);
        }
    }

    private void handleHologram(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player standing at the spot can place a hologram."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /boatrace hologram create <race> <wins|fastest|queue>  OR  /boatrace hologram remove"));
            return;
        }
        String action = args[1].toLowerCase();
        if (action.equals("create")) {
            Set<String> validTypes = Set.of("wins", "fastest", "queue");
            if (args.length < 4 || !validTypes.contains(args[3].toLowerCase())) {
                sender.sendMessage(Component.text("Usage: /boatrace hologram create <race> <wins|fastest|queue>"));
                return;
            }
            String raceName = args[2];
            if (!raceManager.exists(raceName)) {
                sender.sendMessage(Component.text("No race called '" + raceName + "'."));
                return;
            }
            plugin.getHologramManager().create(raceName, args[3].toLowerCase(), player.getLocation());
            sender.sendMessage(Component.text("Hologram placed."));
        } else if (action.equals("remove")) {
            boolean removed = plugin.getHologramManager().removeNearest(player.getLocation(), 5.0);
            sender.sendMessage(Component.text(removed ? "Nearest hologram removed." : "No hologram found nearby."));
        }
    }

    private void sendInfo(CommandSender sender, Race r) {
        sender.sendMessage(Component.text("--- " + r.getDisplayName() + " (" + r.getName() + ") ---"));
        if (!r.getDescription().isBlank()) sender.sendMessage(Component.text(r.getDescription()));
        sender.sendMessage(Component.text("Difficulty: " + r.getDifficulty() + (r.getAuthor().isBlank() ? "" : "   Author: " + r.getAuthor())));
        sender.sendMessage(Component.text("Status: " + r.getStatus() + "  Enabled: " + r.isEnabled()));
        sender.sendMessage(Component.text("Players: " + r.getQueueSize() + " queued (min " + r.getMinPlayers() + ", max " + r.getMaxPlayers() + ")"));
        sender.sendMessage(Component.text("Countdown: " + r.getCountdownSeconds() + "s   Laps: " + r.getLaps() + "   Boat: " + r.getBoatType()));
        sender.sendMessage(Component.text("Queue timeout: " + r.getQueueTimeoutSeconds() + "s after the first player joins"));
        sender.sendMessage(Component.text("Lanes: " + r.getLaneCount() + "   Checkpoints: " + r.getCheckpointCount()));
        sender.sendMessage(Component.text("Weather: " + (r.getWeather() == null ? "unchanged" : r.getWeather())
                + "   Time: " + (r.getTimeOfDay() == null ? "unchanged" : r.getTimeOfDay())));
        sender.sendMessage(Component.text("Lobby: " + (r.getLobby() != null ? "set" : "not set")
                + "   Waiting room: " + (r.getWaitingRoom() != null ? "set" : "not set")
                + "   Finish: " + (r.getFinish() != null ? "set" : "not set")
                + "   Spectator spawn: " + (r.getSpectatorSpawn() != null ? "set" : "not set")));
    }

    // ---- /boatrace <name> <action> ----

    private void handleRaceAction(CommandSender sender, String raceName, String action, String[] args) {
        if (action.equals("checkpoint")) {
            handleCheckpoint(sender, raceName, args);
            return;
        }

        Race race = raceManager.get(raceName);

        switch (action) {
            case "setlobby", "setwaiting", "setfinish", "setspawn", "setspectator" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only a player standing at the spot can set this."));
                    return;
                }
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                Location loc = player.getLocation();
                switch (action) {
                    case "setlobby" -> race.setLobby(loc);
                    case "setfinish" -> race.setFinish(loc);
                    case "setwaiting", "setspawn" -> race.setWaitingRoom(loc);
                    case "setspectator" -> race.setSpectatorSpawn(loc);
                }
                raceManager.save();
                sender.sendMessage(Component.text(action + " set for '" + raceName + "'."));
            }
            case "setmaxplayers" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                Integer n = parseInt(args, 2);
                if (n == null || !race.setMaxPlayers(n)) { sender.sendMessage(Component.text("Enter a number between 1 and " + Race.MAX_LANES + ".")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("Max players for '" + raceName + "' set to " + n + "."));
            }
            case "setminplayers" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                Integer n = parseInt(args, 2);
                if (n == null || !race.setMinPlayers(n)) { sender.sendMessage(Component.text("Enter a number between 1 and " + Race.MAX_LANES + ".")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("Min players for '" + raceName + "' set to " + n + "."));
            }
            case "setcountdown" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                Integer n = parseInt(args, 2);
                if (n == null || !race.setCountdown(n)) { sender.sendMessage(Component.text("Enter a countdown length between 1 and 60 seconds.")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("Countdown for '" + raceName + "' set to " + n + "s."));
            }
            case "setlaps" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                Integer n = parseInt(args, 2);
                if (n == null || !race.setLaps(n)) { sender.sendMessage(Component.text("Enter a number of laps between 1 and 100.")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("'" + raceName + "' now requires " + n + " lap(s) to finish."));
            }
            case "setqueuetimeout" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                Integer n = parseInt(args, 2);
                if (n == null || !race.setQueueTimeout(n)) { sender.sendMessage(Component.text("Enter a number of seconds between 5 and 3600.")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("'" + raceName + "' will force-start " + n + "s after the first player joins, if it hasn't filled up."));
            }
            case "setdescription" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " setdescription <text>")); return; }
                race.setDescription(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                raceManager.save();
                sender.sendMessage(Component.text("Description set for '" + raceName + "'."));
            }
            case "setdifficulty" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " setdifficulty <text>")); return; }
                race.setDifficulty(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                raceManager.save();
                sender.sendMessage(Component.text("Difficulty set for '" + raceName + "'."));
            }
            case "setauthor" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " setauthor <text>")); return; }
                race.setAuthor(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                raceManager.save();
                sender.sendMessage(Component.text("Author set for '" + raceName + "'."));
            }
            case "setweather" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " setweather <clear|rain|thunder|none>")); return; }
                String value = args[2].equalsIgnoreCase("none") ? null : args[2];
                if (!race.setWeather(value)) { sender.sendMessage(Component.text("Use clear, rain, thunder, or none.")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("Weather for '" + raceName + "' set to " + (value == null ? "unchanged (none)" : value) + ". Applies to the lane world when the race starts."));
            }
            case "settime" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " settime <day|noon|night|midnight|none|<ticks 0-24000>>")); return; }
                String arg = args[2].toLowerCase();
                Long ticks = switch (arg) {
                    case "day" -> 1000L;
                    case "noon" -> 6000L;
                    case "night" -> 13000L;
                    case "midnight" -> 18000L;
                    case "none" -> null;
                    default -> parseLong(arg);
                };
                if (!arg.equals("none") && ticks == null) { sender.sendMessage(Component.text("Use day, noon, night, midnight, none, or a tick value 0-24000.")); return; }
                if (!race.setTimeOfDay(ticks)) { sender.sendMessage(Component.text("Enter a tick value between 0 and 24000.")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("Time for '" + raceName + "' set to " + (ticks == null ? "unchanged (none)" : ticks) + ". Applies to the lane world when the race starts."));
            }
            case "checklanes" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                var occupancy = race.checkLaneOccupancy();
                if (occupancy.isEmpty()) { sender.sendMessage(Component.text("No lanes set up for '" + raceName + "'.")); return; }
                sender.sendMessage(Component.text("--- Lane occupancy for '" + raceName + "' ---"));
                occupancy.forEach((laneNum, occupied) ->
                        sender.sendMessage(Component.text("Lane " + laneNum + ": " + (occupied ? "OCCUPIED" : "clear"))));
            }
            case "setboat" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " setboat <oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|bamboo>")); return; }
                String typeName = args[2].toUpperCase() + (args[2].equalsIgnoreCase("bamboo") ? "_RAFT" : "_BOAT");
                org.bukkit.entity.EntityType type;
                try { type = org.bukkit.entity.EntityType.valueOf(typeName); } catch (IllegalArgumentException e) { type = null; }
                if (type == null || !race.setBoatType(type)) {
                    sender.sendMessage(Component.text("Unknown boat type. Try: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, bamboo"));
                    return;
                }
                raceManager.save();
                sender.sendMessage(Component.text("Boat type for '" + raceName + "' set to " + args[2] + "."));
            }
            case "setlane" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Only a player standing at the spot can set a lane.")); return; }
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                Integer n = parseInt(args, 2);
                if (n == null || !race.setLane(n, player.getLocation())) { sender.sendMessage(Component.text("Lane number must be between 1 and " + Race.MAX_LANES + ".")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("Lane " + n + " set for '" + raceName + "'."));
            }
            case "removelane" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                Integer n = parseInt(args, 2);
                if (n == null) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " removelane <lane>")); return; }
                boolean removed = race.removeLane(n);
                if (removed) raceManager.save();
                sender.sendMessage(Component.text(removed ? "Lane " + n + " removed." : "No lane " + n + " to remove."));
            }
            case "listlanes" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                sender.sendMessage(Component.text(race.getLaneCount() + " lane(s): " + race.getAllLanes().keySet()));
            }
            case "start", "forcestart" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                sender.sendMessage(Component.text(race.forceStart()));
            }
            case "stop", "forcestop" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                sender.sendMessage(Component.text(race.forceStop()));
            }
            case "reset" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                race.resetQueue();
                sender.sendMessage(Component.text("Race '" + raceName + "' has been reset."));
            }
            case "enable" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                if (!race.isReadyToEnable()) {
                    sender.sendMessage(Component.text("Can't enable '" + raceName + "' - run /boatrace " + raceName + " validate to see what's missing."));
                    return;
                }
                race.setEnabled(true);
                raceManager.save();
                sender.sendMessage(Component.text("Race '" + raceName + "' enabled."));
            }
            case "disable" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                race.setEnabled(false);
                raceManager.save();
                sender.sendMessage(Component.text("Race '" + raceName + "' disabled."));
            }
            case "validate" -> {
                if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
                sendValidation(sender, race);
            }
            default -> sendHelp(sender);
        }
    }

    private void handleCheckpoint(CommandSender sender, String raceName, String[] args) {
        Race race = raceManager.get(raceName);
        if (race == null) { sender.sendMessage(Component.text("No race called '" + raceName + "'.")); return; }
        if (args.length < 3) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " checkpoint <add|remove|list>")); return; }

        String sub = args[2].toLowerCase();
        switch (sub) {
            case "add" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Only a player standing at the spot can add a checkpoint.")); return; }
                race.addCheckpoint(player.getLocation());
                raceManager.save();
                sender.sendMessage(Component.text("Checkpoint " + race.getCheckpointCount() + " added to '" + raceName + "'."));
            }
            case "remove" -> {
                Integer id = parseInt(args, 3);
                if (id == null || !race.removeCheckpoint(id)) { sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " checkpoint remove <id>")); return; }
                raceManager.save();
                sender.sendMessage(Component.text("Checkpoint " + id + " removed."));
            }
            case "list" -> sender.sendMessage(Component.text(race.getCheckpointCount() + " checkpoint(s) on '" + raceName + "'."));
            default -> sender.sendMessage(Component.text("Usage: /boatrace " + raceName + " checkpoint <add|remove|list>"));
        }
    }

    private void sendValidation(CommandSender sender, Race race) {
        sender.sendMessage(Component.text("/boatrace " + race.getName() + " validate", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(""));
        boolean allOk = true;
        for (String result : race.validate()) {
            String[] parts = result.split("\\|", 2);
            switch (parts[0]) {
                case "OK" -> sender.sendMessage(Component.text("\u2714 " + parts[1], NamedTextColor.GREEN));
                case "FAIL" -> { allOk = false; sender.sendMessage(Component.text("\u2718 " + parts[1], NamedTextColor.RED)); }
                default -> sender.sendMessage(Component.text("\u2139 " + parts[1], NamedTextColor.GRAY));
            }
        }
        sender.sendMessage(Component.text(""));
        sender.sendMessage(allOk
                ? Component.text("Race is ready.", NamedTextColor.GREEN)
                : Component.text("Race cannot be enabled.", NamedTextColor.RED));
    }

    private Integer parseInt(String[] args, int index) {
        if (args.length <= index) return null;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- BoatRace Admin Commands ---", NamedTextColor.GOLD));
        sendCmd(sender, "/boatrace create <name>");
        sendCmd(sender, "/boatrace delete <name>");
        sendCmd(sender, "/boatrace list");
        sendCmd(sender, "/boatrace info <name>");
        sendCmd(sender, "/boatrace clone <old> <new>   |  rename <old> <new>");
        sendCmd(sender, "/boatrace reload");
        sendCmd(sender, "/boatrace hologram create <race> <wins|fastest|queue>  |  /boatrace hologram remove");
        sendCmd(sender, "/boatrace <name> setlobby|setwaiting|setfinish|setspawn|setspectator");
        sendCmd(sender, "/boatrace <name> setmaxplayers|setminplayers <n>");
        sendCmd(sender, "/boatrace <name> setcountdown <seconds>");
        sendCmd(sender, "/boatrace <name> setlaps <number>");
        sendCmd(sender, "/boatrace <name> setqueuetimeout <seconds>   - force-start this long after the first player joins");
        sendCmd(sender, "/boatrace <name> setboat <oak|spruce|birch|...>");
        sendCmd(sender, "/boatrace <name> setdescription|setdifficulty|setauthor <text>");
        sendCmd(sender, "/boatrace <name> setweather <clear|rain|thunder|none>");
        sendCmd(sender, "/boatrace <name> settime <day|noon|night|midnight|none|<ticks>>");
        sendCmd(sender, "/boatrace <name> checklanes   - shows if anything is currently sitting on a lane spot");
        sendCmd(sender, "/boatrace <name> setlane|removelane <lane>   |  listlanes");
        sendCmd(sender, "/boatrace <name> checkpoint add|remove <id>|list");
        sendCmd(sender, "/boatrace <name> start|stop|forcestart|forcestop|reset");
        sendCmd(sender, "/boatrace <name> enable|disable|validate");
    }

    private void sendCmd(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.AQUA));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.addAll(GLOBAL_SUBCOMMANDS);
            options.addAll(raceManager.getAllRaces().keySet());
        } else if (args.length == 2) {
            String first = args[0].toLowerCase();
            if (first.equals("delete") || first.equals("info") || first.equals("clone")) {
                options.addAll(raceManager.getAllRaces().keySet());
            } else if (first.equals("hologram")) {
                options.addAll(List.of("create", "remove"));
            } else if (!GLOBAL_SUBCOMMANDS.contains(first)) {
                options.addAll(RACE_ACTIONS);
            }
        } else if (args.length == 3) {
            String first = args[0].toLowerCase();
            String second = args[1].toLowerCase();
            if (first.equals("hologram") && second.equals("create")) {
                options.addAll(raceManager.getAllRaces().keySet());
            } else if (second.equals("checkpoint")) {
                options.addAll(List.of("add", "remove", "list"));
            } else if (second.equals("setboat")) {
                options.addAll(BOAT_TYPES);
            } else if (second.equals("setweather")) {
                options.addAll(WEATHER_TYPES);
            } else if (second.equals("settime")) {
                options.addAll(TIME_PRESETS);
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("hologram") && args[1].equalsIgnoreCase("create")) {
                options.addAll(List.of("wins", "fastest", "queue"));
            }
        }

        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(args[args.length - 1], options, matches);
        return matches;
    }
}
