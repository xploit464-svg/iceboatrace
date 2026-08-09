package com.boatrace.plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class DisconnectListener implements Listener {

    private final BoatRacePlugin plugin;

    public DisconnectListener(BoatRacePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String raceName = plugin.getActiveRaceOf().get(player.getUniqueId());
        if (raceName == null) return;

        Race race = plugin.getRaceManager().get(raceName);
        if (race != null) {
            race.markDisconnectedAsDnf(player.getUniqueId());
        }
    }
}
