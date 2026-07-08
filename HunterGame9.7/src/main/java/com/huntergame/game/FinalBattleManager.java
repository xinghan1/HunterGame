package com.huntergame.game;

import com.huntergame.HunterGame;
import com.huntergame.role.RoleSelectionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class FinalBattleManager implements Listener {
   private final HunterGame plugin;
   private final Map<UUID, Integer> playerVotes;
   private final List<Player> finalBattleEscapers = new ArrayList();
   private final List<Player> hunters = new ArrayList();
   private boolean gameActive = false;
   private boolean dragonHealthModified = false;
   private final Map<UUID, Set<Location>> playerCages = new HashMap();
   private final Map<UUID, Integer> taskIds = new HashMap();
   private final Set<Location> barrierBlocks = new HashSet();
   private final Map<UUID, Integer> titleTaskIds = new HashMap();

   public FinalBattleManager(HunterGame plugin, Map<UUID, Integer> votes) {
      this.plugin = plugin;
      this.playerVotes = votes;
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
   }

   public void startFinalBattle() {
      this.gameActive = true;
      this.plugin.setGameInProgress(true);
      this.plugin.startGame();
      this.assignRoles();
      World endWorld = this.getOrCreateEndWorld();
      if (endWorld == null) {
         Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_end_world_failed", "&c\u672b\u5730\u4e16\u754c\u52a0\u8f7d\u5931\u8d25\uff01"));
      } else {
         for(Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 700, 0, false));
         }

         this.teleportPlayers(endWorld);
         this.openProfessionSelection();
         this.createCagesForAllPlayers();
         this.checkAndModifyExistingDragon(endWorld);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_started", "&a===== \u7ec8\u7ae0\u4e4b\u6218 \u5df2\u542f\u52a8 ====="));
            if (this.plugin.isPersistenceBattle()) {
               int minutes = this.plugin.getConfig().getInt("game.persistence_modes.final_battle_minutes", 15);
               Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_persistence_mode_title", "&e\u3010\u751f\u5b58\u6218\u6a21\u5f0f\u3011"));
               Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_persistence_escaper_objective", "&7\u2022 \u9003\u751f\u8005\u76ee\u6807\uff1a\u5b58\u6d3b %minutes% \u5206\u949f \u2022").replace("%minutes%", String.valueOf(minutes)));
               Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_persistence_hunter_objective", "&7\u2022 \u730e\u4eba\u76ee\u6807\uff1a\u963b\u6b62\u9003\u751f\u8005\uff0c\u6b7c\u706d\u6218 \u2022"));
            } else {
               Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_clearance_mode_title", "&c\u3010\u901a\u5173\u6218\u6a21\u5f0f\u3011"));
               Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_clearance_escaper_objective", "&7\u2022 \u9003\u751f\u8005\u76ee\u6807\uff1a\u51fb\u6740\u672b\u5f71\u9f99 \u2022"));
               Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_clearance_hunter_objective", "&7\u2022 \u730e\u4eba\u76ee\u6807\uff1a\u963b\u6b62\u9003\u751f\u8005\uff0c\u6b7c\u706d\u6218 \u2022"));
            }

            Bukkit.broadcastMessage(this.plugin.getMessage("final_battle_team_ratio", "&7\u2022 \u9635\u8425\uff1a %escapers%\u540d\u9003\u751f\u8005 vs %hunters%\u540d\u730e\u4eba \u2022").replace("%escapers%", String.valueOf(this.finalBattleEscapers.size())).replace("%hunters%", String.valueOf(this.hunters.size())));

            for(Player hunter : this.plugin.getHunters()) {
               hunter.sendMessage(this.plugin.getMessage("hunter_identity", "&a\u4f60\u662f &c\u730e\u4eba\uff01"));
            }

            for(Player escaper : this.plugin.getEscapers()) {
               escaper.sendMessage(this.plugin.getMessage("escaper_identity", "&a\u4f60\u662f &b\u9003\u751f\u8005\uff01"));
            }

         }, 30L);

         for(Player player : Bukkit.getOnlinePlayers()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(GameMode.SURVIVAL);
         }

      }
   }

   private void checkAndModifyExistingDragon(World world) {
      for(Entity entity : world.getEntities()) {
         if (entity instanceof EnderDragon) {
            this.modifyDragonHealth((EnderDragon)entity);
            break;
         }
      }

   }

   private void modifyDragonHealth(EnderDragon dragon) {
      AttributeInstance maxHealthAttribute = dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH);
      if (maxHealthAttribute != null) {
         maxHealthAttribute.setBaseValue((double)400.0F);
         dragon.setHealth((double)400.0F);
         this.dragonHealthModified = true;
         dragon.getWorld().strikeLightningEffect(dragon.getLocation());
         Bukkit.broadcastMessage(this.plugin.getMessage("dragon_awakened", "&c\u672b\u5f71\u9f99\u5df2\u89c9\u9192\uff01\u751f\u547d\u503c: %health%").replace("%health%", "400"));
      }

   }

   @EventHandler
   public void onDragonSpawn(CreatureSpawnEvent event) {
      if (event.getEntity() instanceof EnderDragon && !this.dragonHealthModified) {
         EnderDragon dragon = (EnderDragon)event.getEntity();
         this.modifyDragonHealth(dragon);
      }

   }

   private void createCagesForAllPlayers() {
      List<Player> allPlayers = new ArrayList(this.hunters);
      allPlayers.addAll(this.finalBattleEscapers);

      for(Player player : allPlayers) {
         this.createCage(player);
      }

   }

   public void createCage(final Player player) {
      final UUID playerId = player.getUniqueId();
      Location center = player.getLocation().clone();
      center = center.getBlock().getLocation().add((double)0.0F, (double)1.0F, (double)0.0F);
      Set<Location> cageBlocks = new HashSet();
      int radius = 2;

      for(int x = -radius; x <= radius; ++x) {
         for(int y = -1; y <= 3; ++y) {
            for(int z = -radius; z <= radius; ++z) {
               boolean isSurface = Math.abs(x) == radius || Math.abs(z) == radius || y == -1 || y == 3;
               Location loc = center.clone().add((double)x, (double)y, (double)z);
               Block block = loc.getBlock();
               if (isSurface) {
                  block.setType(Material.BARRIER);
                  cageBlocks.add(loc);
                  this.barrierBlocks.add(loc);
               } else {
                  block.setType(Material.AIR);
               }
            }
         }
      }

      this.playerCages.put(player.getUniqueId(), cageBlocks);
      int totalSeconds = this.plugin.isEscaper(playerId) ? 30 : 35;
      int delayTicks = totalSeconds * 20;
      this.startTitleCountdown(player, totalSeconds);
      int taskId = (new BukkitRunnable() {
         public void run() {
            FinalBattleManager.this.removeCage(player);
            FinalBattleManager.this.taskIds.remove(player.getUniqueId());
            FinalBattleManager.this.cancelTitleTask(playerId);
            player.sendTitle(FinalBattleManager.this.plugin.getMessage("final_battle_cage_start_title", "&a\u6e38\u620f\u5df2\u5f00\u59cb\uff01"), FinalBattleManager.this.plugin.getMessage("final_battle_cage_start_subtitle", ""), 10, 40, 10);
         }
      }).runTaskLater(this.plugin, (long)delayTicks).getTaskId();
      this.taskIds.put(player.getUniqueId(), taskId);
   }

   private void cancelTitleTask(UUID playerId) {
      if (this.titleTaskIds.containsKey(playerId)) {
         Bukkit.getScheduler().cancelTask((Integer)this.titleTaskIds.get(playerId));
         this.titleTaskIds.remove(playerId);
      }

   }

   private void startTitleCountdown(final Player player, final int totalSeconds) {
      UUID playerId = player.getUniqueId();
      player.sendTitle(this.plugin.getMessage("final_battle_cage_countdown_title", "&e\u8bf7\u6ce8\u610f\u5f53\u524d\u73af\u5883\u662f\u5426\u5b89\u5168"), this.plugin.getMessage("final_battle_cage_countdown_subtitle", "&c\u51c6\u5907\u5f00\u59cb\uff1a%seconds%\u79d2").replace("%seconds%", String.valueOf(totalSeconds)), 0, 20, 0);
      int titleTaskId = (new BukkitRunnable() {
         int remaining = totalSeconds - 1;

         public void run() {
            if (this.remaining <= 0) {
               this.cancel();
            } else {
               player.sendTitle(FinalBattleManager.this.plugin.getMessage("final_battle_cage_countdown_title", "&e\u8bf7\u6ce8\u610f\u5f53\u524d\u73af\u5883\u662f\u5426\u5b89\u5168"), FinalBattleManager.this.plugin.getMessage("final_battle_cage_countdown_subtitle", "&c\u51c6\u5907\u5f00\u59cb\uff1a%seconds%\u79d2").replace("%seconds%", String.valueOf(this.remaining)), 0, 20, 0);
               --this.remaining;
            }
         }
      }).runTaskTimer(this.plugin, 20L, 20L).getTaskId();
      this.titleTaskIds.put(playerId, titleTaskId);
   }

   private void removeCage(Player player) {
      UUID playerId = player.getUniqueId();
      if (this.playerCages.containsKey(playerId)) {
         for(Location loc : (Set<Location>)this.playerCages.get(playerId)) {
            Block block = loc.getBlock();
            if (block.getType() == Material.BARRIER) {
               block.setType(Material.AIR);
            }
         }

         this.playerCages.remove(playerId);
      }

   }

   private void assignRoles() {
      List<Player> players = new ArrayList(Bukkit.getOnlinePlayers());
      this.finalBattleEscapers.clear();
      this.hunters.clear();
      int playerCount = players.size();
      int targetEscaperCount = this.getTargetEscaperCount(playerCount);
      List<Player> escaperCandidates = (List)players.stream().filter((px) -> (Integer)this.playerVotes.getOrDefault(px.getUniqueId(), 0) == 1).collect(Collectors.toList());
      Collections.shuffle(escaperCandidates);

      while(this.finalBattleEscapers.size() < targetEscaperCount && !escaperCandidates.isEmpty()) {
         this.finalBattleEscapers.add((Player)escaperCandidates.remove(0));
      }

      if (this.finalBattleEscapers.size() < targetEscaperCount) {
         List<Player> remainingPlayers = new ArrayList(players);
         remainingPlayers.removeAll(this.finalBattleEscapers);
         Collections.shuffle(remainingPlayers);

         while(this.finalBattleEscapers.size() < targetEscaperCount && !remainingPlayers.isEmpty()) {
            this.finalBattleEscapers.add((Player)remainingPlayers.remove(0));
         }
      }

      for(Player p : players) {
         if (!this.finalBattleEscapers.contains(p)) {
            this.hunters.add(p);
            this.plugin.addHunter(p.getUniqueId());
            p.getPersistentDataContainer().set(RoleSelectionHandler.IS_HUNTER, PersistentDataType.BOOLEAN, true);
         }
      }

      for(Player esc : this.finalBattleEscapers) {
         this.plugin.addEscaper(esc.getUniqueId());
         esc.getPersistentDataContainer().set(RoleSelectionHandler.IS_ESCAPER, PersistentDataType.BOOLEAN, true);
         this.applyGlowingEffect(esc);
      }

   }

   private int getTargetEscaperCount(int totalPlayers) {
      int count = this.plugin.getConfig().getInt("player_counts.final_battle.scaling.default", 1);
      ConfigurationSection thresholds = this.plugin.getConfig().getConfigurationSection("player_counts.final_battle.scaling.thresholds");
      if (thresholds != null) {
         for(int threshold : (List<Integer>)thresholds.getKeys(false).stream().map((key) -> {
            try {
               return Integer.parseInt(key);
            } catch (NumberFormatException var2) {
               return -1;
            }
         }).filter((key) -> key > 0).sorted(Collections.reverseOrder()).collect(Collectors.toList())) {
            if (totalPlayers >= threshold) {
               return thresholds.getInt(String.valueOf(threshold), 1);
            }
         }
      }

      return count;
   }

   private void applyGlowingEffect(Player player) {
      PotionEffect glowing = new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false);
      player.addPotionEffect(glowing);
   }

   private World getOrCreateEndWorld() {
      World end = Bukkit.getWorld("world_the_end");
      if (end == null) {
         WorldCreator creator = new WorldCreator("world_the_end");
         creator.environment(Environment.THE_END);
         creator.generateStructures(false);
         end = Bukkit.createWorld(creator);
      }

      if (end != null) {
         end.setTime(6000L);
         end.setStorm(false);
         end.setThundering(false);
      }

      return end;
   }

   private void teleportPlayers(World endWorld) {
      Location center = new Location(endWorld, (double)100.0F, (double)70.0F, (double)0.0F);
      Location escLoc = this.findSafeLocation(endWorld, center, (double)0.0F);

      for(Player esc : this.finalBattleEscapers) {
         this.enableFinalBattleFlightTolerance(esc);
         esc.teleport(escLoc);
         esc.setBedSpawnLocation(escLoc, true);
      }

      Location huntLoc = this.findOffsetLocation(endWorld, center, (double)30.0F);

      for(Player h : this.hunters) {
         this.enableFinalBattleFlightTolerance(h);
         h.teleport(huntLoc);
         h.setBedSpawnLocation(huntLoc, true);
      }

   }

   private void openProfessionSelection() {
      List<Player> players = new ArrayList(this.finalBattleEscapers);
      players.addAll(this.hunters);
      this.plugin.getFinalBattleProfessionManager().startSelection(players);
   }

   private void enableFinalBattleFlightTolerance(Player player) {
      if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
         player.setAllowFlight(false);
         player.setFlying(false);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline() && this.plugin.isGameRunning() && this.plugin.isFinalBattleMode() && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE) {
               player.setAllowFlight(false);
               player.setFlying(false);
            }
         }, 100L);
      }
   }

   private Location findSafeLocation(World world, Location center, double offset) {
      Random rand = new Random();

      for(int attempts = 0; attempts < 100; ++attempts) {
         double angle = rand.nextDouble() * Math.PI * (double)2.0F;
         double x = center.getX() + Math.cos(angle) * offset;
         double z = center.getZ() + Math.sin(angle) * offset;
         int y = world.getHighestBlockYAt((int)x, (int)z);
         Location loc = new Location(world, x, (double)(y + 1), z);
         if (this.isLocationSafe(loc)) {
            return loc;
         }
      }

      return center;
   }

   private Location findOffsetLocation(World world, Location center, double distance) {
      Random rand = new Random();
      double angle = rand.nextDouble() * Math.PI * (double)2.0F;
      double x = center.getX() + Math.cos(angle) * distance;
      double z = center.getZ() + Math.sin(angle) * distance;
      int y = world.getHighestBlockYAt((int)x, (int)z);
      Location loc = new Location(world, x, (double)(y + 1), z);
      return this.isLocationSafe(loc) ? loc : this.findSafeLocation(world, center, distance);
   }

   private boolean isLocationSafe(Location loc) {
      Block feet = loc.getBlock();
      Block head = loc.clone().add((double)0.0F, (double)1.0F, (double)0.0F).getBlock();
      Block below = loc.clone().add((double)0.0F, (double)-1.0F, (double)0.0F).getBlock();
      return !feet.getType().isSolid() && !head.getType().isSolid() && below.getType().isSolid() && !feet.isLiquid() && !head.isLiquid();
   }
}
