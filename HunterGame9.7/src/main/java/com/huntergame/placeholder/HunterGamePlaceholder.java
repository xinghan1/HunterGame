package com.huntergame.placeholder;

import com.huntergame.HunterGame;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public class HunterGamePlaceholder extends PlaceholderExpansion implements Listener {
   private final HunterGame plugin;
   private static Location bastionLocation = null;
   private static Location fortressLocation = null;
   private final Map<UUID, Integer> tierCache = new ConcurrentHashMap();
   private boolean bastionSearchRunning = false;
   private boolean fortressSearchRunning = false;

   public HunterGamePlaceholder(HunterGame plugin) {
      this.plugin = plugin;
      this.scheduleWeeklyTierRefresh();
   }

   private void scheduleWeeklyTierRefresh() {
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime nextMonday4am = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(4).withMinute(0).withSecond(0).withNano(0);
      if (now.getDayOfWeek() == DayOfWeek.MONDAY && now.getHour() < 4) {
         nextMonday4am = now.withHour(4).withMinute(0).withSecond(0).withNano(0);
      }

      long delaySeconds = Duration.between(now, nextMonday4am).getSeconds();
      long delayTicks = delaySeconds * 20L;
      long oneWeekTicks = 12096000L;
      Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, this::refreshAllTiers, delayTicks, oneWeekTicks);
   }

   public void refreshAllTiers() {
      Map<UUID, Integer> allTiers = this.plugin.getDataStorageManager().getAllPlayerTiers();
      this.tierCache.clear();
      this.tierCache.putAll(allTiers);
      this.plugin.getLogger().info("\u730e\u4eba\u6e38\u620f\u5168\u670d\u6392\u540d\u5df2\u5237\u65b0\u3002");
   }

   public @NotNull String getIdentifier() {
      return "huntergame";
   }

   public @NotNull String getAuthor() {
      return "\u4f60\u7684\u540d\u5b57";
   }

   public @NotNull String getVersion() {
      return "1.0";
   }

   public boolean persist() {
      return true;
   }

   public boolean canRegister() {
      return true;
   }

   public String onPlaceholderRequest(Player player, @NotNull String params) {
      if (player == null) {
         return null;
      } else {
         UUID playerId = player.getUniqueId();
         if (params.equalsIgnoreCase("role")) {
            if (this.plugin.isHunter(playerId)) {
               return this.plugin.getMessage("placeholder_role_hunter", "&c\u730e\u4eba");
            } else if (this.plugin.isEscaper(playerId)) {
               return this.plugin.getMessage("placeholder_role_escaper", "&b\u9003\u751f\u8005");
            } else {
               return this.plugin.isDeathescapers(playerId) ? this.plugin.getMessage("placeholder_role_dead_escaper", "&b\u9003\u751f\u8005 \u6b7b\u4ea1") : this.plugin.getMessage("placeholder_role_unassigned", "&7\u672a\u5206\u914d");
            }
         } else if (params.equalsIgnoreCase("mode")) {
            return this.plugin.getMessage("placeholder_mode_final_battle", "&c终章");
         } else if (params.equalsIgnoreCase("hunter_count")) {
            return String.valueOf(this.plugin.getHunters().size());
         } else if (params.equalsIgnoreCase("escaper_count")) {
            return String.valueOf(this.plugin.getEscapers().size());
         } else if (params.equalsIgnoreCase("kills")) {
            return String.valueOf(this.plugin.getDataStorageManager().getKills(player.getUniqueId()));
         } else if (params.equalsIgnoreCase("kills_put")) {
            return String.valueOf(this.plugin.getDataStorageManager().getKillsput(player.getUniqueId()));
         } else if (params.equalsIgnoreCase("deaths")) {
            return String.valueOf(this.plugin.getDataStorageManager().getDeaths(player.getUniqueId()));
         } else if (params.equalsIgnoreCase("games_played")) {
            return String.valueOf(this.plugin.getDataStorageManager().getGamesPlayed(player.getUniqueId()));
         } else if (params.equalsIgnoreCase("hunter_wins")) {
            return String.valueOf(this.plugin.getDataStorageManager().getHunterWin(player.getUniqueId()));
         } else if (params.equalsIgnoreCase("escape_wins")) {
            return String.valueOf(this.plugin.getDataStorageManager().getEscapeWin(player.getUniqueId()));
         } else if (params.equalsIgnoreCase("total_wins")) {
            return String.valueOf(this.plugin.getDataStorageManager().getTotalWins(player.getUniqueId()));
         } else if (params.equalsIgnoreCase("gametime")) {
            return this.plugin.getFormattedGameTime();
         } else if (params.equalsIgnoreCase("proficiency")) {
            return String.valueOf(this.plugin.getDataStorageManager().getProficiency(player));
         } else if (params.equalsIgnoreCase("rank")) {
            double proficiency = this.plugin.getDataStorageManager().getProficiency(player);
            return this.plugin.getRankManager().getRankName(proficiency);
         } else if (params.equalsIgnoreCase("fortress")) {
            return fortressLocation != null ? formatLocation(fortressLocation) : "\u672a\u627e\u5230";
         } else if (params.equalsIgnoreCase("bastion")) {
            return bastionLocation != null ? formatLocation(bastionLocation) : "\u672a\u627e\u5230";
         } else if (params.equalsIgnoreCase("portal")) {
            return this.plugin.getPortalCoordinatesPlaceholder(player);
         } else if (params.equalsIgnoreCase("season")) {
            return String.valueOf(this.plugin.getSeasonManager().getCurrentSeasonId());
         } else if (params.equalsIgnoreCase("tier")) {
            Integer tier = (Integer)this.tierCache.get(player.getUniqueId());
            return tier != null ? String.valueOf(tier) : "\u6682\u65e0\u6570\u636e";
         } else {
            return null;
         }
      }
   }

   @EventHandler
   public void onPlayerEnterNether(PlayerPortalEvent event) {
   }

   private void startBastionSearchTask(final Player player, final World nether) {
      if (bastionLocation == null && !this.bastionSearchRunning) {
         this.bastionSearchRunning = true;
         (new BukkitRunnable() {
            private int attempts = 0;

            public void run() {
               if (HunterGamePlaceholder.bastionLocation == null && this.attempts++ < 12) {
                  Location bastion = nether.locateNearestStructure(player.getLocation(), StructureType.BASTION_REMNANT, 200, false);
                  if (bastion != null) {
                     HunterGamePlaceholder.bastionLocation = bastion;
                     HunterGamePlaceholder.this.bastionSearchRunning = false;
                     this.cancel();
                  }

               } else {
                  HunterGamePlaceholder.this.bastionSearchRunning = false;
                  this.cancel();
               }
            }
         }).runTaskTimer(this.plugin, 0L, 300L);
      }
   }

   private void startFortressSearchTask(final Player player, final World nether) {
      if (fortressLocation == null && !this.fortressSearchRunning) {
         this.fortressSearchRunning = true;
         (new BukkitRunnable() {
            private int attempts = 0;

            public void run() {
               if (HunterGamePlaceholder.fortressLocation == null && this.attempts++ < 12) {
                  Location fortress = nether.locateNearestStructure(player.getLocation(), StructureType.NETHER_FORTRESS, 200, false);
                  if (fortress != null) {
                     HunterGamePlaceholder.fortressLocation = fortress;
                     HunterGamePlaceholder.this.fortressSearchRunning = false;
                     this.cancel();
                  }

               } else {
                  HunterGamePlaceholder.this.fortressSearchRunning = false;
                  this.cancel();
               }
            }
         }).runTaskTimer(this.plugin, 0L, 300L);
      }
   }

   static String formatLocation(Location location) {
      return String.format("%d,%d,%d", location.getBlockX(), location.getBlockY(), location.getBlockZ());
   }
}
