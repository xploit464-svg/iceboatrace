package com.boatrace.plugin;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

public class RaceManager {

    private final BoatRacePlugin plugin;
    private final Map<String, Race> races = new LinkedHashMap<>();

    public RaceManager(BoatRacePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        races.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("races");
        if (section == null) {
            return;
        }
        for (String raceName : section.getKeys(false)) {
            ConfigurationSection rs = section.getConfigurationSection(raceName);
            if (rs == null) continue;

            Race race = new Race(plugin, raceName);
            race.setEnabled(rs.getBoolean("enabled", true));
            race.setDisplayName(rs.getString("display-name", raceName));
            race.setMinPlayers(rs.getInt("min-players", 2));
            race.setMaxPlayers(rs.getInt("max-players", 12));
            race.setCountdown(rs.getInt("countdown", 5));
            race.setLaps(rs.getInt("laps", 1));
            race.setQueueTimeout(rs.getInt("queue-timeout", 60));
            race.setAllowSpectators(rs.getBoolean("allow-spectators", true));
            race.setLobby(rs.getLocation("lobby"));
            race.setWaitingRoom(rs.getLocation("waiting-room"));
            race.setFinish(rs.getLocation("finish"));
            race.setSpectatorSpawn(rs.getLocation("spectator-spawn"));
            race.setDescription(rs.getString("description", ""));
            race.setDifficulty(rs.getString("difficulty", "Normal"));
            race.setAuthor(rs.getString("author", ""));
            if (rs.contains("weather")) race.setWeather(rs.getString("weather"));
            if (rs.contains("time-of-day")) race.setTimeOfDay(rs.getLong("time-of-day"));

            String boatTypeName = rs.getString("boat-type", "OAK_BOAT");
            try {
                race.setBoatType(EntityType.valueOf(boatTypeName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                race.setBoatType(EntityType.OAK_BOAT);
            }

            ConfigurationSection lanesSection = rs.getConfigurationSection("lanes");
            if (lanesSection != null) {
                for (String key : lanesSection.getKeys(false)) {
                    Location loc = lanesSection.getLocation(key);
                    if (loc != null) {
                        try {
                            race.setLane(Integer.parseInt(key), loc);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            var checkpointList = rs.getList("checkpoints");
            if (checkpointList != null) {
                for (Object obj : checkpointList) {
                    if (obj instanceof Location loc) {
                        race.addCheckpoint(loc);
                    }
                }
            }

            var rewardCommands = rs.getStringList("reward-commands");
            race.getRewardCommands().addAll(rewardCommands);

            ConfigurationSection xpSection = rs.getConfigurationSection("reward-xp");
            if (xpSection != null) {
                for (String key : xpSection.getKeys(false)) {
                    try {
                        race.setRewardXp(Integer.parseInt(key), xpSection.getInt(key));
                    } catch (NumberFormatException ignored) {}
                }
            }

            races.put(raceName.toLowerCase(), race);
        }
    }

    public void save() {
        plugin.getConfig().set("races", null);
        for (Race race : races.values()) {
            ConfigurationSection rs = plugin.getConfig().createSection("races." + race.getName());
            rs.set("enabled", race.isEnabled());
            rs.set("display-name", race.getDisplayName());
            rs.set("min-players", race.getMinPlayers());
            rs.set("max-players", race.getMaxPlayers());
            rs.set("countdown", race.getCountdownSeconds());
            rs.set("laps", race.getLaps());
            rs.set("queue-timeout", race.getQueueTimeoutSeconds());
            rs.set("lobby", race.getLobby());
            rs.set("waiting-room", race.getWaitingRoom());
            rs.set("finish", race.getFinish());
            rs.set("spectator-spawn", race.getSpectatorSpawn());
            rs.set("description", race.getDescription());
            rs.set("difficulty", race.getDifficulty());
            rs.set("author", race.getAuthor());
            rs.set("weather", race.getWeather());
            rs.set("time-of-day", race.getTimeOfDay());
            rs.set("boat-type", race.getBoatType().name());
            rs.set("allow-spectators", race.isAllowSpectators());

            for (Map.Entry<Integer, Location> entry : race.getAllLanes().entrySet()) {
                rs.set("lanes." + entry.getKey(), entry.getValue());
            }

            rs.set("checkpoints", race.getCheckpoints());
            rs.set("reward-commands", race.getRewardCommands());

            for (Map.Entry<Integer, Integer> entry : race.getRewardXp().entrySet()) {
                rs.set("reward-xp." + entry.getKey(), entry.getValue());
            }
        }
        plugin.saveConfig();
    }

    public Race create(String name) {
        if (races.containsKey(name.toLowerCase())) {
            return null;
        }
        Race race = new Race(plugin, name);
        races.put(name.toLowerCase(), race);
        save();
        return race;
    }

    public boolean delete(String name) {
        boolean removed = races.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public Race clone(String oldName, String newName) {
        Race source = get(oldName);
        if (source == null || races.containsKey(newName.toLowerCase())) {
            return null;
        }
        Race copy = new Race(plugin, newName);
        copy.setEnabled(source.isEnabled());
        copy.setDisplayName(newName);
        copy.setMinPlayers(source.getMinPlayers());
        copy.setMaxPlayers(source.getMaxPlayers());
        copy.setCountdown(source.getCountdownSeconds());
        copy.setLaps(source.getLaps());
        copy.setQueueTimeout(source.getQueueTimeoutSeconds());
        copy.setLobby(source.getLobby());
        copy.setWaitingRoom(source.getWaitingRoom());
        copy.setFinish(source.getFinish());
        copy.setBoatType(source.getBoatType());
        copy.setSpectatorSpawn(source.getSpectatorSpawn());
        copy.setAllowSpectators(source.isAllowSpectators());
        copy.setDescription(source.getDescription());
        copy.setDifficulty(source.getDifficulty());
        copy.setAuthor(source.getAuthor());
        copy.setWeather(source.getWeather());
        copy.setTimeOfDay(source.getTimeOfDay());
        for (Map.Entry<Integer, Location> entry : source.getAllLanes().entrySet()) {
            copy.setLane(entry.getKey(), entry.getValue());
        }
        for (Location cp : source.getCheckpoints()) {
            copy.addCheckpoint(cp);
        }
        copy.getRewardCommands().addAll(source.getRewardCommands());
        for (Map.Entry<Integer, Integer> entry : source.getRewardXp().entrySet()) {
            copy.setRewardXp(entry.getKey(), entry.getValue());
        }
        races.put(newName.toLowerCase(), copy);
        save();
        return copy;
    }

    public boolean rename(String oldName, String newName) {
        Race cloned = clone(oldName, newName);
        if (cloned == null) return false;
        delete(oldName);
        return true;
    }

    public Race get(String name) {
        return races.get(name.toLowerCase());
    }

    public boolean exists(String name) {
        return races.containsKey(name.toLowerCase());
    }

    public Map<String, Race> getAllRaces() {
        return races;
    }
}
