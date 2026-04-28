package com.huntergame.world;

import com.huntergame.HunterGame;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class OreMultiplier implements Listener {

    private static final int DEFAULT_PLAYER_RADIUS_CHUNKS = 1;
    private static final int DEFAULT_MAX_CHUNKS_PER_TICK = 1;
    private static final int DEFAULT_VEINS_PER_CHUNK = 18;
    private static final double DEFAULT_VISIBLE_VEIN_CHANCE = 0.35;
    private static final int CENTER_ATTEMPTS = 12;
    private static final int PLAYER_CHUNK_SCAN_INTERVAL_TICKS = 100;

    private static final Set<Material> NATURAL_STONES = Set.of(
            Material.STONE, Material.DEEPSLATE,
            Material.ANDESITE, Material.GRANITE, Material.DIORITE,
            Material.TUFF, Material.CALCITE, Material.DRIPSTONE_BLOCK
    );
    private static final Set<Material> AIR_LIKE_MATERIALS = Set.of(
            Material.AIR, Material.CAVE_AIR, Material.WATER, Material.LAVA
    );

    private final HunterGame plugin;
    private final Random random = new Random();
    private final Map<UUID, Set<Long>> generatedChunksByWorld = new HashMap<>();
    private final Queue<ChunkRequest> pendingChunks = new ArrayDeque<>();
    private final Set<ChunkRequest> queuedChunks = new HashSet<>();
    private final int playerRadiusChunks;
    private final int maxChunksPerTick;
    private final int veinsPerChunk;
    private final double visibleVeinChance;
    private int playerChunkScanTicks = 0;
    private BukkitTask workerTask;

    public OreMultiplier(HunterGame plugin) {
        this.plugin = plugin;
        this.playerRadiusChunks = plugin.getConfig().getInt("ore_multiplier.player_radius_chunks", DEFAULT_PLAYER_RADIUS_CHUNKS);
        this.maxChunksPerTick = Math.max(1, plugin.getConfig().getInt("ore_multiplier.max_chunks_per_tick", DEFAULT_MAX_CHUNKS_PER_TICK));
        this.veinsPerChunk = Math.max(1, plugin.getConfig().getInt("ore_multiplier.veins_per_chunk", DEFAULT_VEINS_PER_CHUNK));
        this.visibleVeinChance = clamp(plugin.getConfig().getDouble("ore_multiplier.visible_vein_chance", DEFAULT_VISIBLE_VEIN_CHANCE));

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startWorker();
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        if (!shouldGenerateBonusOres()) {
            return;
        }
        queueChunk(event.getChunk());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !shouldGenerateBonusOres() || isSameChunk(event.getFrom(), to)) {
            return;
        }

        World world = to.getWorld();
        if (world == null) {
            return;
        }

        int currentChunkX = to.getBlockX() >> 4;
        int currentChunkZ = to.getBlockZ() >> 4;
        queueLoadedChunksAround(world, currentChunkX, currentChunkZ);
    }

    private void startWorker() {
        workerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            scanPlayerChunksPeriodically();

            int processed = 0;
            while (processed < maxChunksPerTick && !pendingChunks.isEmpty()) {
                ChunkRequest request = pendingChunks.poll();
                queuedChunks.remove(request);
                if (request.world.isChunkLoaded(request.chunkX, request.chunkZ)) {
                    generateOreVeins(request.world, request.chunkX, request.chunkZ);
                    processed++;
                }
            }
        }, 1L, 1L);
    }

    private void scanPlayerChunksPeriodically() {
        if (++playerChunkScanTicks < PLAYER_CHUNK_SCAN_INTERVAL_TICKS) {
            return;
        }
        playerChunkScanTicks = 0;
        if (!shouldGenerateBonusOres()) {
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world != null) {
                queueLoadedChunksAround(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
            }
        }
    }

    private void queueLoadedChunksAround(World world, int currentChunkX, int currentChunkZ) {
        for (int dx = -playerRadiusChunks; dx <= playerRadiusChunks; dx++) {
            for (int dz = -playerRadiusChunks; dz <= playerRadiusChunks; dz++) {
                int chunkX = currentChunkX + dx;
                int chunkZ = currentChunkZ + dz;
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    queueChunk(world, chunkX, chunkZ);
                }
            }
        }
    }

    public void stop() {
        if (workerTask != null) {
            workerTask.cancel();
            workerTask = null;
        }
        pendingChunks.clear();
        queuedChunks.clear();
        generatedChunksByWorld.clear();
    }

    private void queueChunk(Chunk chunk) {
        queueChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    private void queueChunk(World world, int chunkX, int chunkZ) {
        Set<Long> generatedChunks = generatedChunksByWorld.computeIfAbsent(world.getUID(), key -> new HashSet<>());
        long chunkKey = getChunkKey(chunkX, chunkZ);
        if (!generatedChunks.add(chunkKey)) {
            return;
        }

        ChunkRequest request = new ChunkRequest(world, chunkX, chunkZ);
        if (queuedChunks.add(request)) {
            pendingChunks.offer(request);
        }
    }

    private void generateOreVeins(World world, int chunkX, int chunkZ) {
        for (int i = 0; i < veinsPerChunk; i++) {
            OreConfig.OreSettings settings = selectOreSettings();
            if (settings.material == null || random.nextDouble() > settings.chance) {
                continue;
            }

            Location center = findVeinCenter(world, chunkX, chunkZ, settings);
            if (center != null) {
                generateVeinAt(world, center, settings);
            }
        }
    }

    private Location findVeinCenter(World world, int chunkX, int chunkZ, OreConfig.OreSettings settings) {
        int minY = Math.max(world.getMinHeight(), settings.minY);
        int maxY = Math.min(world.getMaxHeight() - 1, settings.maxY);
        if (minY > maxY) {
            return null;
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        boolean preferVisible = random.nextDouble() < visibleVeinChance;
        Location fallback = null;

        for (int i = 0; i < CENTER_ATTEMPTS; i++) {
            int x = startX + random.nextInt(16);
            int y = randomBetween(minY, maxY);
            int z = startZ + random.nextInt(16);
            Block block = world.getBlockAt(x, y, z);
            if (!block.getChunk().isLoaded() || !isReplaceable(block.getType())) {
                continue;
            }

            if (fallback == null) {
                fallback = block.getLocation();
            }
            if (!preferVisible || isExposedToAir(block)) {
                return block.getLocation();
            }
        }

        return fallback;
    }

    private void generateVeinAt(World world, Location center, OreConfig.OreSettings settings) {
        int count = randomBetween(settings.minCount, settings.maxCount);
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();

        for (int n = 0; n < count; n++) {
            int dx = x + randomOffset(settings.radius);
            int dy = y + randomOffset(settings.radius);
            int dz = z + randomOffset(settings.radius);

            if (dy < world.getMinHeight() || dy >= world.getMaxHeight()) {
                continue;
            }

            Block block = world.getBlockAt(dx, dy, dz);
            if (block.getChunk().isLoaded() && isReplaceable(block.getType())) {
                block.setType(settings.material, false);
            }
        }
    }

    private boolean isSameChunk(Location from, Location to) {
        return (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4);
    }

    private boolean shouldGenerateBonusOres() {
        return plugin.isGameRunning() && !plugin.isVanillaHunterMode();
    }

    private OreConfig.OreSettings selectOreSettings() {
        int totalWeight = 0;
        for (OreConfig.OreSettings settings : OreConfig.getAllSettings()) {
            totalWeight += Math.max(0, settings.weight);
        }

        int selectedWeight = random.nextInt(Math.max(1, totalWeight));
        for (OreConfig.OreSettings settings : OreConfig.getAllSettings()) {
            selectedWeight -= Math.max(0, settings.weight);
            if (selectedWeight < 0) {
                return settings;
            }
        }
        return OreConfig.getSettings(Material.IRON_ORE);
    }

    private long getChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private boolean isReplaceable(Material material) {
        return NATURAL_STONES.contains(material);
    }

    private boolean isExposedToAir(Block block) {
        return AIR_LIKE_MATERIALS.contains(block.getRelative(1, 0, 0).getType())
                || AIR_LIKE_MATERIALS.contains(block.getRelative(-1, 0, 0).getType())
                || AIR_LIKE_MATERIALS.contains(block.getRelative(0, 1, 0).getType())
                || AIR_LIKE_MATERIALS.contains(block.getRelative(0, -1, 0).getType())
                || AIR_LIKE_MATERIALS.contains(block.getRelative(0, 0, 1).getType())
                || AIR_LIKE_MATERIALS.contains(block.getRelative(0, 0, -1).getType());
    }

    private int randomBetween(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    private int randomOffset(int radius) {
        return random.nextInt(radius * 2 + 1) - radius;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static final class ChunkRequest {
        private final World world;
        private final int chunkX;
        private final int chunkZ;

        private ChunkRequest(World world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkRequest)) return false;
            ChunkRequest other = (ChunkRequest) obj;
            return chunkX == other.chunkX && chunkZ == other.chunkZ && world.getUID().equals(other.world.getUID());
        }

        @Override
        public int hashCode() {
            int result = world.getUID().hashCode();
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            return result;
        }
    }
}
