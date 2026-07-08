package com.huntergame.listener;

import com.huntergame.HunterGame;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CustomEntityListener implements Listener {
   private final HunterGame plugin;

   public CustomEntityListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onEntityDeath(EntityDeathEvent event) {
      Entity entity = event.getEntity();
      if (!this.plugin.isVanillaHunterMode()) {
         if (entity instanceof Blaze) {
            this.handleHunterLoot(event);
         }

         if (entity instanceof WitherSkeleton) {
            this.handleWitherSkeletonLoot(event);
         }

      }
   }

   @EventHandler
   public void onPiglinBarter(PiglinBarterEvent event) {
      ItemStack input = event.getInput();
      if (input.getType() == Material.GOLD_INGOT) {
         List<ItemStack> outcome = event.getOutcome();
         outcome.clear();
         double roll = ThreadLocalRandom.current().nextDouble();
         if (roll < 0.3) {
            outcome.add(this.createFireResistancePotion());
         } else if (roll < 0.8) {
            outcome.add(new ItemStack(Material.ENDER_PEARL, 4));
            outcome.add(new ItemStack(Material.OBSIDIAN, 4));
         } else {
            outcome.add(new ItemStack(Material.SPECTRAL_ARROW, 8));
         }

      }
   }

   private ItemStack createFireResistancePotion() {
      ItemStack potion = new ItemStack(Material.SPLASH_POTION);
      PotionMeta meta = (PotionMeta)potion.getItemMeta();
      if (meta == null) {
         return potion;
      } else {
         meta.addCustomEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 3600, 0, true, true), true);
         potion.setItemMeta(meta);
         return potion;
      }
   }

   private void handleHunterLoot(EntityDeathEvent event) {
      event.getDrops().add(new ItemStack(Material.BLAZE_ROD, 2));
   }

   private void handleWitherSkeletonLoot(EntityDeathEvent event) {
      if (ThreadLocalRandom.current().nextInt(100) < 25) {
         event.getDrops().add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
      }

   }
}
