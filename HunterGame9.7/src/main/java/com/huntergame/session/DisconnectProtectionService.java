package com.huntergame.session;

import com.huntergame.HunterGame;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DisconnectProtectionService implements Listener {
   private final HunterGame plugin;
   private final Map<UUID, Location> lastLocations = new ConcurrentHashMap();
   final Map<UUID, Boolean> playerRoles = new ConcurrentHashMap();
   private final Map<UUID, Long> playerDisconnectTime = new ConcurrentHashMap();
   private int cleanupTaskId = -1;
   private static final long CLEANUP_DELAY_MILLIS = 180000L;

   public DisconnectProtectionService(HunterGame plugin) {
      this.plugin = plugin;
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      this.startCleanupTask();
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      if (!this.plugin.isGameRunning()) {
         this.cleanupPlayerData(event.getPlayer().getUniqueId());
      } else {
         Player player = event.getPlayer();
         UUID playerId = player.getUniqueId();
         if (player.getGameMode() != GameMode.SPECTATOR) {
            boolean isEscaper = this.plugin.isEscaper(playerId);
            this.playerRoles.put(playerId, isEscaper);
            this.lastLocations.put(playerId, player.getLocation().clone());
            this.playerDisconnectTime.put(playerId, System.currentTimeMillis());
            Logger var10000 = this.plugin.getLogger();
            String var10001 = player.getName();
            var10000.info("\u73a9\u5bb6 " + var10001 + " \u5df2\u79bb\u7ebf\uff0c\u89d2\u8272 (" + (isEscaper ? "\u9003\u751f\u8005" : "\u730e\u4eba") + ") \u5df2\u8bb0\u5f55");
         }
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID uuid = player.getUniqueId();
      if (!this.plugin.isGameRunning()) {
         this.cleanupPlayerData(uuid);
      } else {
         if (this.playerRoles.containsKey(uuid)) {
            Boolean wasEscaper = (Boolean)this.playerRoles.get(uuid);
            if (wasEscaper == null) {
               this.plugin.getLogger().warning("\u73a9\u5bb6 " + player.getName() + " \u7684\u89d2\u8272\u8bb0\u5f55\u5f02\u5e38\uff01");
               this.cleanupPlayerData(uuid);
               player.sendMessage(this.plugin.getMessage("restore_failed", "\u79bb\u7ebf\u6570\u636e\u5f02\u5e38\uff0c\u672a\u80fd\u6062\u590d\u89d2\u8272"));
               return;
            }

            if (wasEscaper) {
               this.plugin.addEscaper(uuid);
               player.sendMessage(this.plugin.getMessage("recover_escape", "&a\u6b22\u8fce\u56de\u6765\uff01\u5df2\u6062\u590d\u9003\u751f\u8005\u89d2\u8272"));
            } else {
               this.plugin.addHunter(uuid);
               player.sendMessage(this.plugin.getMessage("recover_hunter", "&a\u6b22\u8fce\u56de\u6765\uff01\u5df2\u6062\u590d\u730e\u4eba\u89d2\u8272"));
            }

            Location loc = (Location)this.lastLocations.get(uuid);
            if (loc != null) {
               player.teleport(loc);
            }

            this.plugin.getLogger().info("\u73a9\u5bb6 " + player.getName() + " \u5df2\u6062\u590d\u79bb\u7ebf\u6570\u636e");
         }

      }
   }

   public void cleanupPlayerData(UUID uuid) {
      this.plugin.getSkillManager().escapeeSkills.remove(uuid);
      this.plugin.getSkillManager().hunterSkills.remove(uuid);
      this.clearOfflineProtectionData(uuid);
   }

   public void clearOfflineProtectionData(UUID uuid) {
      this.playerRoles.remove(uuid);
      this.lastLocations.remove(uuid);
      this.playerDisconnectTime.remove(uuid);
   }

   private void startCleanupTask() {
      if (this.cleanupTaskId != -1) {
         Bukkit.getScheduler().cancelTask(this.cleanupTaskId);
      }

      this.cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
         long currentTime = System.currentTimeMillis();
         Iterator<Map.Entry<UUID, Long>> iterator = this.playerDisconnectTime.entrySet().iterator();

         while(iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = (Map.Entry)iterator.next();
            UUID uuid = (UUID)entry.getKey();
            long disconnectTime = (Long)entry.getValue();
            if (currentTime - disconnectTime > 180000L) {
               String playerName = uuid.toString();
               this.plugin.getLogger().info("\u73a9\u5bb6 " + playerName + " \u79bb\u7ebf\u8d85\u8fc73\u5206\u949f\uff0c\u6e05\u7406\u6570\u636e");
               this.cleanupPlayerData(uuid);
               iterator.remove();
            }
         }

      }, 20L, 20L);
   }

   public void cleanup() {
      if (this.cleanupTaskId != -1) {
         Bukkit.getScheduler().cancelTask(this.cleanupTaskId);
         this.cleanupTaskId = -1;
      }

      this.clearAllData();
   }

   public void clearAllData() {
      this.lastLocations.clear();
      this.playerRoles.clear();
      this.playerDisconnectTime.clear();
   }

   public boolean hasOfflineProtectionData(UUID playerId) {
      return this.playerRoles.containsKey(playerId);
   }
}
