package com.huntergame.skill;

import com.huntergame.HunterGame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreezeSkill implements Listener {
    private final HunterGame plugin;
    // 存储被定身的玩家及其定身信息
    private final Map<UUID, FreezeData> frozenPlayers = new HashMap<>();
    // 存储解冻任务ID
    private final Map<UUID, Integer> freezeTasks = new HashMap<>();

    public FreezeSkill(HunterGame plugin) {
        this.plugin = plugin;
    }


    // 公共方法：立即解除定身
    public void unfreezeNow(UUID playerId) {
        unfreezePlayer(playerId); // 你已经写好的方法
    }

    /**
     * 冻结指定范围内的逃生者
     * @param hunter 触发技能的猎人
     * @param durationTicks 定身持续时间（刻）
     */
    public void freezeEscapers(Player hunter, int durationTicks) {
        UUID hunterId = hunter.getUniqueId();
        Location hunterLoc = hunter.getLocation();

        // 遍历所有在线玩家
        for (Player target : Bukkit.getOnlinePlayers()) {
            UUID targetId = target.getUniqueId();

            // 检查是否为逃生者且在40格范围内
            if (plugin.isEscaper(targetId)
                    && hunterLoc.distanceSquared(target.getLocation()) <= 40 * 40
                    && !target.hasPotionEffect(PotionEffectType.INVISIBILITY)
                    && target.getGameMode() != GameMode.SPECTATOR) {
                // 记录玩家定身时的位置和朝向
                Location originalLoc = target.getLocation().clone();

                // 添加定身标记
                target.setMetadata("frozen_by_perspective", new FixedMetadataValue(plugin, hunterId));

                // 视觉效果
                target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0F, 1.5F);
                target.getWorld().spawnParticle(
                        Particle.END_ROD,
                        target.getLocation().add(0, 1, 0),
                        20, 0.5, 0.5, 0.5, 0.2
                );
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE,
                        durationTicks, // 3秒 (20ticks/秒 × 3秒)
                        254,   // 等级0（仅免疫摔落伤害）
                        true,
                        false
                ));

                // 记录被冻结的玩家
                frozenPlayers.put(targetId, new FreezeData(hunterId, durationTicks, originalLoc));

                // 设置解冻任务
                int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    unfreezePlayer(targetId);
                }, durationTicks).getTaskId();

                freezeTasks.put(targetId, taskId);
            }
        }
    }

    /**
     * 解冻玩家
     * @param playerId 被解冻的玩家ID
     */
    private void unfreezePlayer(UUID playerId) {
        if (frozenPlayers.containsKey(playerId)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                // 移除定身标记
                player.removeMetadata("frozen_by_perspective", plugin);

                // 解冻视觉效果
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);

            }

            // 清理任务和数据
            if (freezeTasks.containsKey(playerId)) {
                Bukkit.getScheduler().cancelTask(freezeTasks.get(playerId));
                freezeTasks.remove(playerId);
            }
            frozenPlayers.remove(playerId);
        }
    }

    /**
     * 监听玩家移动事件，阻止被定身玩家移动
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查玩家是否被定身
        if (isPlayerFrozen(playerId)) {
            FreezeData freezeData = frozenPlayers.get(playerId);
            if (freezeData != null) {
                Location originalLoc = freezeData.getOriginalLocation();

                // 比较移动后的位置与原位置（使用容差值处理浮点误差）
                if (isPositionChanged(event.getTo(), originalLoc)) {
                    // 取消移动并强制传送回原位置
                    event.setCancelled(true);
                    player.teleport(originalLoc);

                    // 恢复原朝向
                    player.setRotation(originalLoc.getYaw(), originalLoc.getPitch());
                }
            }
        }
    }

    /**
     * 检查玩家是否被定身
     * @param playerId 玩家ID
     * @return 是否被定身
     */
    boolean isPlayerFrozen(UUID playerId) {
        return frozenPlayers.containsKey(playerId) &&
                Bukkit.getPlayer(playerId) != null &&
                Bukkit.getPlayer(playerId).hasMetadata("frozen_by_perspective");
    }

    /**
     * 检查位置是否发生改变（考虑浮点误差）
     */
    private boolean isPositionChanged(Location newLoc, Location originalLoc) {
        if (newLoc == null) return true;

        double tolerance = 0.01; // 容差值
        return Math.abs(newLoc.getX() - originalLoc.getX()) > tolerance ||
                Math.abs(newLoc.getY() - originalLoc.getY()) > tolerance ||
                Math.abs(newLoc.getZ() - originalLoc.getZ()) > tolerance ||
                Math.abs(newLoc.getYaw() - originalLoc.getYaw()) > 1 ||
                Math.abs(newLoc.getPitch() - originalLoc.getPitch()) > 1;
    }

    /**
     * 定身数据类
     */
    private static class FreezeData {
        private final UUID hunterId;
        private final int durationTicks;
        private final Location originalLocation;

        FreezeData(UUID hunterId, int durationTicks, Location originalLocation) {
            this.hunterId = hunterId;
            this.durationTicks = durationTicks;
            this.originalLocation = originalLocation;
        }

        public UUID getHunterId() {
            return hunterId;
        }

        public int getDurationTicks() {
            return durationTicks;
        }

        public Location getOriginalLocation() {
            return originalLocation;
        }
    }
}
