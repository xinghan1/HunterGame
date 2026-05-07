package com.huntergame.listener;

import com.huntergame.HunterGame;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CustomEntityListener implements Listener {

    private final HunterGame plugin;

    public CustomEntityListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    // 监听实体死亡事件
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 获取死亡的实体
        Entity entity = event.getEntity();

        if (plugin.isVanillaHunterMode()) {return;}// 如果是原版猎人模式，不触发

        // 3. 击杀猎人掉落烈焰棒
        if (entity instanceof Blaze) {
            handleHunterLoot(event);
        }

        // 4. 击杀凋零骷髅掉落凋零骷髅头
        if (entity instanceof WitherSkeleton) {
            handleWitherSkeletonLoot(event);
        }
    }

    @EventHandler
    public void onPiglinBarter(PiglinBarterEvent event) {
        // 1. 获取交易输入物品（金锭）
        ItemStack input = event.getInput();
        if (input.getType() != Material.GOLD_INGOT) return;

        // 2. 获取可修改的结果列表
        List<ItemStack> outcome = event.getOutcome();
        outcome.clear(); // 清除原版掉落物

        // 3. 添加自定义掉落（带随机性）
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.3) {
            outcome.add(createFireResistancePotion());
        } else if (roll < 0.8) {
            outcome.add(new ItemStack(Material.ENDER_PEARL, 4));
            outcome.add(new ItemStack(Material.OBSIDIAN, 4));
        } else {
            outcome.add(new ItemStack(Material.SPECTRAL_ARROW, 8));
        }

    }

    private ItemStack createFireResistancePotion() {
        // 创建喷溅药水（抗火）
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta == null) return potion;

        // 设置主药水类型为 抗火（标准药水）
        meta.addCustomEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE,  // 效果类型
                20 * 60 * 3,                          // 持续时间（ticks）
                0,                         // 效果等级（0为1级，1为2级，依此类推）
                true,                              // 是否显示粒子效果
                true                               // 是否显示图标
        ), true);  // true表示覆盖已有效果

        potion.setItemMeta(meta);
        return potion;
    }


    // 处理猎人击杀后的掉落烈焰棒
    private void handleHunterLoot(EntityDeathEvent event) {
        // 添加烈焰棒掉落，概率 100%
        event.getDrops().add(new ItemStack(Material.BLAZE_ROD, 2));
    }

    // 处理凋零骷髅掉落凋零骷髅头，概率25%
    private void handleWitherSkeletonLoot(EntityDeathEvent event) {
        // 如果随机数小于等于 50，掉落凋零骷髅头
        if (ThreadLocalRandom.current().nextInt(100) < 25) {
            event.getDrops().add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
        }
    }

}

