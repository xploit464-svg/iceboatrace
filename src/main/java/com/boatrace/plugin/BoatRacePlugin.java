package com.boatrace.plugin;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BoatRacePlugin extends JavaPlugin {

    private RaceManager raceManager;
    private StatsManager statsManager;
    private HologramManager hologramManager;
    private DiscordWebhookManager discordWebhookManager;
    private RaceGui raceGui;

    private final Set<UUID> frozenPlayers = new HashSet<>();
    // Which race (by name) each currently-racing player is in, so listeners can look it up fast
    private final Map<UUID, String> activeRaceOf = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        raceManager = new RaceManager(this);
        statsManager = new StatsManager(this);
        hologramManager = new HologramManager(this);
        discordWebhookManager = new DiscordWebhookManager(this);
        raceGui = new RaceGui(this, raceManager);

        getCommand("race").setExecutor(new RaceCommand(this, raceManager));
        getCommand("boatrace").setExecutor(new BoatCommand(this, raceManager));

        getServer().getPluginManager().registerEvents(raceGui, this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new CheckpointListener(this), this);
        getServer().getPluginManager().registerEvents(new FinishListener(this), this);
        getServer().getPluginManager().registerEvents(new AntiCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinSignListener(raceManager), this);
        getServer().getPluginManager().registerEvents(new DisconnectListener(this), this);
        getServer().getPluginManager().registerEvents(new BoatProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(this), this);

        getLogger().info("BoatRace has been enabled with " + raceManager.getAllRaces().size() + " race(s).");
    }

    @Override
    public void onDisable() {
        if (raceManager != null) {
            raceManager.save();
        }
        getLogger().info("BoatRace has been disabled.");
    }

    public RaceManager getRaceManager() { return raceManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public DiscordWebhookManager getDiscordWebhookManager() { return discordWebhookManager; }
    public RaceGui getRaceGui() { return raceGui; }
    public Set<UUID> getFrozenPlayers() { return frozenPlayers; }
    public Map<UUID, String> getActiveRaceOf() { return activeRaceOf; }
}
