package com.huntergame.world;

import com.huntergame.HunterGame;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

public class DamageProtection implements Listener {

    private final HunterGame plugin;

    public DamageProtection(HunterGame plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (plugin.isFinalBattleMode()) {
            // 执行终章之战特有的逻辑
            return;
        }
        if (plugin.isVanillaHunterMode()) {return;}  // 如果是原版猎人模式，不处理

        // 只处理玩家受到伤害的情况
        Entity victim = event.getEntity();
        if (!(victim instanceof Player)) {
            return;
        }

        Player player = (Player) victim;
        UUID playerId = player.getUniqueId();

        // 判断攻击者是否为玩家
        Entity attacker = event.getDamager();
        if (attacker instanceof Player) {
            // 玩家之间的伤害不减免，直接返回
            return;
        }

        // 其他实体（怪物等）造成的伤害，应用减免
        double originalDamage = event.getDamage();
        double finalDamage = applyGeneralDamageReduction(playerId, originalDamage);

        // 应用最终伤害
        event.setDamage(finalDamage);
    }

    // 处理所有环境伤害（爆炸、摔落、火焰、岩浆等）
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (plugin.isFinalBattleMode()) {
            // 执行终章之战特有的逻辑
            return;
        }
        if (plugin.isVanillaHunterMode()) {return;}  // 如果是原版猎人模式，不处理

        // 只处理玩家受到伤害的情况
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        UUID playerId = player.getUniqueId();

        double originalDamage = event.getDamage();
        double finalDamage = applyGeneralDamageReduction(playerId, originalDamage);

        // 特殊处理末地摔落伤害
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL &&
                player.getWorld().getEnvironment() == World.Environment.THE_END) {
            // 末地摔落伤害额外减免30%
            finalDamage = finalDamage * 0.7;
        }

        // 应用最终伤害
        event.setDamage(finalDamage);
    }

    // 应用通用伤害减免逻辑（逃生者40%，猎人40%）
    private double applyGeneralDamageReduction(UUID playerId, double originalDamage) {
        if (plugin.isEscaper(playerId)) {
            // 逃生者免伤25%
            return originalDamage * 0.75;
        } else {
            // 猎人免伤25%
            return originalDamage * 0.75;
        }
    }
}