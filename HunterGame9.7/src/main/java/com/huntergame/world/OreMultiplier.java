package com.huntergame.world;

import com.huntergame.HunterGame;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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

public class OreMultiplier implements Listener {
   private static final int DEFAULT_PLAYER_RADIUS_CHUNKS = 1;
   private static final int DEFAULT_MAX_CHUNKS_PER_TICK = 1;
   private static final int DEFAULT_VEINS_PER_CHUNK = 18;
   private static final double DEFAULT_VISIBLE_VEIN_CHANCE = 0.35;
   private static final int CENTER_ATTEMPTS = 12;
   private static final int PLAYER_CHUNK_SCAN_INTERVAL_TICKS = 100;
   private static final Set<Material> NATURAL_STONES;
   private static final Set<Material> AIR_LIKE_MATERIALS;
   private final HunterGame plugin;
   private final Random random = new Random();
   private final Map<UUID, Set<Long>> generatedChunksByWorld = new HashMap<>();
   private final Queue<ChunkRequest> pendingChunks = new ArrayDeque<>();
   private final Set<ChunkRequest> queuedChunks = new HashSet<>();
   private final int playerRadiusChunks;
   private final int maxChunksPerTick;
   private final int veinsPerChunk;
   private final double visibleVeinChance;
   private final List<OreConfig.OreSettings> oreSettings;
   private final int totalOreWeight;
   private int playerChunkScanTicks = 0;
   private BukkitTask workerTask;

   public OreMultiplier(HunterGame plugin) {
      this.plugin = plugin;
      this.playerRadiusChunks = Math.max(0, plugin.getConfig().getInt("ore_multiplier.player_radius_chunks", DEFAULT_PLAYER_RADIUS_CHUNKS));
      this.maxChunksPerTick = Math.max(1, plugin.getConfig().getInt("ore_multiplier.max_chunks_per_tick", DEFAULT_MAX_CHUNKS_PER_TICK));
      this.veinsPerChunk = Math.max(1, plugin.getConfig().getInt("ore_multiplier.veins_per_chunk", DEFAULT_VEINS_PER_CHUNK));
      this.visibleVeinChance = this.clamp(plugin.getConfig().getDouble("ore_multiplier.visible_vein_chance", DEFAULT_VISIBLE_VEIN_CHANCE));
      this.oreSettings = List.copyOf(OreConfig.getAllSettings());
      this.totalOreWeight = this.calculateTotalOreWeight();
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      this.startWorker();
   }

   @EventHandler
   public void onChunkPopulate(ChunkPopulateEvent event) {
      if (this.shouldGenerateBonusOres()) {
         this.queueChunk(event.getChunk());
      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Location to = event.getTo();
      if (to != null && this.shouldGenerateBonusOres() && !this.isSameChunk(event.getFrom(), to)) {
         World world = to.getWorld();
         if (world != null) {
            int currentChunkX = to.getBlockX() >> 4;
            int currentChunkZ = to.getBlockZ() >> 4;
            this.queueLoadedChunksAround(world, currentChunkX, currentChunkZ);
         }
      }
   }

   private void startWorker() {
      if (this.workerTask == null) {
         this.workerTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            this.scanPlayerChunksPeriodically();
            int processed = 0;

            while(processed < this.maxChunksPerTick && !this.pendingChunks.isEmpty()) {
               ChunkRequest request = this.pendingChunks.poll();
               this.queuedChunks.remove(request);
               if (request.world.isChunkLoaded(request.chunkX, request.chunkZ)) {
                  this.generateOreVeins(request.world, request.chunkX, request.chunkZ);
                  ++processed;
               }
            }

         }, 1L, 1L);
      }
   }

   public void start() {
      this.startWorker();
   }

   private void scanPlayerChunksPeriodically() {
      if (++this.playerChunkScanTicks >= PLAYER_CHUNK_SCAN_INTERVAL_TICKS) {
         this.playerChunkScanTicks = 0;
         if (this.shouldGenerateBonusOres()) {
            for(Player player : this.plugin.getServer().getOnlinePlayers()) {
               Location location = player.getLocation();
               World world = location.getWorld();
               if (world != null) {
                  this.queueLoadedChunksAround(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
               }
            }

         }
      }
   }

   private void queueLoadedChunksAround(World world, int currentChunkX, int currentChunkZ) {
      for(int dx = -this.playerRadiusChunks; dx <= this.playerRadiusChunks; ++dx) {
         for(int dz = -this.playerRadiusChunks; dz <= this.playerRadiusChunks; ++dz) {
            int chunkX = currentChunkX + dx;
            int chunkZ = currentChunkZ + dz;
            if (world.isChunkLoaded(chunkX, chunkZ)) {
               this.queueChunk(world, chunkX, chunkZ);
            }
         }
      }

   }

   public void stop() {
      if (this.workerTask != null) {
         this.workerTask.cancel();
         this.workerTask = null;
      }

      this.pendingChunks.clear();
      this.queuedChunks.clear();
      this.generatedChunksByWorld.clear();
   }

   private void queueChunk(Chunk chunk) {
      this.queueChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
   }

   private void queueChunk(World world, int chunkX, int chunkZ) {
      Set<Long> generatedChunks = this.generatedChunksByWorld.computeIfAbsent(world.getUID(), (key) -> new HashSet<>());
      long chunkKey = this.getChunkKey(chunkX, chunkZ);
      if (generatedChunks.add(chunkKey)) {
         ChunkRequest request = new ChunkRequest(world, chunkX, chunkZ);
         if (this.queuedChunks.add(request)) {
            this.pendingChunks.offer(request);
         }

      }
   }

   private void generateOreVeins(World world, int chunkX, int chunkZ) {
      for(int i = 0; i < this.veinsPerChunk; ++i) {
         OreConfig.OreSettings settings = this.selectOreSettings();
         if (settings.material != null && !(this.random.nextDouble() > settings.chance)) {
            Location center = this.findVeinCenter(world, chunkX, chunkZ, settings);
            if (center != null) {
               this.generateVeinAt(world, center, settings);
            }
         }
      }

   }

   private Location findVeinCenter(World world, int chunkX, int chunkZ, OreConfig.OreSettings settings) {
      int minY = Math.max(world.getMinHeight(), settings.minY);
      int maxY = Math.min(world.getMaxHeight() - 1, settings.maxY);
      if (minY > maxY) {
         return null;
      } else {
         int startX = chunkX << 4;
         int startZ = chunkZ << 4;
         boolean preferVisible = this.random.nextDouble() < this.visibleVeinChance;
         Location fallback = null;

         for(int i = 0; i < CENTER_ATTEMPTS; ++i) {
            int x = startX + this.random.nextInt(16);
            int y = this.randomBetween(minY, maxY);
            int z = startZ + this.random.nextInt(16);
            Block block = world.getBlockAt(x, y, z);
            if (block.getChunk().isLoaded() && this.isReplaceable(block.getType())) {
               if (fallback == null) {
                  fallback = block.getLocation();
               }

               if (!preferVisible || this.isExposedToAir(block)) {
                  return block.getLocation();
               }
            }
         }

         return fallback;
      }
   }

   private void generateVeinAt(World world, Location center, OreConfig.OreSettings settings) {
      int count = this.randomBetween(settings.minCount, settings.maxCount);
      int x = center.getBlockX();
      int y = center.getBlockY();
      int z = center.getBlockZ();

      for(int n = 0; n < count; ++n) {
         int dx = x + this.randomOffset(settings.radius);
         int dy = y + this.randomOffset(settings.radius);
         int dz = z + this.randomOffset(settings.radius);
         if (dy >= world.getMinHeight() && dy < world.getMaxHeight()) {
            Block block = world.getBlockAt(dx, dy, dz);
            if (block.getChunk().isLoaded() && this.isReplaceable(block.getType())) {
               block.setType(settings.material, false);
            }
         }
      }

   }

   private boolean isSameChunk(Location from, Location to) {
      return from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4;
   }

   private boolean shouldGenerateBonusOres() {
      return this.plugin.isGameRunning() && !this.plugin.isVanillaHunterMode();
   }

   private int calculateTotalOreWeight() {
      int totalWeight = 0;

      for(OreConfig.OreSettings settings : this.oreSettings) {
         totalWeight += Math.max(0, settings.weight);
      }

      return Math.max(1, totalWeight);
   }

   private OreConfig.OreSettings selectOreSettings() {
      int selectedWeight = this.random.nextInt(this.totalOreWeight);

      for(OreConfig.OreSettings settings : this.oreSettings) {
         selectedWeight -= Math.max(0, settings.weight);
         if (selectedWeight < 0) {
            return settings;
         }
      }

      return OreConfig.getSettings(Material.IRON_ORE);
   }

   private long getChunkKey(int chunkX, int chunkZ) {
      return (long)chunkX << 32 | (long)chunkZ & 4294967295L;
   }

   private boolean isReplaceable(Material material) {
      return NATURAL_STONES.contains(material);
   }

   private boolean isExposedToAir(Block block) {
      return AIR_LIKE_MATERIALS.contains(block.getRelative(1, 0, 0).getType()) || AIR_LIKE_MATERIALS.contains(block.getRelative(-1, 0, 0).getType()) || AIR_LIKE_MATERIALS.contains(block.getRelative(0, 1, 0).getType()) || AIR_LIKE_MATERIALS.contains(block.getRelative(0, -1, 0).getType()) || AIR_LIKE_MATERIALS.contains(block.getRelative(0, 0, 1).getType()) || AIR_LIKE_MATERIALS.contains(block.getRelative(0, 0, -1).getType());
   }

   private int randomBetween(int min, int max) {
      return this.random.nextInt(max - min + 1) + min;
   }

   private int randomOffset(int radius) {
      return this.random.nextInt(radius * 2 + 1) - radius;
   }

   private double clamp(double value) {
      return Math.max((double)0.0F, Math.min((double)1.0F, value));
   }

   static {
      NATURAL_STONES = Set.of(Material.STONE, Material.DEEPSLATE, Material.ANDESITE, Material.GRANITE, Material.DIORITE, Material.TUFF, Material.CALCITE, Material.DRIPSTONE_BLOCK);
      AIR_LIKE_MATERIALS = Set.of(Material.AIR, Material.CAVE_AIR, Material.WATER, Material.LAVA);
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

      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (!(obj instanceof ChunkRequest)) {
            return false;
         } else {
            ChunkRequest other = (ChunkRequest)obj;
            return this.chunkX == other.chunkX && this.chunkZ == other.chunkZ && this.world.getUID().equals(other.world.getUID());
         }
      }

      public int hashCode() {
         int result = this.world.getUID().hashCode();
         result = 31 * result + this.chunkX;
         result = 31 * result + this.chunkZ;
         return result;
      }
   }
}
