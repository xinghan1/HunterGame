package com.huntergame.skill.skills;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class AdrenalineBurstSkill implements HunterSkill {
   private static final String NAME = "\u80be\u4e0a\u817a\u7206\u53d1";
   private final Map<UUID, Integer> speedTasks = new ConcurrentHashMap();

   public String getName() {
      return "\u80be\u4e0a\u817a\u7206\u53d1";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      UUID uuid = player.getUniqueId();
      this.cancelSpeedTask(uuid);
      int effectSeconds = context.duration("\u80be\u4e0a\u817a\u7206\u53d1");
      if (context.plugin().isEscaper(uuid)) {
         effectSeconds += context.intParam("\u80be\u4e0a\u817a\u7206\u53d1", "escaper_extra_seconds", 5);
      }

      int effectTicks = effectSeconds * 20;
      player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, effectTicks, context.intParam("\u80be\u4e0a\u817a\u7206\u53d1", "speed_amplifier", 4), true, true));
      player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 30, (double)0.5F, (double)0.5F, (double)0.5F, 0.2);
      player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0F, 0.5F);
      player.sendTitle(context.message("skill_adrenaline_title", "&b\u26a1 &l\u80be\u4e0a\u817a\u7206\u53d1&r\u26a1"), context.message("skill_duration_subtitle", "&7\u6301\u7eed\u65f6\u95f4: &a%seconds%\u79d2", "%seconds%", effectSeconds), 10, 60, 10);
      int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(context.plugin(), () -> {
         player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add((double)0.0F, 0.2, (double)0.0F), 5, 0.1, 0.1, 0.1, new Particle.DustOptions(Color.fromRGB(0, 0, 255), 1.5F));
         player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 3, 0.3, (double)0.5F, 0.3, 0.1);
      }, 0L, 2L);
      this.speedTasks.put(uuid, taskId);
      Bukkit.getScheduler().runTaskLater(context.plugin(), () -> {
         this.cancelSpeedTask(uuid);
         player.removePotionEffect(PotionEffectType.SPEED);
         player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, context.intParam("\u80be\u4e0a\u817a\u7206\u53d1", "slowness_after_seconds", 3) * 20, context.intParam("\u80be\u4e0a\u817a\u7206\u53d1", "slowness_after_amplifier", 1), true, true));
         player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0F, 1.0F);
      }, (long)effectTicks);
      context.sendActivationMessage(player);
      return SkillActivationResult.successWithDuration(effectSeconds);
   }

   public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
      this.cancelSpeedTask(event.getPlayer().getUniqueId());
   }

   public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
      Player player = event.getEntity();
      this.cancelSpeedTask(player.getUniqueId());
      player.removePotionEffect(PotionEffectType.SPEED);
   }

   private void cancelSpeedTask(UUID uuid) {
      Integer taskId = (Integer)this.speedTasks.remove(uuid);
      if (taskId != null) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

   }
}
