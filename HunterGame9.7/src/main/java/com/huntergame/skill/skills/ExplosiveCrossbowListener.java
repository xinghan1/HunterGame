package com.huntergame.skill.skills;

import com.huntergame.HunterGame;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class ExplosiveCrossbowListener implements Listener {
   private static final String SKILL_NAME = "\u7206\u70b8\u5f29";
   private static final double EXPLOSION_RADIUS = (double)2.5F;
   private static final double DEFAULT_TRUE_DAMAGE = (double)3.0F;
   private final HunterGame plugin;
   private final NamespacedKey explosiveCrossbowKey;

   public ExplosiveCrossbowListener(HunterGame plugin) {
      this.plugin = plugin;
      this.explosiveCrossbowKey = new NamespacedKey(plugin, "explosive_crossbow");
   }

   @EventHandler
   public void onPlayerShootBow(ProjectileLaunchEvent event) {
      if (event.getEntity() instanceof Firework) {
         Firework firework = (Firework)event.getEntity();
         ProjectileSource shooterSource = firework.getShooter();
         if (shooterSource instanceof Player) {
            Player player = (Player)shooterSource;
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (this.isExplosiveCrossbow(itemInHand)) {
               this.markAsExplosiveRocket(firework, player);
            }

         }
      }
   }

   @EventHandler
   public void onFireworkExplode(FireworkExplodeEvent event) {
      Firework firework = event.getEntity();
      if (this.isExplosiveRocket(firework)) {
         Location loc = firework.getLocation();
         ProjectileSource shooter = firework.getShooter();
         if (shooter instanceof Player) {
            this.explodeWithTrueDamage(loc, (Player)shooter);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F);
         }

      }
   }

   private boolean isExplosiveCrossbow(ItemStack item) {
      if (item != null && item.getType() == Material.CROSSBOW) {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            return false;
         } else {
            Byte data = (Byte)meta.getPersistentDataContainer().get(this.explosiveCrossbowKey, PersistentDataType.BYTE);
            return data != null && data == 1;
         }
      } else {
         return false;
      }
   }

   private void markAsExplosiveRocket(Firework firework, Player shooter) {
      firework.setMetadata("explosive_rocket", new FixedMetadataValue(this.plugin, true));
      firework.setShooter(shooter);
   }

   private boolean isExplosiveRocket(Firework firework) {
      return firework.hasMetadata("explosive_rocket");
   }

   private void explodeWithTrueDamage(Location center, Player shooter) {
      for(Entity entity : center.getWorld().getNearbyEntities(center, (double)2.5F, (double)2.5F, (double)2.5F)) {
         if (entity instanceof LivingEntity target && !entity.equals(shooter)) {
            double distance = center.distance(entity.getLocation());
            if (!(distance > (double)2.5F)) {
               this.applyTrueDamage(target, this.getTrueDamage());
               Vector knockback = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply((double)0.5F);
               entity.setVelocity(knockback);
            }
         }
      }

   }

   private void applyTrueDamage(LivingEntity target, double damage) {
      double health = target.getHealth();
      AttributeInstance attr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
      double maxHealth = attr != null ? attr.getValue() : (double)20.0F;
      double newHealth = Math.max((double)0.0F, Math.min(maxHealth, health - damage));
      target.setHealth(newHealth);
      target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 10);
   }

   private double getTrueDamage() {
      return Math.max((double)0.0F, this.plugin.getSkillManager().getSkillDouble("\u7206\u70b8\u5f29", "true_damage", (double)3.0F));
   }

   public void giveCrossbowPackage(Player player) {
      ItemStack crossbow = new ItemStack(Material.CROSSBOW);
      ItemMeta meta = crossbow.getItemMeta();
      meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);
      meta.getPersistentDataContainer().set(this.explosiveCrossbowKey, PersistentDataType.BYTE, (byte)1);
      List<String> lore = new ArrayList();
      lore.add(this.getCrossbowSupplyLore());
      meta.setLore(lore);
      crossbow.setItemMeta(meta);
      ItemStack rockets = this.createExplosiveRockets(4);
      player.getInventory().addItem(new ItemStack[]{crossbow, rockets});
      player.updateInventory();
   }

   public String getCrossbowSupplyLore() {
      return this.plugin.getMessage("explosive_crossbow_supply_lore", "&7\u5de6\u952e\u83b7\u53d6\u7206\u70b8\u706b\u7bad");
   }

   ItemStack createExplosiveRockets(int amount) {
      ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET, amount);
      FireworkMeta meta = (FireworkMeta)rocket.getItemMeta();
      FireworkEffect effect = FireworkEffect.builder().with(Type.BURST).withColor(Color.RED).withFade(Color.ORANGE).withTrail().withFlicker().build();
      meta.addEffect(effect);
      meta.setPower(2);
      rocket.setItemMeta(meta);
      return rocket;
   }
}
