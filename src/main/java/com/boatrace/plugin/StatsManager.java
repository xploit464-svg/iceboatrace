package com.boatrace.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class StatsManager {

    private final BoatRacePlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public StatsManager(BoatRacePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private String path(UUID uuid, String key) {
        return "players." + uuid + "." + key;
    }

    public void recordFinish(UUID uuid, String raceName, long timeMillis, int place) {
        int races = data.getInt(path(uuid, "races"), 0) + 1;
        data.set(path(uuid, "races"), races);

        if (place == 1) {
            int wins = data.getInt(path(uuid, "wins"), 0) + 1;
            int streak = data.getInt(path(uuid, "streak"), 0) + 1;
            int bestStreak = Math.max(streak, data.getInt(path(uuid, "best-streak"), 0));
            data.set(path(uuid, "wins"), wins);
            data.set(path(uuid, "streak"), streak);
            data.set(path(uuid, "best-streak"), bestStreak);
        } else {
            data.set(path(uuid, "losses"), data.getInt(path(uuid, "losses"), 0) + 1);
            data.set(path(uuid, "streak"), 0);
        }

        String bestKey = path(uuid, "best-times." + raceName);
        long currentBest = data.getLong(bestKey, Long.MAX_VALUE);
        if (timeMillis < currentBest) {
            data.set(bestKey, timeMillis);
        }

        save();
    }

    public void recordDnf(UUID uuid) {
        data.set(path(uuid, "races"), data.getInt(path(uuid, "races"), 0) + 1);
        data.set(path(uuid, "losses"), data.getInt(path(uuid, "losses"), 0) + 1);
        data.set(path(uuid, "streak"), 0);
        save();
    }

    public int getWins(UUID uuid) { return data.getInt(path(uuid, "wins"), 0); }
    public int getLosses(UUID uuid) { return data.getInt(path(uuid, "losses"), 0); }
    public int getRaces(UUID uuid) { return data.getInt(path(uuid, "races"), 0); }
    public int getStreak(UUID uuid) { return data.getInt(path(uuid, "streak"), 0); }
    public int getBestStreak(UUID uuid) { return data.getInt(path(uuid, "best-streak"), 0); }

    public Long getPersonalBest(UUID uuid, String raceName) {
        long value = data.getLong(path(uuid, "best-times." + raceName), -1);
        return value == -1 ? null : value;
    }

    // Top N players by fastest personal-best time on a given race
    public List<String> getFastestLeaderboard(String raceName, int limit) {
        var section = data.getConfigurationSection("players");
        if (section == null) return List.of();

        return section.getKeys(false).stream()
                .map(uuidStr -> Map.entry(uuidStr, data.getLong("players." + uuidStr + ".best-times." + raceName, -1)))
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparingLong(Map.Entry::getValue))
                .limit(limit)
                .map(e -> {
                    String name = plugin.getServer().getOfflinePlayer(UUID.fromString(e.getKey())).getName();
                    return (name != null ? name : "Unknown") + " - " + Race.formatTime(e.getValue());
                })
                .collect(Collectors.toList());
    }

    // Top N players by total wins across all races
    public List<String> getWinsLeaderboard(int limit) {
        var section = data.getConfigurationSection("players");
        if (section == null) return List.of();

        return section.getKeys(false).stream()
                .map(uuidStr -> Map.entry(uuidStr, data.getInt("players." + uuidStr + ".wins", 0)))
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed())
                .limit(limit)
                .map(e -> {
                    String name = plugin.getServer().getOfflinePlayer(UUID.fromString(e.getKey())).getName();
                    return (name != null ? name : "Unknown") + " - " + e.getValue() + " win(s)";
                })
                .collect(Collectors.toList());
    }

    public void sendStats(Player p) {
        UUID id = p.getUniqueId();
        int wins = getWins(id);
        int losses = getLosses(id);
        int races = getRaces(id);
        double winRate = races == 0 ? 0 : (wins * 100.0 / races);

        p.sendMessage(net.kyori.adventure.text.Component.text("--- Your BoatRace Stats ---"));
        p.sendMessage(net.kyori.adventure.text.Component.text("Wins: " + wins + "  Losses: " + losses + "  Total races: " + races));
        p.sendMessage(net.kyori.adventure.text.Component.text(String.format("Win rate: %.1f%%", winRate)));
        p.sendMessage(net.kyori.adventure.text.Component.text("Current streak: " + getStreak(id) + "  Best streak: " + getBestStreak(id)));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save stats.yml: " + e.getMessage());
        }
    }
}
