package com.boatrace.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class JoinSignListener implements Listener {

    private final RaceManager raceManager;

    public JoinSignListener(RaceManager raceManager) {
        this.raceManager = raceManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) return;

        String firstLine = plain(sign, 0);
        if (!firstLine.equalsIgnoreCase("[BoatRace]")) return;

        String raceName = plain(sign, 1);
        if (raceName.isBlank()) return;

        Race race = raceManager.get(raceName);
        if (race == null) {
            event.getPlayer().sendMessage(Component.text("This join sign points to a race that no longer exists."));
            return;
        }

        event.getPlayer().sendMessage(Component.text(race.joinQueue(event.getPlayer())));
    }

    private String plain(Sign sign, int lineIndex) {
        return PlainTextComponentSerializer.plainText().serialize(sign.line(lineIndex));
    }
}
