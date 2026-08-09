package com.boatrace.plugin;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RespawnListener implements Listener {

    private final BoatRacePlugin plugin;

    public RespawnListener(BoatRacePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        String raceName = plugin.getActiveRaceOf().get(event.getPlayer().getUniqueId());
        if (raceName == null) return;

        Race race = plugin.getRaceManager().get(raceName);
        if (race == null) return;

        Location respawnLoc = race.getRespawnLocationFor(event.getPlayer().getUniqueId());
        if (respawnLoc != null) {
            event.setRespawnLocation(respawnLoc);
        }
    }
}
