package com.huntergame.listener;

import com.huntergame.HunterGame;
import com.huntergame.combat.LastDamageTracker;
import com.huntergame.role.RoleSelectionHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public class DeathMessageListener implements Listener {
   private final HunterGame plugin;
   private final Random rand = new Random();

   public DeathMessageListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      String deathMessage = this.resolveRole(player) == DeathMessageListener.PlayerRole.ESCAPER ? this.escaperDeathMessage(player) : this.hunterDeathMessage(player);
      event.deathMessage((Component)null);
      Bukkit.broadcastMessage(deathMessage);
   }

   private PlayerRole resolveRole(Player player) {
      UUID playerId = player.getUniqueId();
      boolean hunter = this.plugin.isHunter(playerId);
      boolean escaper = this.plugin.isEscaper(playerId) || this.plugin.isDeathescapers(playerId);
      if (hunter) {
         return DeathMessageListener.PlayerRole.HUNTER;
      } else if (escaper) {
         return DeathMessageListener.PlayerRole.ESCAPER;
      } else {
         PersistentDataContainer container = player.getPersistentDataContainer();
         boolean hunterTag = Boolean.TRUE.equals(container.get(RoleSelectionHandler.IS_HUNTER, PersistentDataType.BOOLEAN));
         boolean escaperTag = Boolean.TRUE.equals(container.get(RoleSelectionHandler.IS_ESCAPER, PersistentDataType.BOOLEAN));
         if (hunterTag) {
            return DeathMessageListener.PlayerRole.HUNTER;
         } else {
            return escaperTag ? DeathMessageListener.PlayerRole.ESCAPER : DeathMessageListener.PlayerRole.HUNTER;
         }
      }
   }

   private String hunterDeathMessage(Player player) {
      return this.deathMessage(player, DeathMessageListener.PlayerRole.HUNTER);
   }

   private String escaperDeathMessage(Player player) {
      return this.deathMessage(player, DeathMessageListener.PlayerRole.ESCAPER);
   }

   private String deathMessage(Player player, PlayerRole role) {
      String roleKey = role.configKey;
      String prefix = this.getPrefix(role);
      String killerPrefix = this.getKillerPrefix(role);
      LastDamageTracker.DamageCredit creditedKiller = this.getNonPlayerDeathCredit(player);
      if (creditedKiller != null) {
         return this.randomMessage("death." + roleKey + ".credited_player", this.creditedPlayerDefaults(), player, prefix, killerPrefix, creditedKiller.playerName(), 0, 0);
      } else {
         EntityDamageEvent damageEvent = player.getLastDamageCause();
         if (damageEvent == null) {
            String fallback = role == DeathMessageListener.PlayerRole.HUNTER ? "%prefix%%victim% &7\u5728\u72e9\u730e\u4e2d\u795e\u79d8\u5931\u8e2a" : "%prefix%%victim% &7\u6b7b\u7fd8\u7fd8\u4e86";
            return this.message("death." + roleKey + ".no_cause", fallback, player, prefix, killerPrefix, "", 0, 0);
         } else {
            Entity damager = this.resolveDamager(damageEvent);
            EntityDamageEvent.DamageCause cause = damageEvent.getCause();
            switch (cause) {
               case ENTITY_ATTACK:
                  return this.entityAttackMessage(player, role, prefix, killerPrefix, damager);
               case ENTITY_EXPLOSION:
                  return this.explosionMessage(player, prefix, killerPrefix, damager);
               case PROJECTILE:
                  return this.projectileMessage(player, role, prefix, killerPrefix, damager);
               case FALL:
                  return this.message("death.common.fall", "%prefix%%victim% &7\u4ece &6%height%\u683c &7\u9ad8\u7a7a\u6454\u6210\u8089\u997c", player, prefix, killerPrefix, "", 0, (int)player.getFallDistance());
               case VOID:
                  return this.message("death.common.void", "%prefix%%victim% &7\u5760\u5165\u4e86 &8\u865a\u7a7a &7\uff0c\u88ab\u9ed1\u6697\u6c38\u8fdc\u541e\u566c", player, prefix, killerPrefix, "", 0, 0);
               case DROWNING:
                  return this.message("death.common.drowning", "%prefix%%victim% &7\u8bd5\u56fe &3\u6f5c\u6c34\u9003\u8131 &7\u5374\u6ca1\u80fd\u6d6e\u51fa\u6c34\u9762", player, prefix, killerPrefix, "", 0, 0);
               case FIRE:
                  return this.message("death.common.fire", "%prefix%%victim% &7\u88ab &c\u706b\u7130 &7\u541e\u566c\u6210\u7070", player, prefix, killerPrefix, "", 0, 0);
               case LAVA:
                  return this.message("death.common.lava", "%prefix%%victim% &7\u6311\u6218 &6\u5ca9\u6d46\u6d17\u793c &7\u5931\u8d25", player, prefix, killerPrefix, "", 0, 0);
               case SUFFOCATION:
                  return this.message("death.common.suffocation", "%prefix%%victim% &7\u88ab &8\u5899\u4f53 &7\u7f13\u6162\u78be\u788e", player, prefix, killerPrefix, "", 0, 0);
               case POISON:
                  return this.message("death.common.poison", "%prefix%%victim% &7\u88ab &2\u5267\u6bd2 &7\u6298\u78e8\u81f3\u6b7b", player, prefix, killerPrefix, "", 0, 0);
               case HOT_FLOOR:
                  if (damager instanceof Blaze) {
                     return this.message("death.common.blaze", "%prefix%%victim% &7\u88ab &6\u70c8\u7130\u4eba &7\u7684\u706b\u7130\u98ce\u66b4\u541e\u566c", player, prefix, killerPrefix, "", 0, 0);
                  }

                  return this.message("death.common.hot_floor", "%prefix%%victim% &7\u5728 &c\u70c8\u7130 &7\u4e2d\u6d85\u69c3\u5931\u8d25", player, prefix, killerPrefix, "", 0, 0);
               default:
                  return this.message("death.common.default", "%prefix%%victim% &7\u6b7b\u7fd8\u7fd8\u4e86", player, prefix, killerPrefix, "", 0, 0);
            }
         }
      }
   }

   private String entityAttackMessage(Player player, PlayerRole role, String prefix, String killerPrefix, Entity damager) {
      if (damager instanceof Player killer) {
         return this.randomMessage("death." + role.configKey + ".player_melee", this.playerMeleeDefaults(role), player, prefix, killerPrefix, killer.getName(), 0, 0);
      } else if (damager instanceof Enderman) {
         return this.message("death.common.enderman", "%prefix%%victim% &7\u7684\u88c5\u5907\u88ab &5\u672b\u5f71\u4eba &7\u7f34\u68b0", player, prefix, killerPrefix, "", 0, 0);
      } else if (!(damager instanceof Spider) && !(damager instanceof CaveSpider)) {
         if (damager instanceof WitherSkeleton) {
            return this.message("death.common.wither_skeleton", "%prefix%%victim% &7\u7684\u62a4\u7532\u88ab &7\u51cb\u96f6\u9ab7\u9ac5 &7\u8150\u8680\u51fb\u7834", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof Zombie) {
            return this.message("death.common.zombie", "%prefix%%victim% &7\u88ab &7\u50f5\u5c38 &7\u7206\u64cd\u4e86", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof IronGolem) {
            return this.message("death.common.iron_golem", "%prefix%%victim% &7\u88ab &f\u94c1\u5080\u5121 &7\u51fb\u98de\u81f4\u6b7b\u4e86", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof PigZombie) {
            return this.message("death.common.pig_zombie", "%prefix%%victim% &7\u88ab &6\u50f5\u5c38\u732a\u4eba &7\u51fb\u6740\u4e86", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof MagmaCube) {
            return this.message("death.common.magma_cube", "%prefix%%victim% &7\u88ab &6\u5ca9\u6d46\u602a &7\u538b\u6210\u4e86\u8089\u997c", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof Vindicator) {
            return this.message("death.common.vindicator", "%prefix%%victim% &7\u88ab &6\u536b\u9053\u58eb &7\u780d\u6210\u4e94\u9a6c\u5206\u5c38", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof PiglinBrute) {
            return this.message("death.common.piglin_brute", "%prefix%%victim% &7\u88ab &6\u732a\u7075\u86ee\u5175 &7\u780d\u6210\u4e94\u9a6c\u5206\u5c38", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof Wolf) {
            return this.message("death.common.wolf", "%prefix%%victim% &7\u88ab &6\u72fc &7\u54ac\u6b7b\u4e86", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof Drowned) {
            return this.message("death.common.drowned", "%prefix%%victim% &7\u88ab &3\u6eba\u5c38 &7\u62d6\u5165\u4e86\u6c34\u4e0b\u6df1\u6e0a", player, prefix, killerPrefix, "", 0, 0);
         } else if (damager instanceof Husk) {
            return this.message("death.common.husk", "%prefix%%victim% &7\u88ab &6\u5c38\u58f3 &7\u541e\u566c\u5728\u6c99\u6f20\u98ce\u66b4\u4e2d", player, prefix, killerPrefix, "", 0, 0);
         } else {
            return damager instanceof Warden ? this.message("death.common.warden", "%prefix%%victim% &7\u88ab &1\u5b88\u536b\u8005 &7\u7684\u6124\u6012\u6495\u6210\u788e\u7247", player, prefix, killerPrefix, "", 0, 0) : this.message("death.common.entity_attack", "%prefix%%victim% &7\u5728\u8fd1\u6218\u4ea4\u950b\u4e2d\u9668\u843d", player, prefix, killerPrefix, "", 0, 0);
         }
      } else {
         return this.message("death.common.spider", "%prefix%%victim% &7\u7684\u9677\u9631\u88ab &7\u6bd2\u86db &7\u53cd\u5236", player, prefix, killerPrefix, "", 0, 0);
      }
   }

   private String explosionMessage(Player player, String prefix, String killerPrefix, Entity damager) {
      if (damager instanceof Wither) {
         return this.message("death.common.wither_explosion", "%prefix%%victim% &7\u88ab &8\u51cb\u96f6 &7\u7684\u6bc1\u706d\u51b2\u51fb\u6ce2\u7c89\u788e", player, prefix, killerPrefix, "", 0, 0);
      } else {
         return damager instanceof Creeper ? this.message("death.common.creeper", "%prefix%%victim% &7\u88ab &a\u82e6\u529b\u6015 &7\u7684\u7231\u5fc3\u7206\u70b8\u878d\u5316", player, prefix, killerPrefix, "", 0, 0) : this.message("death.common.explosion", "%prefix%%victim% &7\u88ab\u7206\u70b8\u70b8\u5f97\u56db\u5206\u4e94\u88c2", player, prefix, killerPrefix, "", 0, 0);
      }
   }

   private String projectileMessage(Player player, PlayerRole role, String prefix, String killerPrefix, Entity damager) {
      if (damager instanceof Player shooter) {
         int roundedDistance = (int)Math.round(shooter.getLocation().distance(player.getLocation()));
         return this.message("death." + role.configKey + ".player_projectile", "%prefix%%victim% &7\u88ab %killer_prefix%%killer% \u7cbe\u51c6\u547d\u4e2d &8(&f%distance%\u7c73&8)", player, prefix, killerPrefix, shooter.getName(), roundedDistance, 0);
      } else if (damager instanceof Skeleton) {
         return this.message("death.common.skeleton", "%prefix%%victim% &7\u88ab &8Donk\u9ab7\u9ac5\u5f13\u624b &7\u9897\u79d2\u4e86", player, prefix, killerPrefix, "", 0, 0);
      } else if (damager instanceof Pillager) {
         return this.message("death.common.pillager", "%prefix%%victim% &7\u88ab &8\u63a0\u593a\u8005 &7\u7684\u5f29\u7bad\u9489\u5728\u5899\u4e0a", player, prefix, killerPrefix, "", 0, 0);
      } else if (damager instanceof Piglin) {
         return this.message("death.common.piglin", "%prefix%%victim% &7\u88ab &6\u732a\u7075 &7\u7684\u5f29\u7bad\u9489\u5728\u5899\u4e0a", player, prefix, killerPrefix, "", 0, 0);
      } else {
         return damager instanceof Blaze ? this.message("death.common.blaze", "%prefix%%victim% &7\u88ab &6\u70c8\u7130\u4eba &7\u7684\u706b\u7130\u98ce\u66b4\u541e\u566c", player, prefix, killerPrefix, "", 0, 0) : this.message("death.common.projectile", "%prefix%%victim% &7\u88ab\u8fdc\u7a0b\u6b66\u5668\u730e\u6740", player, prefix, killerPrefix, "", 0, 0);
      }
   }

   private LastDamageTracker.DamageCredit getNonPlayerDeathCredit(Player player) {
      LastDamageTracker.DamageCredit credit = this.plugin.getLastDamageTracker().getCreditedKiller(player);
      if (credit == null) {
         return null;
      } else {
         EntityDamageEvent damageEvent = player.getLastDamageCause();
         if (damageEvent instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent)damageEvent;
            Player damager = this.plugin.getLastDamageTracker().getPlayerDamager(entityEvent.getDamager());
            if (damager != null && damager.getUniqueId().equals(credit.playerId())) {
               return null;
            }
         }

         return credit;
      }
   }

   private Entity resolveDamager(EntityDamageEvent damageEvent) {
      if (damageEvent instanceof EntityDamageByEntityEvent entityEvent) {
         Entity originalDamager = entityEvent.getDamager();
         if (originalDamager instanceof Projectile projectile) {
            ProjectileSource var6 = projectile.getShooter();
            if (var6 instanceof Entity shooter) {
               return shooter;
            }
         }

         return originalDamager;
      } else {
         return null;
      }
   }

   private String randomMessage(String key, List<String> defaults, Player victim, String prefix, String killerPrefix, String killer, int distance, int height) {
      List<String> messages = this.plugin.getMessageList(key, defaults);
      String template = messages.isEmpty() ? "" : (String)messages.get(this.rand.nextInt(messages.size()));
      return this.applyPlaceholders(template, victim, prefix, killerPrefix, killer, distance, height);
   }

   private String message(String key, String fallback, Player victim, String prefix, String killerPrefix, String killer, int distance, int height) {
      return this.applyPlaceholders(this.plugin.getMessage(key, fallback), victim, prefix, killerPrefix, killer, distance, height);
   }

   private String applyPlaceholders(String template, Player victim, String prefix, String killerPrefix, String killer, int distance, int height) {
      return template.replace("%prefix%", prefix).replace("%victim%", victim.getName()).replace("%killer_prefix%", killerPrefix).replace("%killer%", killer == null ? "" : killer).replace("%distance%", String.valueOf(distance)).replace("%height%", String.valueOf(height));
   }

   private String getPrefix(PlayerRole role) {
      String var10001 = role.configKey;
      return this.plugin.getMessage("death." + var10001 + ".prefix", role == DeathMessageListener.PlayerRole.HUNTER ? "&f[\u2620] &c\ud83c\udff9" : "&f[\u2620] &b\u2694");
   }

   private String getKillerPrefix(PlayerRole victimRole) {
      String var10001 = victimRole.configKey;
      return this.plugin.getMessage("death." + var10001 + ".killer_prefix", victimRole == DeathMessageListener.PlayerRole.HUNTER ? "&b\u2694" : "&c\ud83c\udff9");
   }

   private List<String> playerMeleeDefaults(PlayerRole role) {
      return role == DeathMessageListener.PlayerRole.HUNTER ? Arrays.asList("%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u8fd1\u6218\u51fb\u8d25", "%prefix%%victim% &7\u4e0e %killer_prefix%%killer% &7\u8089\u640f\u540e\u843d\u8d25", "%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u6253\u6210\u91cd\u4f24\u5012\u5730", "%prefix%%victim% &7\u7684\u9632\u7ebf\u88ab %killer_prefix%%killer% &7\u5f7b\u5e95\u51fb\u7834", "%prefix%%victim% &7\u5728\u4e0e %killer_prefix%%killer% &7\u7684\u6b7b\u6597\u4e2d\u6218\u8d25", "%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u7ed9\u560e\u6389\u4e86") : Arrays.asList("%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u7ec8\u7ed3\u4e86", "%prefix%%victim% &7\u4e0e %killer_prefix%%killer% &7\u8089\u640f\u540e\u843d\u8d25", "%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u6253\u6210\u91cd\u4f24\u5012\u5730", "%prefix%%victim% &7\u7684\u9632\u7ebf\u88ab %killer_prefix%%killer% &7\u5f7b\u5e95\u51fb\u7834", "%prefix%%victim% &7\u5728\u4e0e %killer_prefix%%killer% &7\u7684\u6b7b\u6597\u4e2d\u6218\u8d25", "%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u7ed9\u560e\u6389\u4e86");
   }

   private List<String> creditedPlayerDefaults() {
      return Arrays.asList("%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u51fb\u8d25", "%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u6253\u5165\u7edd\u5883\u540e\u6b7b\u4ea1", "%prefix%%victim% &7\u6ca1\u80fd\u9003\u51fa %killer_prefix%%killer% &7\u7684\u8ffd\u6740", "%prefix%%victim% &7\u88ab %killer_prefix%%killer% &7\u7ec8\u7ed3");
   }

   private static enum PlayerRole {
      HUNTER("hunter"),
      ESCAPER("escaper");

      private final String configKey;

      private PlayerRole(String configKey) {
         this.configKey = configKey;
      }

      // $FF: synthetic method
      private static PlayerRole[] $values() {
         return new PlayerRole[]{HUNTER, ESCAPER};
      }
   }
}
