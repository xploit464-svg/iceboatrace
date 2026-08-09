package com.boatrace.plugin;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

public class CheckpointListener implements Listener {

    private static final double TRIGGER_RADIUS = 2.5;

    private final BoatRacePlugin plugin;

    public CheckpointListener(BoatRacePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String raceName = plugin.getActiveRaceOf().get(player.getUniqueId());
        if (raceName == null) return;

        Race race = plugin.getRaceManager().get(raceName);
        if (race == null || race.getStatus() != Race.Status.RUNNING) return;

        Location loc = event.getTo();
        if (loc == null) return;

        List<Location> checkpoints = race.getCheckpoints();
        for (int i = 0; i < checkpoints.size(); i++) {
            Location cp = checkpoints.get(i);
            if (cp.getWorld() != null && cp.getWorld().equals(loc.getWorld()) && cp.distance(loc) <= TRIGGER_RADIUS) {
                int checkpointId = i + 1;
                if (race.tryAdvanceCheckpoint(player, checkpointId)) {
                    race.showCheckpointFeedback(player, checkpointId);
                }
                break;
            }
        }
    }
}
