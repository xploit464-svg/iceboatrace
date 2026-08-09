package com.boatrace.plugin;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BoatProtectionListener implements Listener {

    private final BoatRacePlugin plugin;

    public BoatProtectionListener(BoatRacePlugin plugin) {
        this.plugin = plugin;
    }

    private Race findRaceOwning(UUID boatId, UUID[] outRacerUuid) {
        for (Race race : plugin.getRaceManager().getAllRaces().values()) {
            UUID racer = race.getRacerForBoat(boatId);
            if (racer != null) {
                outRacerUuid[0] = racer;
                return race;
            }
        }
        return null;
    }

    // Nobody but the assigned racer can hop into a race boat
    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        if (!(event.getEntered() instanceof Player player)) return;

        UUID[] holder = new UUID[1];
        Race race = findRaceOwning(boat.getUniqueId(), holder);
        if (race != null && !holder[0].equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Other players can't damage/destroy someone else's race boat
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Boat boat)) return;
        if (!(event.getDamager() instanceof Player damager)) return;

        UUID[] holder = new UUID[1];
        Race race = findRaceOwning(boat.getUniqueId(), holder);
        if (race != null && !holder[0].equals(damager.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // If a race boat is destroyed some other way (fall damage, lava, etc.), give the racer a fresh one
    @EventHandler
    public void onDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;

        UUID[] holder = new UUID[1];
        Race race = findRaceOwning(boat.getUniqueId(), holder);
        if (race == null) return;

        UUID racerUuid = holder[0];
        new BukkitRunnable() {
            @Override
            public void run() {
                race.respawnBoatFor(racerUuid);
            }
        }.runTaskLater(plugin, 5L);
    }
}
