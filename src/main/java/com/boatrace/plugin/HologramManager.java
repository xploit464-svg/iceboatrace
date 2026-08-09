package com.boatrace.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HologramManager {

    // One entry per hologram: race name, "wins" or "fastest", the display entity's UUID, and its location
    private static class HologramEntry {
        String raceName;
        String type;
        UUID entityId;
        Location location;
    }

    private final BoatRacePlugin plugin;
    private final List<HologramEntry> holograms = new ArrayList<>();

    public HologramManager(BoatRacePlugin plugin) {
        this.plugin = plugin;
        load();
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshAll();
            }
        }.runTaskTimer(plugin, 100L, 100L); // refresh every 5 seconds
    }

    public void create(String raceName, String type, Location location) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.text(Component.text("Loading leaderboard..."));

        HologramEntry entry = new HologramEntry();
        entry.raceName = raceName;
        entry.type = type;
        entry.entityId = display.getUniqueId();
        entry.location = location;
        holograms.add(entry);

        refresh(entry, display);
        save();
    }

    public boolean removeNearest(Location location, double radius) {
        HologramEntry closest = null;
        double closestDist = radius;
        for (HologramEntry entry : holograms) {
            if (entry.location.getWorld().equals(location.getWorld())) {
                double dist = entry.location.distance(location);
                if (dist <= closestDist) {
                    closest = entry;
                    closestDist = dist;
                }
            }
        }
        if (closest == null) return false;

        Entity entity = Bukkit.getEntity(closest.entityId);
        if (entity != null) entity.remove();
        holograms.remove(closest);
        save();
        return true;
    }

    private void refreshAll() {
        for (HologramEntry entry : holograms) {
            Entity entity = Bukkit.getEntity(entry.entityId);
            if (entity instanceof TextDisplay display) {
                refresh(entry, display);
            }
        }
    }

    private void refresh(HologramEntry entry, TextDisplay display) {
        if (entry.type.equals("queue")) {
            refreshQueue(entry, display);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(entry.raceName).append(" - ").append(entry.type.equals("wins") ? "Most Wins" : "Fastest Times").append("\n");

        List<String> lines = entry.type.equals("wins")
                ? plugin.getStatsManager().getWinsLeaderboard(10)
                : plugin.getStatsManager().getFastestLeaderboard(entry.raceName, 10);

        if (lines.isEmpty()) {
            sb.append("No data yet");
        } else {
            for (int i = 0; i < lines.size(); i++) {
                sb.append(i + 1).append(". ").append(lines.get(i)).append("\n");
            }
        }

        display.text(Component.text(sb.toString().stripTrailing()));
    }

    private void refreshQueue(HologramEntry entry, TextDisplay display) {
        Race race = plugin.getRaceManager().get(entry.raceName);
        if (race == null) {
            display.text(Component.text(entry.raceName + " - race no longer exists"));
            return;
        }
        display.text(Component.text(race.getDisplayName() + "\n"
                + "Queue: " + race.getQueueSize() + "/" + race.getMaxPlayers() + "\n"
                + "Status: " + race.getStatus()));
    }

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("holograms");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection hs = section.getConfigurationSection(key);
            if (hs == null) continue;
            HologramEntry entry = new HologramEntry();
            entry.raceName = hs.getString("race");
            entry.type = hs.getString("type");
            entry.location = hs.getLocation("location");
            String idStr = hs.getString("entity-id");
            if (idStr != null) entry.entityId = UUID.fromString(idStr);
            if (entry.location != null && entry.entityId != null) {
                holograms.add(entry);
            }
        }
    }

    private void save() {
        plugin.getConfig().set("holograms", null);
        int i = 0;
        for (HologramEntry entry : holograms) {
            String base = "holograms.h" + (i++);
            plugin.getConfig().set(base + ".race", entry.raceName);
            plugin.getConfig().set(base + ".type", entry.type);
            plugin.getConfig().set(base + ".location", entry.location);
            plugin.getConfig().set(base + ".entity-id", entry.entityId.toString());
        }
        plugin.saveConfig();
    }
}
