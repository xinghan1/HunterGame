package com.huntergame.listener;

import com.huntergame.HunterGame;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class InactivityMonitor {
   private final HunterGame plugin;
   private final Map<UUID, Long> lastActivityTime = new HashMap();
   private long inactivityKickTime;

   public InactivityMonitor(HunterGame plugin, long inactivityKickTime) {
      this.plugin = plugin;
      this.inactivityKickTime = inactivityKickTime;
      this.startInactivityCheckTask();
   }

   private void startInactivityCheckTask() {
      (new BukkitRunnable() {
         public void run() {
            if (InactivityMonitor.this.plugin.isGameRunning()) {
               long currentTime = System.currentTimeMillis();

               for(Player player : Bukkit.getOnlinePlayers()) {
                  UUID playerId = player.getUniqueId();
                  if (InactivityMonitor.this.lastActivityTime.containsKey(playerId)) {
                     long inactiveTime = currentTime - (Long)InactivityMonitor.this.lastActivityTime.get(playerId);
                     if (inactiveTime >= InactivityMonitor.this.inactivityKickTime) {
                        InactivityMonitor.this.kickForInactivity(player);
                     }
                  }
               }

            }
         }
      }).runTaskTimer(this.plugin, 0L, 1200L);
   }

   private void kickForInactivity(Player player) {
      player.kickPlayer(this.plugin.getMessage("inactivity_kick_message", "&c\u4f60\u56e0\u957f\u65f6\u95f4\u65e0\u64cd\u4f5c\u88ab\u8e22\u51fa\u6e38\u620f\uff01"));
      this.lastActivityTime.remove(player.getUniqueId());
      this.plugin.getLogger().info("\u73a9\u5bb6 " + player.getName() + " \u56e0\u957f\u65f6\u95f4\u65e0\u64cd\u4f5c\u88ab\u8e22\u51fa\u6e38\u620f\u3002");
   }

   public void updateActivity(Player player) {
      this.lastActivityTime.put(player.getUniqueId(), System.currentTimeMillis());
   }
}
