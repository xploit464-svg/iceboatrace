package com.boatrace.plugin;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class Race {

    public static final int MAX_LANES = 500; // effectively unlimited while still guarding against typos/mistakes

    public enum Status { WAITING, STARTING, COUNTDOWN, RUNNING, FINISHED, RESETTING }

    // ---- Config-backed fields (see RaceManager for load/save) ----
    private final String name;
    private boolean enabled = true;
    private String displayName;
    private int minPlayers = 2;
    private int maxPlayers = 12;
    private int countdownSeconds = 5;
    private int laps = 1;
    private int queueTimeoutSeconds = 60; // if min-players is met but not max, start anyway after this long
    private Location lobby;
    private Location waitingRoom;
    private Location finish;
    private Location spectatorSpawn;
    private double finishRadius = 3.0;
    private final Map<Integer, Location> lanes = new TreeMap<>();
    private final List<Location> checkpoints = new ArrayList<>();
    private EntityType boatType = EntityType.OAK_BOAT;
    private boolean allowSpectators = true;
    private final List<String> rewardCommands = new ArrayList<>();
    private final Map<Integer, Integer> rewardXp = new LinkedHashMap<>(); // place -> XP points, native, no plugins needed
    private String description = "";
    private String difficulty = "Normal";
    private String author = "";
    private String weather = null; // "clear", "rain", "thunder", or null = don't change
    private Long timeOfDay = null; // 0-24000 ticks, or null = don't change

    // ---- Runtime state (not saved) ----
    private Status status = Status.WAITING;
    private final List<Player> queue = new ArrayList<>();
    private final Map<UUID, RacerState> activeRacers = new LinkedHashMap<>();
    private final List<String> lastResults = new ArrayList<>();
    private BukkitTask countdownTask;
    private BukkitTask positionTask;
    private BukkitTask queueTimeoutTask;
    private final BossBar queueBossBar = BossBar.bossBar(Component.text("Waiting for players..."), 0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    private final BossBar countdownBossBar = BossBar.bossBar(Component.text("Starting..."), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    private final BoatRacePlugin plugin;

    public Race(BoatRacePlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.displayName = name;
    }

    // Tracks a single racer's progress once a race is running
    public static class RacerState {
        long startTimeMillis;
        int lastCheckpoint = 0; // 0 = hasn't hit checkpoint 1 yet, resets each lap
        int currentLap = 1;
        long lastLapAdvanceMillis = 0;
        boolean finished = false;
        boolean dnf = false;
        int lane;
        Location lastCheckpointLocation; // where to respawn if they die mid-race
        UUID boatEntityId;
    }

    // ================= Getters / setters for config fields =================

    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public int getLaps() { return laps; }
    public int getQueueTimeoutSeconds() { return queueTimeoutSeconds; }
    public Location getLobby() { return lobby; }
    public Location getWaitingRoom() { return waitingRoom; }
    public Location getFinish() { return finish; }
    public Location getSpectatorSpawn() { return spectatorSpawn; }
    public double getFinishRadius() { return finishRadius; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getWeather() { return weather; }
    public Long getTimeOfDay() { return timeOfDay; }

    public boolean setWeather(String value) {
        if (value == null) { weather = null; return true; }
        String v = value.toLowerCase();
        if (!v.equals("clear") && !v.equals("rain") && !v.equals("thunder")) return false;
        weather = v;
        return true;
    }

    public boolean setTimeOfDay(Long ticks) {
        if (ticks == null) { timeOfDay = null; return true; }
        if (ticks < 0 || ticks > 24000) return false;
        timeOfDay = ticks;
        return true;
    }
    public EntityType getBoatType() { return boatType; }
    public boolean isAllowSpectators() { return allowSpectators; }
    public void setAllowSpectators(boolean allow) { this.allowSpectators = allow; }
    public List<String> getRewardCommands() { return rewardCommands; }
    public Map<Integer, Integer> getRewardXp() { return rewardXp; }
    public void setRewardXp(int place, int xp) { rewardXp.put(place, xp); }
    public Status getStatus() { return status; }
    public int getQueueSize() { return queue.size(); }
    public int getLaneCount() { return lanes.size(); }
    public int getCheckpointCount() { return checkpoints.size(); }
    public Map<Integer, Location> getAllLanes() { return new TreeMap<>(lanes); }
    public List<Location> getCheckpoints() { return checkpoints; }
    public Map<UUID, RacerState> getActiveRacers() { return activeRacers; }

    public void setLobby(Location loc) { this.lobby = loc; }
    public void setWaitingRoom(Location loc) { this.waitingRoom = loc; }
    public void setFinish(Location loc) { this.finish = loc; }
    public void setSpectatorSpawn(Location loc) { this.spectatorSpawn = loc; }

    public boolean setMaxPlayers(int n) {
        if (n < 1 || n > MAX_LANES) return false;
        maxPlayers = n;
        return true;
    }

    public boolean setMinPlayers(int n) {
        if (n < 1 || n > MAX_LANES) return false;
        minPlayers = n;
        return true;
    }

    public boolean setCountdown(int seconds) {
        if (seconds < 1 || seconds > 60) return false;
        countdownSeconds = seconds;
        return true;
    }

    public boolean setLaps(int n) {
        if (n < 1 || n > 100) return false;
        laps = n;
        return true;
    }

    public boolean setQueueTimeout(int seconds) {
        if (seconds < 5 || seconds > 3600) return false;
        queueTimeoutSeconds = seconds;
        return true;
    }

    public boolean setBoatType(EntityType type) {
        if (type == null || !(type.name().endsWith("_BOAT") || type == EntityType.BAMBOO_RAFT)) {
            return false;
        }
        boatType = type;
        return true;
    }

    public boolean setLane(int number, Location loc) {
        if (number < 1 || number > MAX_LANES) return false;
        lanes.put(number, loc.clone());
        return true;
    }

    public boolean removeLane(int number) {
        return lanes.remove(number) != null;
    }

    public void addCheckpoint(Location loc) {
        checkpoints.add(loc.clone());
    }

    public boolean removeCheckpoint(int id) {
        if (id < 1 || id > checkpoints.size()) return false;
        checkpoints.remove(id - 1);
        return true;
    }

    // ================= Validation =================

    public List<String> validate() {
        List<String> results = new ArrayList<>();

        // Required locations
        results.add((lobby != null ? "OK" : "FAIL") + "|Lobby set");
        results.add((waitingRoom != null ? "OK" : "FAIL") + "|Waiting room set");
        results.add((finish != null ? "OK" : "FAIL") + "|Finish set");
        results.add((spectatorSpawn != null ? "OK" : "INFO") + "|Spectator spawn " + (spectatorSpawn != null ? "set" : "not set (will fall back to finish/lobby)"));

        // World existence for every location actually set
        boolean worldOk = (lobby == null || lobby.getWorld() != null)
                && (waitingRoom == null || waitingRoom.getWorld() != null)
                && (finish == null || finish.getWorld() != null)
                && (spectatorSpawn == null || spectatorSpawn.getWorld() != null);
        results.add((worldOk ? "OK" : "FAIL") + "|World exists for all set locations");

        // Lanes
        results.add((lanes.size() >= 2 ? "OK" : "FAIL") + "|" + lanes.size() + " lane(s) configured (need at least 2)");
        boolean laneWorldsOk = lanes.values().stream().allMatch(loc -> loc.getWorld() != null);
        results.add((laneWorldsOk ? "OK" : "FAIL") + "|All lane worlds exist");
        long occupiedCount = lanes.isEmpty() ? 0 : checkLaneOccupancy().values().stream().filter(Boolean::booleanValue).count();
        results.add((occupiedCount == 0 ? "OK" : "INFO") + "|" + occupiedCount + " lane(s) currently occupied right now");

        // Checkpoints (optional, informational)
        results.add("INFO|" + checkpoints.size() + " checkpoint(s) configured" + (checkpoints.isEmpty() ? " (none - finish line only)" : ""));

        // Player counts
        results.add((minPlayers >= 1 ? "OK" : "FAIL") + "|Min players is " + minPlayers);
        results.add((maxPlayers >= minPlayers ? "OK" : "FAIL") + "|Max players (" + maxPlayers + ") is at least min players");
        results.add((maxPlayers <= lanes.size() || lanes.isEmpty() ? "OK" : "FAIL") + "|Max players (" + maxPlayers + ") does not exceed lane count (" + lanes.size() + ")");

        // Timing
        results.add((countdownSeconds >= 1 ? "OK" : "FAIL") + "|Countdown is " + countdownSeconds + "s");
        results.add("INFO|Queue timeout is " + queueTimeoutSeconds + "s");
        results.add("INFO|Laps set to " + laps);

        // Boat / rewards / extras
        results.add("INFO|Boat type: " + boatType);
        results.add("INFO|" + rewardCommands.size() + " reward command(s), " + rewardXp.size() + " XP reward tier(s) configured");
        results.add("INFO|Weather: " + (weather == null ? "unchanged" : weather) + "   Time: " + (timeOfDay == null ? "unchanged" : timeOfDay));

        return results;
    }

    public boolean isReadyToEnable() {
        return lobby != null && waitingRoom != null && finish != null
                && lanes.size() >= 2 && maxPlayers >= minPlayers && maxPlayers <= lanes.size()
                && countdownSeconds >= 1;
    }

    // ================= Queue handling =================

    public String joinQueue(Player player) {
        if (!enabled) {
            return "Race '" + name + "' is currently disabled.";
        }
        if (status != Status.WAITING) {
            return "Race '" + name + "' is already starting/running. Try again shortly.";
        }
        if (queue.contains(player)) {
            return "You're already in the '" + name + "' queue.";
        }
        if (lanes.isEmpty()) {
            return "Race '" + name + "' has no lanes set up yet.";
        }

        queue.add(player);
        if (waitingRoom != null) {
            player.teleport(waitingRoom);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && queue.contains(player)) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }, 40L); // 2 seconds - gives your Multiverse world-modify command time to finish first
        player.showBossBar(queueBossBar);
        broadcastToQueue(player.getName() + " joined " + displayName + " (" + queue.size() + "/" + maxPlayers + ")");
        updateQueueBossBar();

        if (queue.size() >= maxPlayers) {
            broadcastToQueue("Queue is full! Starting now...");
            cancelQueueTimeout();
            startRace(false);
        } else if (queueTimeoutTask == null) {
            broadcastToQueue("Race starts in " + queueTimeoutSeconds + "s if the queue doesn't fill up sooner.");
            queueTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                queueTimeoutTask = null;
                if (status == Status.WAITING && !queue.isEmpty()) {
                    startRace(true);
                }
            }, queueTimeoutSeconds * 20L);
        }

        return "Joined the queue for '" + name + "' (" + queue.size() + "/" + maxPlayers + ")";
    }

    private void updateQueueBossBar() {
        float progress = maxPlayers <= 0 ? 0f : Math.max(0f, Math.min(1f, queue.size() / (float) maxPlayers));
        queueBossBar.name(Component.text(displayName + ": " + queue.size() + "/" + maxPlayers + " players"));
        queueBossBar.progress(progress);
        for (Player p : queue) {
            p.sendActionBar(Component.text("Queued for " + displayName + ": " + queue.size() + "/" + maxPlayers));
        }
    }

    public String leaveQueue(Player player) {
        if (!queue.contains(player)) {
            return "You're not queued for '" + name + "'.";
        }
        queue.remove(player);
        player.hideBossBar(queueBossBar);
        Bukkit.dispatchCommand(player, "mvtp lobby");
        broadcastToQueue(player.getName() + " left " + displayName + " (" + queue.size() + "/" + maxPlayers + ")");
        updateQueueBossBar();
        if (queue.isEmpty()) {
            cancelQueueTimeout();
        }
        return "You left the queue for '" + name + "'.";
    }

    private void cancelQueueTimeout() {
        if (queueTimeoutTask != null) {
            queueTimeoutTask.cancel();
            queueTimeoutTask = null;
        }
    }

    // Removes a player from the queue without teleporting them - used when they switch to spectating instead
    public boolean removeFromQueueSilently(Player player) {
        if (!queue.contains(player)) {
            return false;
        }
        queue.remove(player);
        player.hideBossBar(queueBossBar);
        if (queue.isEmpty()) {
            cancelQueueTimeout();
        }
        broadcastToQueue(player.getName() + " left " + displayName + " (" + queue.size() + "/" + maxPlayers + ")");
        updateQueueBossBar();
        return true;
    }

    private void broadcastToQueue(String message) {
        for (Player p : queue) {
            p.sendMessage(Component.text("[" + displayName + "] " + message));
        }
    }

    public void resetQueue() {
        queue.clear();
        cancelTasks();
        for (UUID uuid : new ArrayList<>(activeRacers.keySet())) {
            plugin.getFrozenPlayers().remove(uuid);
            plugin.getActiveRaceOf().remove(uuid);
        }
        activeRacers.clear();
        status = Status.WAITING;
    }

    public String forceStart() {
        if (status != Status.WAITING) {
            return "Race '" + name + "' is already starting/running.";
        }
        if (queue.isEmpty()) {
            return "The queue for '" + name + "' is empty.";
        }
        if (lanes.isEmpty()) {
            return "Race '" + name + "' has no lanes set up yet.";
        }
        int count = Math.min(queue.size(), lanes.size());
        cancelQueueTimeout();
        startRace(true);
        return "Race '" + name + "' force-started with " + count + " player(s), teleported to their lanes.";
    }

    public String forceStop() {
        if (status == Status.WAITING) {
            return "Race '" + name + "' isn't running.";
        }
        for (UUID uuid : activeRacers.keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                p.sendMessage(Component.text("[" + displayName + "] The race was force-stopped."));
                removeBoat(p);
                Bukkit.dispatchCommand(p, "mvtp lobby");
            }
        }
        resetQueue();
        return "Race '" + name + "' force-stopped.";
    }

    // ================= Race lifecycle =================

    private void startRace(boolean bypassMinPlayers) {
        if (!bypassMinPlayers && queue.size() < minPlayers) {
            return; // not enough players yet, just wait for more (or an admin forcestart)
        }

        status = Status.STARTING;

        List<Player> racers = new ArrayList<>(queue);
        queue.clear();

        List<Location> orderedLanes = new ArrayList<>(lanes.values());
        int laneCount = Math.min(racers.size(), orderedLanes.size());

        applyWeatherAndTime(orderedLanes);

        for (int i = 0; i < laneCount; i++) {
            Player p = racers.get(i);
            Location laneSpot = orderedLanes.get(i);
            if (isLocationOccupied(laneSpot, p)) {
                p.sendMessage(Component.text("[" + displayName + "] Heads up - your lane looked occupied when you were placed."));
            }
            p.teleport(laneSpot);
            plugin.getFrozenPlayers().add(p.getUniqueId());
            p.hideBossBar(queueBossBar);
            p.showBossBar(countdownBossBar);

            RacerState state = new RacerState();
            state.lane = i + 1;
            activeRacers.put(p.getUniqueId(), state);
            plugin.getActiveRaceOf().put(p.getUniqueId(), name);

            giveBoat(p, state);
            p.sendMessage(Component.text("[" + displayName + "] You've been placed in lane " + (i + 1) + "!"));
        }

        if (racers.size() > orderedLanes.size()) {
            for (int i = orderedLanes.size(); i < racers.size(); i++) {
                racers.get(i).sendMessage(Component.text("[" + displayName + "] Not enough lanes configured, you couldn't be placed."));
                if (lobby != null) Bukkit.dispatchCommand(racers.get(i), "mvtp lobby");
            }
        }

        status = Status.COUNTDOWN;
        runCountdown(countdownSeconds);
    }

    // Applies this race's configured weather/time to its arena world, if set.
    // Note: since the world is shared, this will affect everyone/everything else in that world too -
    // only use this if the race has its own dedicated world.
    private void applyWeatherAndTime(List<Location> orderedLanes) {
        if ((weather == null && timeOfDay == null) || orderedLanes.isEmpty()) return;
        org.bukkit.World world = orderedLanes.get(0).getWorld();
        if (world == null) return;

        if (weather != null) {
            switch (weather) {
                case "clear" -> { world.setStorm(false); world.setThundering(false); }
                case "rain" -> { world.setStorm(true); world.setThundering(false); }
                case "thunder" -> { world.setStorm(true); world.setThundering(true); }
            }
        }
        if (timeOfDay != null) {
            world.setTime(timeOfDay);
        }
    }

    // Checks whether another player or boat is already sitting on a lane spot
    private boolean isLocationOccupied(Location loc, Player excluding) {
        if (loc.getWorld() == null) return false;
        return loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5).stream()
                .anyMatch(e -> (e instanceof Player p && !p.equals(excluding)) || e instanceof Boat);
    }

    // Lets an admin check right now whether any lane spots currently have something sitting on them
    public Map<Integer, Boolean> checkLaneOccupancy() {
        Map<Integer, Boolean> result = new TreeMap<>();
        for (Map.Entry<Integer, Location> entry : lanes.entrySet()) {
            result.put(entry.getKey(), isLocationOccupied(entry.getValue(), null));
        }
        return result;
    }

    private void giveBoat(Player p, RacerState state) {
        Location loc = p.getLocation();
        org.bukkit.entity.Entity entity = loc.getWorld().spawnEntity(loc, boatType);
        if (entity instanceof Boat boat) {
            boat.addPassenger(p);
            state.boatEntityId = boat.getUniqueId();
        }
    }

    // Returns the UUID of the racer this boat belongs to, or null if it's not a race boat (or race has ended)
    public UUID getRacerForBoat(UUID boatEntityId) {
        for (Map.Entry<UUID, RacerState> entry : activeRacers.entrySet()) {
            if (boatEntityId.equals(entry.getValue().boatEntityId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Gives a fresh boat to a racer whose boat was destroyed mid-race
    public void respawnBoatFor(UUID racerUuid) {
        RacerState state = activeRacers.get(racerUuid);
        Player p = plugin.getServer().getPlayer(racerUuid);
        if (state == null || p == null || state.finished || state.dnf) return;
        giveBoat(p, state);
        p.sendMessage(Component.text("[" + displayName + "] Your boat was destroyed - a new one has been given to you."));
    }

    private void removeBoat(Player p) {
        if (p.getVehicle() instanceof Boat boat) {
            boat.eject();
            boat.remove();
        }
    }

    private void runCountdown(int secondsLeft) {
        if (secondsLeft <= 0) {
            status = Status.RUNNING;
            long now = System.currentTimeMillis();
            countdownBossBar.name(Component.text("GO!"));
            countdownBossBar.progress(1f);
            for (UUID uuid : activeRacers.keySet()) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) continue;
                activeRacers.get(uuid).startTimeMillis = now;
                plugin.getFrozenPlayers().remove(uuid);
                showTitle(p, "GO!", "", 0, 1000, 300);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                p.hideBossBar(countdownBossBar);
            }
            startPositionUpdates();
            return;
        }

        countdownBossBar.name(Component.text(displayName + " starts in " + secondsLeft + "..."));
        countdownBossBar.progress(Math.max(0f, Math.min(1f, secondsLeft / (float) Math.max(1, countdownSeconds))));

        for (UUID uuid : activeRacers.keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            showTitle(p, String.valueOf(secondsLeft), "Get ready!", 0, 900, 100);
            p.sendActionBar(Component.text("Race starts in " + secondsLeft + "..."));
            p.sendMessage(Component.text("[" + displayName + "] " + secondsLeft + "..."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        }

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                runCountdown(secondsLeft - 1);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void showTitle(Player p, String title, String subtitle, long fadeIn, long stay, long fadeOut) {
        p.showTitle(Title.title(
                Component.text(title),
                Component.text(subtitle),
                Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut))
        ));
    }

    // Sends each active racer their current position (e.g. "2/8") every half-second, based on lap + checkpoint progress
    private void startPositionUpdates() {
        positionTask = new BukkitRunnable() {
            @Override
            public void run() {
                updatePositions();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void updatePositions() {
        List<Map.Entry<UUID, RacerState>> ranked = activeRacers.entrySet().stream()
                .filter(e -> !e.getValue().finished && !e.getValue().dnf)
                .sorted((a, b) -> {
                    int scoreA = (a.getValue().currentLap - 1) * Math.max(1, checkpoints.size()) + a.getValue().lastCheckpoint;
                    int scoreB = (b.getValue().currentLap - 1) * Math.max(1, checkpoints.size()) + b.getValue().lastCheckpoint;
                    return scoreB - scoreA;
                })
                .toList();

        for (int i = 0; i < ranked.size(); i++) {
            Player p = plugin.getServer().getPlayer(ranked.get(i).getKey());
            if (p != null) {
                p.sendActionBar(Component.text("Position: " + (i + 1) + "/" + maxPlayers));
            }
        }
    }

    // Called by CheckpointListener when a racer passes a checkpoint in order
    public boolean tryAdvanceCheckpoint(Player p, int checkpointId) {
        RacerState state = activeRacers.get(p.getUniqueId());
        if (state == null || state.finished || state.dnf) return false;
        if (checkpointId == state.lastCheckpoint + 1) {
            state.lastCheckpoint = checkpointId;
            state.lastCheckpointLocation = checkpoints.get(checkpointId - 1).clone();
            return true;
        }
        return false; // out of order - ignored (anti checkpoint skipping)
    }

    // Where a racer should respawn if they die mid-race - their last checkpoint, or their lane start if none yet
    public Location getRespawnLocationFor(UUID uuid) {
        RacerState state = activeRacers.get(uuid);
        if (state == null) return null;
        if (state.lastCheckpointLocation != null) return state.lastCheckpointLocation;
        List<Location> orderedLanes = new ArrayList<>(lanes.values());
        int idx = state.lane - 1;
        return idx >= 0 && idx < orderedLanes.size() ? orderedLanes.get(idx) : null;
    }

    // Called when a racer disconnects mid-race - since there's no time limit, this is what
    // prevents a race from waiting forever for someone who left
    public void markDisconnectedAsDnf(UUID uuid) {
        RacerState state = activeRacers.get(uuid);
        if (state != null && !state.finished && !state.dnf) {
            state.dnf = true;
            plugin.getStatsManager().recordDnf(uuid);
        }
        plugin.getFrozenPlayers().remove(uuid);
        plugin.getActiveRaceOf().remove(uuid);
        checkForRaceEnd();
    }

    // Called by FinishListener when a racer reaches the finish location
    public void onReachFinish(Player p) {
        RacerState state = activeRacers.get(p.getUniqueId());
        if (state == null || state.finished || state.dnf) return;
        if (state.lastCheckpoint < checkpoints.size()) {
            return; // hasn't passed all checkpoints this lap, don't count it
        }

        long now = System.currentTimeMillis();
        if (now - state.lastLapAdvanceMillis < 3000) {
            return; // debounce - avoids re-triggering every tick while still standing at the finish line
        }
        state.lastLapAdvanceMillis = now;

        if (state.currentLap < laps) {
            int completedLap = state.currentLap;
            state.currentLap++;
            state.lastCheckpoint = 0;
            state.lastCheckpointLocation = null;
            p.sendMessage(Component.text("[" + displayName + "] Lap " + completedLap + "/" + laps + " complete!"));
            showTitle(p, "Lap " + completedLap + "/" + laps, "Keep going!", 0, 700, 200);
            return;
        }

        finalizeFinish(p, state);
    }

    private void finalizeFinish(Player p, RacerState state) {
        state.finished = true;
        long timeMillis = System.currentTimeMillis() - state.startTimeMillis;
        int place = (int) activeRacers.values().stream().filter(s -> s.finished).count();

        plugin.getStatsManager().recordFinish(p.getUniqueId(), name, timeMillis, place);

        String timeStr = formatTime(timeMillis);
        lastResults.add(place + ". " + p.getName() + " - " + timeStr);

        String placeText = place == 1 ? "1st" : place == 2 ? "2nd" : place == 3 ? "3rd" : place + "th";
        if (place == 1) {
            showTitle(p, "YOU WON!", "Time: " + timeStr, 0, 1500, 500);
        } else {
            showTitle(p, "Finished " + placeText, "Time: " + timeStr, 0, 1200, 500);
        }
        p.sendMessage(Component.text("[" + displayName + "] You finished in " + placeText + " place! Time: " + timeStr));

        for (UUID uuid : activeRacers.keySet()) {
            if (uuid.equals(p.getUniqueId())) continue;
            Player other = plugin.getServer().getPlayer(uuid);
            if (other != null) {
                other.sendMessage(Component.text("[" + displayName + "] " + p.getName() + " finished in place " + place + " (" + timeStr + ")"));
            }
        }

        runRewardCommands(p, place, timeStr);
        if (place == 1) {
            plugin.getDiscordWebhookManager().announce(p.getName() + " won the **" + displayName + "** race in " + timeStr + "!");
        }
        removeBoat(p);
        Bukkit.dispatchCommand(p, "mvtp lobby");

        checkForRaceEnd();
    }

    private void runRewardCommands(Player p, int place, String timeStr) {
        Integer xp = rewardXp.get(place);
        if (xp != null && xp > 0) {
            p.giveExp(xp); // native Minecraft XP, no plugins required
            p.sendMessage(Component.text("[" + displayName + "] +" + xp + " XP for finishing " + ordinal(place) + "!"));
        }
        for (String cmd : rewardCommands) {
            String parsed = cmd.replace("%player%", p.getName())
                    .replace("%position%", String.valueOf(place))
                    .replace("%time%", timeStr);
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
        }
    }

    private String ordinal(int n) {
        if (n == 1) return "1st";
        if (n == 2) return "2nd";
        if (n == 3) return "3rd";
        return n + "th";
    }

    private void checkForRaceEnd() {
        boolean allDone = activeRacers.values().stream().allMatch(s -> s.finished || s.dnf);
        if (!allDone) return;

        status = Status.FINISHED;
        for (Map.Entry<UUID, RacerState> entry : activeRacers.entrySet()) {
            if (entry.getValue().dnf) {
                plugin.getStatsManager().recordDnf(entry.getKey());
            }
        }

        status = Status.RESETTING;
        cancelTasks();
        for (UUID uuid : activeRacers.keySet()) {
            plugin.getActiveRaceOf().remove(uuid);
        }
        activeRacers.clear();
        status = Status.WAITING;
        // Auto restart next race: if players already queued (they can queue during a race), check again
        if (queue.size() >= minPlayers && queue.size() >= maxPlayers) {
            startRace(false);
        }
    }

    private void cancelTasks() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (positionTask != null) { positionTask.cancel(); positionTask = null; }
        cancelQueueTimeout();
    }

    public static String formatTime(long millis) {
        long mins = millis / 60000;
        long secs = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format("%d:%02d.%03d", mins, secs, ms);
    }

    public void showCheckpointFeedback(Player p, int checkpointId) {
        Location loc = p.getLocation();
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 20, 0.5, 0.5, 0.5);
        }
        p.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
        showTitle(p, "Checkpoint " + checkpointId, "", 0, 500, 200);
    }

    public List<String> getLastResults() {
        return lastResults;
    }
}
