package com.huntergame.world;

import java.util.Collection;
import java.util.Map;
import org.bukkit.Material;

public class OreConfig {
   private static final Map<Material, OreSettings> ORE_SETTINGS;

   public static OreSettings getSettings(Material oreType) {
      return (OreSettings)ORE_SETTINGS.getOrDefault(oreType, new OreSettings((Material)null, 1, 1, (double)0.0F, 0, 0, 0, 0));
   }

   public static Collection<OreSettings> getAllSettings() {
      return ORE_SETTINGS.values();
   }

   static {
      ORE_SETTINGS = Map.ofEntries(Map.entry(Material.COAL_ORE, new OreSettings(Material.COAL_ORE, 5, 12, 0.85, 3, 0, 96, 20)), Map.entry(Material.DEEPSLATE_COAL_ORE, new OreSettings(Material.DEEPSLATE_COAL_ORE, 4, 9, 0.7, 3, -32, 0, 4)), Map.entry(Material.IRON_ORE, new OreSettings(Material.IRON_ORE, 4, 10, 0.9, 3, -16, 72, 26)), Map.entry(Material.DEEPSLATE_IRON_ORE, new OreSettings(Material.DEEPSLATE_IRON_ORE, 4, 9, 0.85, 3, -64, 0, 14)), Map.entry(Material.COPPER_ORE, new OreSettings(Material.COPPER_ORE, 4, 10, (double)0.75F, 3, 0, 96, 8)), Map.entry(Material.DEEPSLATE_COPPER_ORE, new OreSettings(Material.DEEPSLATE_COPPER_ORE, 3, 8, 0.65, 3, -32, 0, 4)), Map.entry(Material.GOLD_ORE, new OreSettings(Material.GOLD_ORE, 3, 7, 0.65, 2, -16, 32, 7)), Map.entry(Material.DEEPSLATE_GOLD_ORE, new OreSettings(Material.DEEPSLATE_GOLD_ORE, 3, 7, 0.65, 2, -64, 0, 7)), Map.entry(Material.REDSTONE_ORE, new OreSettings(Material.REDSTONE_ORE, 4, 8, (double)0.75F, 3, -32, 16, 10)), Map.entry(Material.DEEPSLATE_REDSTONE_ORE, new OreSettings(Material.DEEPSLATE_REDSTONE_ORE, 4, 9, 0.8, 3, -64, 0, 10)), Map.entry(Material.LAPIS_ORE, new OreSettings(Material.LAPIS_ORE, 3, 6, 0.55, 2, -32, 32, 6)), Map.entry(Material.DEEPSLATE_LAPIS_ORE, new OreSettings(Material.DEEPSLATE_LAPIS_ORE, 3, 7, 0.6, 2, -64, 0, 6)), Map.entry(Material.DIAMOND_ORE, new OreSettings(Material.DIAMOND_ORE, 2, 5, 0.45, 2, -16, 16, 4)), Map.entry(Material.DEEPSLATE_DIAMOND_ORE, new OreSettings(Material.DEEPSLATE_DIAMOND_ORE, 2, 6, 0.55, 2, -64, 0, 7)), Map.entry(Material.EMERALD_ORE, new OreSettings(Material.EMERALD_ORE, 1, 3, (double)0.25F, 1, -16, 72, 1)), Map.entry(Material.DEEPSLATE_EMERALD_ORE, new OreSettings(Material.DEEPSLATE_EMERALD_ORE, 1, 3, (double)0.25F, 1, -64, 0, 1)));
   }

   public static final class OreSettings {
      public final Material material;
      public final int minCount;
      public final int maxCount;
      public final double chance;
      public final int radius;
      public final int minY;
      public final int maxY;
      public final int weight;

      public OreSettings(Material material, int minCount, int maxCount, double chance, int radius, int minY, int maxY, int weight) {
         this.material = material;
         this.minCount = minCount;
         this.maxCount = maxCount;
         this.chance = chance;
         this.radius = radius;
         this.minY = minY;
         this.maxY = maxY;
         this.weight = weight;
      }
   }
}
