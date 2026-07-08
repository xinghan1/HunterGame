package com.huntergame.listener;

import com.huntergame.HunterGame;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class DragonFightListener implements Listener {
   private final HunterGame plugin;

   public DragonFightListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onEnderDragonDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof EnderDragon && event.getDamager() instanceof Player) {
         Player attacker = (Player)event.getDamager();
         if (this.plugin.isHunter(attacker.getUniqueId())) {
            event.setDamage((double)0.0F);
         }

      }
   }

   @EventHandler
   public void onEnderDragonDeath(EntityDeathEvent event) {
      if (event.getEntity() instanceof EnderDragon) {
         if (this.plugin.beginSettlement()) {
            Bukkit.broadcastMessage(this.plugin.getMessage("escaper_win", "&c\u672b\u5f71\u9f99\u5df2\u88ab\u9003\u751f\u8005\u51fb\u8d25\uff01\u9003\u751f\u8005\u80dc\u5229\uff01"));

            for(Player player : Bukkit.getOnlinePlayers()) {
               UUID uuid = player.getUniqueId();
               if (!this.plugin.getGameRewardService().hasSettlementReward(uuid)) {
                  if (this.plugin.getEscapers().contains(player) || this.plugin.isDeathescapers(uuid)) {
                     this.plugin.getDataStorageManager().addEscapeWin(uuid, player);
                     this.plugin.getGameRewardService().giveEscaperReward(player);
                  }

                  this.plugin.getDataStorageManager().saveTotalWins(uuid, player);
                  if (this.plugin.isHunter(uuid)) {
                     player.sendTitle(this.plugin.getMessage("hunters_defeat_title_2", "&c\u4f60\u5931\u8d25\u4e86\uff01"), this.plugin.getMessage("hunters_defeat_subtitle_2", "&f\u730e\u4eba\u672a\u80fd\u963b\u6b62\u9003\u751f\u8005..."), 10, 100, 20);
                     this.plugin.getGameRewardService().giveHunterFailReward(player);
                  } else if (this.plugin.isEscaper(uuid)) {
                     player.sendTitle(this.plugin.getMessage("escapers_victory_title_2", "&a\u606d\u559c\u4f60\uff01"), this.plugin.getMessage("escapers_victory_subtitle_2", "&f\u6210\u529f\u9003\u8131\u730e\u4eba\u7684\u8ffd\u6740"), 10, 100, 20);
                  } else {
                     player.sendTitle(this.plugin.getMessage("watch_victory_title_2", "&a\u6e38\u620f\u7ed3\u675f\uff01"), this.plugin.getMessage("watch_victory_subtitle_2", "&f\u9003\u751f\u8005\u83b7\u5f97\u4e86\u80dc\u5229\uff01"), 10, 100, 20);
                  }
               }
            }

            this.plugin.resetGame();
         }
      }
   }

   @EventHandler
   public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player) {
         Player victim = (Player)event.getEntity();
         Player attacker = this.getPlayerDamager(event);
         if (attacker != null && !attacker.equals(victim)) {
            if (!this.plugin.glassCageManager.isInCage(attacker) && !this.plugin.glassCageManager.isInCage(victim)) {
               UUID attackerId = attacker.getUniqueId();
               UUID victimId = victim.getUniqueId();
               boolean sameHunterTeam = this.plugin.isHunter(attackerId) && this.plugin.isHunter(victimId);
               boolean sameEscaperTeam = this.plugin.isEscaper(attackerId) && this.plugin.isEscaper(victimId);
               if (sameHunterTeam || sameEscaperTeam) {
                  event.setCancelled(true);
                  attacker.sendMessage(this.plugin.getMessage("team-damage", "&c\u4f60\u4e0d\u80fd\u653b\u51fb\u4f60\u7684\u961f\u53cb\uff01"));
               }

            } else {
               event.setCancelled(true);
            }
         }
      }
   }

   private Player getPlayerDamager(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player) {
         return (Player)event.getDamager();
      } else {
         if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile)event.getDamager();
            if (projectile.getShooter() instanceof Player) {
               return (Player)projectile.getShooter();
            }
         }

         return null;
      }
   }
}
