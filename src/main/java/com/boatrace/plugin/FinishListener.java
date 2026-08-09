package com.boatrace.plugin;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class FinishListener implements Listener {

    private final BoatRacePlugin plugin;

    public FinishListener(BoatRacePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String raceName = plugin.getActiveRaceOf().get(player.getUniqueId());
        if (raceName == null) return;

        Race race = plugin.getRaceManager().get(raceName);
        if (race == null || race.getStatus() != Race.Status.RUNNING) return;

        Location finish = race.getFinish();
        Location to = event.getTo();
        if (finish == null || to == null || finish.getWorld() == null || !finish.getWorld().equals(to.getWorld())) {
            return;
        }

        if (finish.distance(to) <= race.getFinishRadius()) {
            race.onReachFinish(player);
        }
    }
}
