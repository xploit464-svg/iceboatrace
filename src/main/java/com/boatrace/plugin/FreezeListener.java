package com.boatrace.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreezeListener implements Listener {

    private final BoatRacePlugin plugin;
    // Simple per-player cooldown so we don't spam the false-start message every tick
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public FreezeListener(BoatRacePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getFrozenPlayers().contains(player.getUniqueId())) {
            return;
        }

        boolean actuallyMoved = event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();

        if (actuallyMoved) {
            event.setTo(event.getFrom().setDirection(event.getTo().getDirection()));
            warnFalseStart(player);
        }
    }

    // Stops a frozen player's boat from drifting/being steered before GO
    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        if (boat.getPassengers().isEmpty()) return;

        boolean anyFrozen = boat.getPassengers().stream()
                .anyMatch(p -> plugin.getFrozenPlayers().contains(p.getUniqueId()));
        if (!anyFrozen) return;

        boolean actuallyMoved = event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();

        if (actuallyMoved) {
            boat.teleport(event.getFrom());
            boat.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            for (var p : boat.getPassengers()) {
                if (p instanceof Player player && plugin.getFrozenPlayers().contains(player.getUniqueId())) {
                    warnFalseStart(player);
                }
            }
        }
    }

    private void warnFalseStart(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last > 2000) {
            player.sendMessage(Component.text("False start! Wait for GO before moving."));
            lastWarning.put(player.getUniqueId(), now);
        }
    }
}
