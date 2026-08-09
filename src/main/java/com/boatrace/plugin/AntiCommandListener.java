package com.boatrace.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.Set;

public class AntiCommandListener implements Listener {

    // Commands allowed even mid-race (without the leading slash)
    private static final Set<String> ALLOWED = Set.of("race", "msg", "tell", "r", "help");

    private final BoatRacePlugin plugin;

    public AntiCommandListener(BoatRacePlugin plugin) {
        this.plugin = plugin;
    }

    // True while a player is frozen/counting down/actually racing - covers the whole active period
    private boolean isCurrentlyActive(Player player) {
        String raceName = plugin.getActiveRaceOf().get(player.getUniqueId());
        if (raceName == null) return false;
        Race race = plugin.getRaceManager().get(raceName);
        if (race == null) return false;
        return race.getStatus() == Race.Status.RUNNING
                || race.getStatus() == Race.Status.STARTING
                || race.getStatus() == Race.Status.COUNTDOWN;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("boatrace.admin")) {
            return; // admins can always run commands
        }
        if (!isCurrentlyActive(player)) {
            return;
        }

        String rawCommand = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (!ALLOWED.contains(rawCommand)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Commands are disabled while a race is running."));
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (isCurrentlyActive(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isCurrentlyActive(player)) {
            event.setCancelled(true);
        }
    }
}
