package com.boatrace.plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DiscordWebhookManager {

    private final BoatRacePlugin plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DiscordWebhookManager(BoatRacePlugin plugin) {
        this.plugin = plugin;
    }

    // Sends a plain message to the webhook URL set in config.yml under discord-webhook-url.
    // If it's blank, this quietly does nothing - Discord announcements are fully optional.
    public void announce(String message) {
        String url = plugin.getConfig().getString("discord-webhook-url", "");
        if (url == null || url.isBlank()) {
            return;
        }

        String json = "{\"content\":\"" + message.replace("\"", "\\\"") + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Discord webhook failed: " + ex.getMessage());
                    return null;
                });
    }
}
