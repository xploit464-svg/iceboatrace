package com.boatrace.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RaceGui implements Listener {

    private static final String MAIN_TITLE = "BoatRace Menu";
    private static final String JOIN_TITLE = "Select a Race to Join";
    private static final String SPECTATE_TITLE = "Select a Race to Spectate";
    private static final String CYCLE_ITEM_NAME = "Next Racer";
    private static final String LEAVE_ITEM_NAME = "Leave Spectate";

    private final BoatRacePlugin plugin;
    private final RaceManager raceManager;
    // Remembers where a player was standing/their gamemode before they started spectating, so "leave spectate" can restore it
    private final Map<UUID, GameMode> previousGameMode = new HashMap<>();
    // Which race each spectator is currently watching, and which racer index they're focused on
    private final Map<UUID, String> spectatingRaceOf = new HashMap<>();
    private final Map<UUID, Integer> spectateIndex = new HashMap<>();

    public RaceGui(BoatRacePlugin plugin, RaceManager raceManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(MAIN_TITLE));

        setItem(inv, 10, Material.LIME_WOOL, "Join Race");
        setItem(inv, 12, Material.RED_WOOL, "Leave Queue");
        setItem(inv, 14, Material.PAPER, "My Statistics");
        setItem(inv, 15, Material.GOLD_INGOT, "Leaderboards");
        setItem(inv, 16, Material.ENDER_EYE, "Spectate");
        if (player.hasPermission("boatrace.admin")) {
            setItem(inv, 22, Material.COMMAND_BLOCK, "Admin Menu");
        }
        setItem(inv, 26, Material.BOOK, "Help");

        player.openInventory(inv);
    }

    private void openTrackSelector(Player player, String title) {
        List<Race> races = new ArrayList<>(raceManager.getAllRaces().values());
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));
        int slot = 0;
        for (Race race : races) {
            if (!race.isEnabled() && title.equals(JOIN_TITLE)) continue;
            if (slot >= 54) break;
            ItemStack item = new ItemStack(Material.OAK_BOAT);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(race.getDisplayName()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(race.getQueueSize() + "/" + race.getMaxPlayers() + " queued"));
            lore.add(Component.text("Status: " + race.getStatus()));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    private void setItem(Inventory inv, int slot, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = plainTitle(event);
        if (title == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        String itemName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName() != null
                ? clicked.getItemMeta().displayName() : Component.text(""));

        switch (title) {
            case MAIN_TITLE -> {
                event.setCancelled(true);
                handleMainMenuClick(player, itemName);
            }
            case JOIN_TITLE -> {
                event.setCancelled(true);
                Race race = raceManager.get(itemName);
                if (race != null) {
                    player.sendMessage(Component.text(race.joinQueue(player)));
                    player.closeInventory();
                }
            }
            case SPECTATE_TITLE -> {
                event.setCancelled(true);
                Race race = raceManager.get(itemName);
                if (race != null) {
                    startSpectating(player, race);
                    player.closeInventory();
                }
            }
        }
    }

    private void handleMainMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "Join Race" -> openTrackSelector(player, JOIN_TITLE);
            case "Leave Queue" -> {
                boolean left = false;
                for (Race race : raceManager.getAllRaces().values()) {
                    String result = race.leaveQueue(player);
                    if (!result.startsWith("You're not")) {
                        player.sendMessage(Component.text(result));
                        left = true;
                    }
                }
                if (!left) player.sendMessage(Component.text("You're not in any queue."));
                player.closeInventory();
            }
            case "My Statistics" -> {
                plugin.getStatsManager().sendStats(player);
                player.closeInventory();
            }
            case "Leaderboards" -> {
                player.sendMessage(Component.text("Use /race leaderboard <track> <wins|fastest> to view one."));
                player.closeInventory();
            }
            case "Spectate" -> openTrackSelector(player, SPECTATE_TITLE);
            case "Admin Menu" -> {
                player.sendMessage(Component.text("Use /boatrace for the full admin command list."));
                player.closeInventory();
            }
            case "Help" -> {
                player.sendMessage(Component.text("Use /race join <name>, /race leave <name>, or open this menu with /race."));
                player.closeInventory();
            }
        }
    }

    public void startSpectating(Player player, Race race) {
        if (!race.isAllowSpectators()) {
            player.sendMessage(Component.text("Spectating is disabled for '" + race.getName() + "'."));
            return;
        }

        // If they were queued anywhere, pull them out first so the queue count updates properly
        for (Race r : raceManager.getAllRaces().values()) {
            r.removeFromQueueSilently(player);
        }

        previousGameMode.put(player.getUniqueId(), player.getGameMode());
        if (race.getSpectatorSpawn() != null) {
            player.teleport(race.getSpectatorSpawn());
        } else if (race.getFinish() != null) {
            player.teleport(race.getFinish());
        } else if (race.getLobby() != null) {
            player.teleport(race.getLobby());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        }, 40L); // 2 seconds - gives your Multiverse world-modify command time to finish first

        spectatingRaceOf.put(player.getUniqueId(), race.getName());
        spectateIndex.put(player.getUniqueId(), -1);
        giveSpectatorItems(player);

        player.sendMessage(Component.text("Now spectating '" + race.getName() + "'. Use the compass to cycle racers, or /race leavespectate to stop."));
    }

    private void giveSpectatorItems(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compass.getItemMeta();
        compassMeta.displayName(Component.text(CYCLE_ITEM_NAME));
        compass.setItemMeta(compassMeta);

        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        barrierMeta.displayName(Component.text(LEAVE_ITEM_NAME));
        barrier.setItemMeta(barrierMeta);

        player.getInventory().setItem(0, compass);
        player.getInventory().setItem(8, barrier);
    }

    // Teleports the spectator to the next active racer in the race they're watching
    public void cycleToNextRacer(Player player) {
        String raceName = spectatingRaceOf.get(player.getUniqueId());
        if (raceName == null) return;
        Race race = raceManager.get(raceName);
        if (race == null) return;

        List<UUID> racers = new ArrayList<>(race.getActiveRacers().keySet());
        if (racers.isEmpty()) {
            player.sendMessage(Component.text("No active racers to cycle to right now."));
            return;
        }

        int index = spectateIndex.getOrDefault(player.getUniqueId(), -1);
        index = (index + 1) % racers.size();
        spectateIndex.put(player.getUniqueId(), index);

        Player target = Bukkit.getPlayer(racers.get(index));
        if (target != null) {
            player.teleport(target.getLocation());
            player.sendMessage(Component.text("Now watching " + target.getName() + "."));
        }
    }

    public void stopSpectating(Player player) {
        GameMode previous = previousGameMode.remove(player.getUniqueId());
        player.setGameMode(previous != null ? previous : GameMode.SURVIVAL);
        player.getInventory().setItem(0, null);
        player.getInventory().setItem(8, null);
        spectatingRaceOf.remove(player.getUniqueId());
        spectateIndex.remove(player.getUniqueId());
        player.sendMessage(Component.text("Stopped spectating."));
        Bukkit.dispatchCommand(player, "mvtp lobby");
    }

    @EventHandler
    public void onSpectatorItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!spectatingRaceOf.containsKey(player.getUniqueId())) return;
        ItemStack item = event.getItem();
        if (item == null || item.getItemMeta() == null || item.getItemMeta().displayName() == null) return;

        String itemName = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        if (itemName.equals(CYCLE_ITEM_NAME)) {
            event.setCancelled(true);
            cycleToNextRacer(player);
        } else if (itemName.equals(LEAVE_ITEM_NAME)) {
            event.setCancelled(true);
            stopSpectating(player);
        }
    }

    private String plainTitle(InventoryClickEvent event) {
        try {
            return PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        } catch (Exception e) {
            return null;
        }
    }
}
