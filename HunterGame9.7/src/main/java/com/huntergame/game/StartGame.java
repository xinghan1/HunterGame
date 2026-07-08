package com.huntergame.game;

import com.huntergame.HunterGame;
import com.huntergame.role.RoleSelectionHandler;
import com.huntergame.vote.VoteSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class StartGame implements Listener {
   private final HunterGame plugin;
   private boolean countdownInProgress = false;
   private int countdownTaskId = -1;
   private final Map<UUID, Integer> respawnTimers = new HashMap();
   private final Map<UUID, BukkitRunnable> respawnTasks = new HashMap();
   private final Map<UUID, Integer> finalBattleHunterRespawns = new HashMap();
   private final Map<UUID, Long> hunterBackpackCooldown = new HashMap();
   private final Map<UUID, Long> escaperBackpackCooldown = new HashMap();
   private long HUNTER_SHARED_BACKPACK_COOLDOWN;
   private long ESCAPER_SHARED_BACKPACK_COOLDOWN;
   private final VoteSystem voteSystem;

   public StartGame(HunterGame plugin) {
      this.plugin = plugin;
      this.voteSystem = new VoteSystem(plugin);
      FileConfiguration config = plugin.getConfig();
      this.HUNTER_SHARED_BACKPACK_COOLDOWN = (long)config.getInt("game.hunter_shared_backpack", config.getInt("hunter_shared_backpack", 90));
      this.ESCAPER_SHARED_BACKPACK_COOLDOWN = (long)config.getInt("game.escaper_shared_backpack", config.getInt("escaper_shared_backpack", 90));
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (!this.plugin.isGameRunning()) {
         this.checkAndStartGame();
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.voteSystem.giveVoteItemToPlayer(player), 5L);
      }

      if (this.respawnTimers.containsKey(playerId)) {
         int remainingTime = (Integer)this.respawnTimers.get(playerId);
         player.sendMessage(this.plugin.getMessage("resurrection_countdown", "&e\u4f60\u8fd8\u6709 %remainingTime% \u79d2\u590d\u6d3b\uff01").replace("%remainingTime%", String.valueOf(remainingTime)));
         player.setGameMode(GameMode.SPECTATOR);
         this.startRespawnCountdown(player, remainingTime);
      }

   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.checkAndCancelCountdownIfNeeded();
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (this.respawnTasks.containsKey(playerId)) {
         ((BukkitRunnable)this.respawnTasks.get(playerId)).cancel();
         this.respawnTasks.remove(playerId);
      }

   }

   public void checkAndStartGame() {
      if (!this.countdownInProgress) {
         int minPlayers = this.plugin.getConfig().getInt("game.minPlayers", 2);
         int countdownTime = this.plugin.getConfig().getInt("game.countdown", 60);
         List<Player> players = new ArrayList(Bukkit.getOnlinePlayers());
         if (players.size() < minPlayers) {
            Bukkit.broadcastMessage(this.plugin.getMessage("not_enough_players_start", "&c\u5f53\u524d\u6e38\u620f\u6700\u5c11\u9700\u8981 %min_players% \u4eba\u624d\u80fd\u5f00\u59cb\uff01").replace("%min_players%", String.valueOf(minPlayers)));
         } else {
            this.startCountdown(countdownTime);
         }
      }
   }

   private void startCountdown(final int seconds) {
      this.countdownInProgress = true;
      List<Integer> configuredKeyTimes = this.plugin.getConfig().getIntegerList("game.countdownKeyTimes");
      final List<Integer> keyTimes = configuredKeyTimes.isEmpty() ? Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1) : configuredKeyTimes;

      this.countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, new Runnable() {
         private int timeLeft = seconds;

         public void run() {
            if (!StartGame.this.plugin.isGameRunning()) {
               int minPlayers = StartGame.this.plugin.getConfig().getInt("game.minPlayers", 2);
               if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                  Bukkit.broadcastMessage(StartGame.this.plugin.getMessage("cancel_game_countdown", "&c\u7531\u4e8e\u73a9\u5bb6\u4eba\u6570\u4e0d\u8db3\uff0c\u65e0\u6cd5\u542f\u7528\u6e38\u620f\u5012\u8ba1\u65f6\uff01"));
                  StartGame.this.cancelCountdown();
               } else {
                  if (keyTimes.contains(this.timeLeft)) {
                     for(Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle(StartGame.this.plugin.getMessage("countdown_title", "&e\u6e38\u620f\u5373\u5c06\u5f00\u59cb"), StartGame.this.plugin.getMessage("countdown_subtitle", "&c%time% \u79d2\uff01").replace("%time%", String.valueOf(this.timeLeft)), 10, 20, 10);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.0F);
                     }

                     Bukkit.broadcastMessage(StartGame.this.plugin.getMessage("game_starting_soon", "&6\u6e38\u620f\u5c06\u5728 %time% \u79d2\u540e\u5f00\u59cb\uff01").replace("%time%", String.valueOf(this.timeLeft)));
                  }

                  if (this.timeLeft <= 0) {
                     Bukkit.getScheduler().cancelTask(StartGame.this.countdownTaskId);
                     StartGame.this.startGame();
                  }

                  --this.timeLeft;
               }
            }
         }
      }, 0L, 20L);
   }

   public void cancelCountdown() {
      if (this.countdownInProgress) {
         Bukkit.getScheduler().cancelTask(this.countdownTaskId);
         this.countdownInProgress = false;
         this.countdownTaskId = -1;
      }

   }

   public void clearFinalBattleRespawnData() {
      this.finalBattleHunterRespawns.clear();
   }

   public void resetRuntimeData() {
      this.cancelCountdown();

      for(BukkitRunnable task : this.respawnTasks.values()) {
         task.cancel();
      }

      this.respawnTasks.clear();
      this.respawnTimers.clear();
      this.finalBattleHunterRespawns.clear();
      this.hunterBackpackCooldown.clear();
      this.escaperBackpackCooldown.clear();
      this.voteSystem.resetVoteData();
   }

   private void checkAndCancelCountdownIfNeeded() {
      if (!this.plugin.isGameRunning()) {
         if (!this.plugin.isResetting()) {
            if (!this.plugin.isGameEnded()) {
               int minPlayers = this.plugin.getConfig().getInt("game.minPlayers", 2);
               if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                  this.cancelCountdown();
                  Bukkit.broadcastMessage(this.plugin.getMessage("cancel_game_countdown", "&c\u7531\u4e8e\u73a9\u5bb6\u4eba\u6570\u4e0d\u8db3\uff0c\u65e0\u6cd5\u542f\u7528\u6e38\u620f\u5012\u8ba1\u65f6\uff01"));
               }

            }
         }
      }
   }

   public void giveHunterMark(Player targetPlayer) {
      if (targetPlayer != null) {
         PersistentDataContainer container = targetPlayer.getPersistentDataContainer();
         container.set(RoleSelectionHandler.IS_HUNTER, PersistentDataType.BOOLEAN, true);
      }

   }

   public void giveEscaperMark(Player targetPlayer) {
      if (targetPlayer != null) {
         PersistentDataContainer container = targetPlayer.getPersistentDataContainer();
         container.set(RoleSelectionHandler.IS_ESCAPER, PersistentDataType.BOOLEAN, true);
      }

   }

   public void startGame() {
      if (this.plugin.isGameRunning()) {
         Bukkit.broadcastMessage(this.plugin.getMessage("game_progress", "&c游戏已经在进行中，不能重复启动！"));
      } else {
         this.plugin.getGameSettlement().resetStats();
         int selectedType = this.voteSystem.determineFinalBattleType();
         this.plugin.setBattleType(selectedType);
         this.plugin.setGameMode(2);
         (new FinalBattleManager(this.plugin, this.voteSystem.getPlayerRoleVotes())).startFinalBattle();
      }
   }

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Player player = event.getPlayer();
         UUID playerId = player.getUniqueId();
         ItemStack item = event.getItem();
         if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
               long currentTime = System.currentTimeMillis();
               if (this.plugin.isHunterSharedBackpack(item) && this.plugin.isHunter(playerId)) {
                  if (this.checkCooldown(playerId, this.hunterBackpackCooldown, this.HUNTER_SHARED_BACKPACK_COOLDOWN * 1000L)) {
                     int remaining = this.getRemainingCooldown(playerId, this.hunterBackpackCooldown, this.HUNTER_SHARED_BACKPACK_COOLDOWN * 1000L);
                     player.sendMessage(this.plugin.getMessage("shared_backpack_countdown", "&c\u80cc\u5305\u4ecd\u5728\u51b7\u5374\u4e2d\uff0c\u5269\u4f59 %remaining% \u79d2\uff01").replace("%remaining%", String.valueOf(remaining)));
                     event.setCancelled(true);
                  } else {
                     player.openInventory(this.plugin.getHunterSharedInventory());
                     this.hunterBackpackCooldown.put(playerId, currentTime);
                     event.setCancelled(true);
                  }
               } else if (this.plugin.isEscaperSharedBackpack(item) && this.plugin.isEscaper(playerId)) {
                  if (this.checkCooldown(playerId, this.escaperBackpackCooldown, this.ESCAPER_SHARED_BACKPACK_COOLDOWN * 1000L)) {
                     int remaining = this.getRemainingCooldown(playerId, this.escaperBackpackCooldown, this.ESCAPER_SHARED_BACKPACK_COOLDOWN * 1000L);
                     player.sendMessage(this.plugin.getMessage("shared_backpack_countdown", "&c\u80cc\u5305\u4ecd\u5728\u51b7\u5374\u4e2d\uff0c\u5269\u4f59 %remaining% \u79d2\uff01").replace("%remaining%", String.valueOf(remaining)));
                     event.setCancelled(true);
                  } else {
                     player.openInventory(this.plugin.getEscaperSharedInventory());
                     this.escaperBackpackCooldown.put(playerId, currentTime);
                     event.setCancelled(true);
                  }
               } else {
                  if (this.plugin.isHunterSharedBackpack(item)) {
                     player.sendMessage(this.plugin.getMessage("unable_open_hunter_backpack", "&c\u4f60\u65e0\u6cd5\u6253\u5f00\u730e\u4eba\u7684\u5171\u4eab\u80cc\u5305\uff01"));
                  } else if (this.plugin.isEscaperSharedBackpack(item)) {
                     player.sendMessage(this.plugin.getMessage("unable_open_escape_backpack", "&c\u4f60\u65e0\u6cd5\u6253\u5f00\u9003\u751f\u8005\u7684\u5171\u4eab\u80cc\u5305\uff01"));
                  }

                  event.setCancelled(true);
               }
            }
         }
      }
   }

   private boolean checkCooldown(UUID playerId, Map<UUID, Long> cooldownMap, long cooldownTime) {
      Long lastUseTime = (Long)cooldownMap.get(playerId);
      if (lastUseTime == null) {
         return false;
      } else {
         long remainingCooldown = lastUseTime + cooldownTime - System.currentTimeMillis();
         return remainingCooldown > 0L;
      }
   }

   private int getRemainingCooldown(UUID playerId, Map<UUID, Long> cooldownMap, long cooldownTime) {
      Long lastUseTime = (Long)cooldownMap.get(playerId);
      if (lastUseTime == null) {
         return 0;
      } else {
         long remainingMillis = lastUseTime + cooldownTime - System.currentTimeMillis();
         return (int)Math.max(0L, remainingMillis / 1000L);
      }
   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      UUID playerId = player.getUniqueId();
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline() && player.isDead()) {
            player.spigot().respawn();
         }

      }, 1L);
      if (this.plugin.isFinalBattleMode()) {
         player.setAllowFlight(false);
         player.setFlying(false);
         player.setGameMode(GameMode.SPECTATOR);
         this.plugin.addRealSpectator(playerId);
         this.respawnTimers.remove(playerId);
         BukkitRunnable oldTask = (BukkitRunnable)this.respawnTasks.remove(playerId);
         if (oldTask != null) {
            oldTask.cancel();
         }

         player.sendMessage(this.plugin.getMessage("final_battle_no_respawn", "&c终章模式死亡后无法复活！"));
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plugin.teleportSpectatorToRandomPlayer(player), 20L);
      }
   }

   @EventHandler
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (this.plugin.isGameRunning()) {
         boolean shouldStaySpectator = this.respawnTimers.containsKey(playerId) || this.plugin.isRealSpectator(playerId) || (this.plugin.isFinalBattleMode() && this.plugin.isDeathescapers(playerId));
         if (shouldStaySpectator) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.forceSpectatorWhileWaiting(player), 1L);
         }
      }
   }

   private void forceSpectatorWhileWaiting(Player player) {
      if (player != null && player.isOnline()) {
         UUID playerId = player.getUniqueId();
         boolean shouldStaySpectator = this.respawnTimers.containsKey(playerId) || this.plugin.isRealSpectator(playerId) || (this.plugin.isFinalBattleMode() && this.plugin.isDeathescapers(playerId));
         if (shouldStaySpectator) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(GameMode.SPECTATOR);
            this.plugin.addRealSpectator(playerId);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR && this.plugin.isRealSpectator(playerId)) {
                  this.plugin.teleportSpectatorToRandomPlayer(player);
               }

            }, 2L);
         }
      }
   }

   void startRespawnCountdown(final Player player, final int respawnTime) {
      final UUID playerId = player.getUniqueId();
      BukkitRunnable task = new BukkitRunnable() {
         int timeLeft = respawnTime;

         public void run() {
            if (this.timeLeft <= 0) {
               StartGame.this.respawnPlayer(player);
               StartGame.this.respawnTimers.remove(playerId);
               StartGame.this.respawnTasks.remove(playerId);
               this.cancel();
            } else {
               if (this.timeLeft == 180 || this.timeLeft == 150 || this.timeLeft == 120 || this.timeLeft == 90 || this.timeLeft == 60 || this.timeLeft == 30 || this.timeLeft <= 10) {
                  player.sendTitle(StartGame.this.plugin.getMessage("cage_countdown_title", "&c%time%").replace("%time%", String.valueOf(this.timeLeft)), StartGame.this.plugin.getMessage("cage_countdown_subtitle", ""), 0, 20, 0);
               }

               StartGame.this.respawnTimers.put(playerId, this.timeLeft);
               --this.timeLeft;
            }
         }
      };
      task.runTaskTimer(this.plugin, 0L, 20L);
      this.respawnTasks.put(playerId, task);
   }

   private void respawnPlayer(Player player) {
      if (this.plugin.isFinalBattleMode()) {
         this.forceSpectatorWhileWaiting(player);
         return;
      }

      player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 255));
      player.setAllowFlight(false);
      player.setFlying(false);
      player.setGameMode(GameMode.SURVIVAL);
      this.plugin.removeRealSpectator(player.getUniqueId());
      World endWorld = Bukkit.getWorld("world_the_end");
      if (endWorld != null) {
         Location bedSpawn = player.getBedSpawnLocation();
         if (bedSpawn != null && bedSpawn.getWorld() != null && bedSpawn.getWorld().getEnvironment() == Environment.THE_END) {
            player.teleport(bedSpawn);
         } else {
            Location escaperLocation = this.findClosestEscaperInEnd();
            if (escaperLocation != null) {
               int respawnRadius = this.plugin.getConfig().getInt("game.hunter_respawn_radius", 50);
               Location respawnLocation = this.findSafeLocationInEnd(endWorld, escaperLocation, respawnRadius);
               player.teleport(respawnLocation);
            } else {
               Location endSpawn = this.findSafeLocationInEnd(endWorld, new Location(endWorld, (double)100.0F, (double)70.0F, (double)0.0F), 10);
               player.teleport(endSpawn);
            }
         }
      }

      player.sendTitle(this.plugin.getMessage("resurrection", "&a你已复活！"), "", 10, 40, 10);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         this.plugin.getFinalBattleProfessionManager().giveSelectedProfessionLoadout(player);
         if ("爆炸弩".equals(this.plugin.getSkillManager().getSelectedSkill(player)) && this.plugin.getSkillManager().isSkillEnabled("爆炸弩")) {
            this.plugin.getExplosiveCrossbowListener().giveCrossbowPackage(player);
         }
      }, 40L);
   }

   private Location findClosestEscaperInEnd() {
      World endWorld = Bukkit.getWorld("world_the_end");
      if (endWorld == null) {
         return null;
      } else {
         Player closestEscaper = null;
         double minDistance = Double.MAX_VALUE;
         Location endCenter = new Location(endWorld, (double)100.0F, (double)70.0F, (double)0.0F);

         for(Player escaper : this.plugin.getEscapers()) {
            if (escaper != null && escaper.isOnline() && escaper.getWorld().getEnvironment() == Environment.THE_END) {
               double distance = escaper.getLocation().distance(endCenter);
               if (distance < minDistance) {
                  minDistance = distance;
                  closestEscaper = escaper;
               }
            }
         }

         return closestEscaper != null ? closestEscaper.getLocation() : null;
      }
   }

   private Location findSafeLocationInEnd(World endWorld, Location center, int radius) {
      Random random = ThreadLocalRandom.current();
      int maxAttempts = 50;

      for(int attempt = 0; attempt < maxAttempts; ++attempt) {
         int offsetX = random.nextInt(radius * 2) - radius;
         int offsetZ = random.nextInt(radius * 2) - radius;
         int x = center.getBlockX() + offsetX;
         int z = center.getBlockZ() + offsetZ;

         for(int y = 120; y > 0; --y) {
            Location testLoc = new Location(endWorld, (double)x + (double)0.5F, (double)y, (double)z + (double)0.5F);
            if (this.isSafeLocation(testLoc)) {
               return testLoc;
            }
         }
      }

      Location fallback = new Location(endWorld, (double)100.5F, (double)70.0F, (double)0.5F);

      for(int y = 70; y < 120; ++y) {
         fallback.setY((double)y);
         if (this.isSafeLocation(fallback)) {
            return fallback;
         }
      }

      return new Location(endWorld, (double)100.5F, (double)80.0F, (double)0.5F);
   }

   private boolean isSafeLocation(Location loc) {
      if (loc.getWorld() == null) {
         return false;
      } else {
         Block feet = loc.getBlock();
         Block head = loc.clone().add((double)0.0F, (double)1.0F, (double)0.0F).getBlock();
         Block below = loc.clone().add((double)0.0F, (double)-1.0F, (double)0.0F).getBlock();
         return below.getType().isSolid() && !feet.getType().isSolid() && !head.getType().isSolid() && !feet.isLiquid() && !head.isLiquid() && loc.getY() > (double)0.0F;
      }
   }

   public boolean isPlayerRespawning(UUID playerId) {
      return this.respawnTimers.containsKey(playerId);
   }

   public VoteSystem getVoteSystem() {
      return this.voteSystem;
   }

}
