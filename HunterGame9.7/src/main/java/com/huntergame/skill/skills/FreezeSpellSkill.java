package com.huntergame.skill.skills;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class FreezeSpellSkill implements HunterSkill {
   private static final String NAME = "\u5b9a\u8eab\u672f";
   private final Map<UUID, Integer> glowTasks = new ConcurrentHashMap();

   public String getName() {
      return "\u5b9a\u8eab\u672f";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      int freezeTicks = context.intParam("\u5b9a\u8eab\u672f", "freeze_ticks", context.duration("\u5b9a\u8eab\u672f") * 20);
      int glowTicks = context.intParam("\u5b9a\u8eab\u672f", "glow_ticks", 1200);
      player.sendTitle(context.message("skill_freeze_spell_title", "&b\ud83d\udc40 &l\u5b9a\u8eab\u672f &r\ud83d\udc40"), context.message("skill_duration_subtitle", "&7\u6301\u7eed\u65f6\u95f4: &a%seconds%\u79d2", "%seconds%", freezeTicks / 20), 10, 60, 10);
      player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, context.intParam("\u5b9a\u8eab\u672f", "haste_ticks", Integer.MAX_VALUE), context.intParam("\u5b9a\u8eab\u672f", "haste_amplifier", 2), true, true));
      this.activateGlowEffect(player, context, glowTicks);
      context.plugin().getFreezeSkill().freezeAllEscapers(player, freezeTicks, context.intParam("\u5b9a\u8eab\u672f", "frozen_resistance_amplifier", 254));
      UUID uuid = player.getUniqueId();
      this.cancelGlowTask(uuid);
      int taskId = Bukkit.getScheduler().runTaskLater(context.plugin(), () -> this.removeGlowEffect(context), (long)glowTicks).getTaskId();
      this.glowTasks.put(uuid, taskId);
      context.sendActivationMessage(player);
      return SkillActivationResult.successWithDuration(context.duration("\u5b9a\u8eab\u672f"));
   }

   public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
      this.cleanup(event.getPlayer(), context);
   }

   public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
      this.cleanup(event.getEntity(), context);
   }

   private void activateGlowEffect(Player hunter, SkillContext context, int durationTicks) {
      for(Player player : Bukkit.getOnlinePlayers()) {
         if (context.plugin().isEscaper(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, true, false));
            hunter.showPlayer(context.plugin(), player);
            hunter.spawnParticle(Particle.REDSTONE, player.getLocation(), 10, new Particle.DustOptions(Color.fromRGB(0, 0, 0), 1.0F));
         }
      }

   }

   private void removeGlowEffect(SkillContext context) {
      for(Player player : Bukkit.getOnlinePlayers()) {
         if (context.plugin().isEscaper(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.GLOWING);
         }
      }

   }

   private void cleanup(Player player, SkillContext context) {
      this.cancelGlowTask(player.getUniqueId());
      player.removePotionEffect(PotionEffectType.FAST_DIGGING);
      this.removeGlowEffect(context);
   }

   private void cancelGlowTask(UUID uuid) {
      Integer taskId = (Integer)this.glowTasks.remove(uuid);
      if (taskId != null) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

   }
}
