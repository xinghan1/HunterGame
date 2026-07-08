package com.huntergame.skill.skills;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class LavaWalkerSkill implements HunterSkill {
   private static final String NAME = "\u7194\u5ca9\u884c\u8005";
   private final Random random = new Random();

   public String getName() {
      return "\u7194\u5ca9\u884c\u8005";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      Location playerLoc = player.getLocation();
      float yaw = playerLoc.getYaw();
      double lavaOffset = context.doubleParam("\u7194\u5ca9\u884c\u8005", "lava_offset", (double)1.0F);
      double x = playerLoc.getX() - Math.sin(Math.toRadians((double)yaw)) * lavaOffset;
      double z = playerLoc.getZ() + Math.cos(Math.toRadians((double)yaw)) * lavaOffset;
      double y = playerLoc.getY() + context.doubleParam("\u7194\u5ca9\u884c\u8005", "lava_y_offset", (double)1.0F);
      Block targetBlock = (new Location(playerLoc.getWorld(), x, y, z)).getBlock();
      targetBlock.setType(Material.LAVA);
      player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, context.intParam("\u7194\u5ca9\u884c\u8005", "fire_resistance_ticks", Integer.MAX_VALUE), context.intParam("\u7194\u5ca9\u884c\u8005", "fire_resistance_amplifier", 0), true, false));
      player.sendTitle(context.message("skill_lava_walker_title", "&b\ud83d\udca5 &l\u7194\u5ca9\u884c\u8005 &r\ud83d\udca5"), context.message("skill_lava_walker_subtitle", "&7\u6c38\u4e45\u6297\u706b\u6548\u679c\u5df2\u6fc0\u6d3b"), 10, 60, 10);
      player.spawnParticle(Particle.LAVA, playerLoc, 10, 0.2, 0.2, 0.2, 0.1);
      context.sendActivationMessage(player);
      return SkillActivationResult.success();
   }

   public void onEntityDamageByEntity(EntityDamageByEntityEvent event, SkillContext context) {
      if (event.getDamager() instanceof Player) {
         Player attacker = (Player)event.getDamager();
         if (context.isSelected(attacker, "\u7194\u5ca9\u884c\u8005")) {
            double fireChance = context.doubleParam("\u7194\u5ca9\u884c\u8005", "fire_chance", 0.2);
            if (!(this.random.nextDouble() >= fireChance)) {
               Entity victim = event.getEntity();
               if (victim instanceof LivingEntity) {
                  ((LivingEntity)victim).setFireTicks(context.intParam("\u7194\u5ca9\u884c\u8005", "fire_ticks", 60));
               }

            }
         }
      }
   }

   public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
      if (context.isSelected(event.getEntity(), "\u7194\u5ca9\u884c\u8005")) {
         event.getEntity().removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
      }

   }
}
