package com.huntergame.spectator;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 观察者服务类，用于处理 HunterGame 游戏中观察者视角的限制、传送以及追捕者粒子追踪特效等功能。
 */
public class SpectatorService {

    /**
     * 定义粒子追踪特效的参数，颜色为蓝色，大小为 1.0。
     */
    private static final Particle.DustOptions DIRECTION_PARTICLE =
            new Particle.DustOptions(Color.BLUE, 1.0f);

    private final HunterGame plugin;
    private final Random random = new Random();

    /**
     * 构造函数，初始化观察者服务。
     *
     * @param plugin 主插件对象
     */
    public SpectatorService(HunterGame plugin) {
        this.plugin = plugin;
    }

    /**
     * 显示追捕者方向的粒子指引。
     * 计算距离追捕者最近的逃脱者，并在追捕者位置生成一条指向逃脱者的粒子特效。
     *
     * @param hunter 追捕者玩家对象
     */
    public void showHunterParticleDirection(Player hunter) {
        Player nearestEscaper = findNearestEscaper(hunter);
        // 如果没有找到正在活动中的逃脱者，则不生成粒子
        if (nearestEscaper == null) {
            return;
        }

        Location hunterLocation = hunter.getLocation();
        Location escaperLocation = nearestEscaper.getLocation();
        // 计算方向向量并归一化
        org.bukkit.util.Vector direction = escaperLocation.toVector().subtract(hunterLocation.toVector()).normalize();

        // 在追捕者脚下略高位置生成粒子
        Location particleLocation = hunterLocation.clone().add(0, 0.1, 0);
        for (double distance = 0; distance <= 2.0; distance += 0.2) {
            Location point = particleLocation.clone().add(direction.clone().multiply(distance));
            hunter.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, DIRECTION_PARTICLE);
        }
    }

    /**
     * 检查并传送违规的观察者。
     * 遍历所有在线观察者，如果其距离锚点过远且没有附身目标，则将其传送回存活玩家附近。
     */
    public void checkAndTeleportSpectators() {
        // 从配置文件中获取允许观察者离开存活玩家的最大距离，默认 100 格
        int maxDistance = plugin.getConfig().getInt("game.spectator_max_distance", 100);

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 如果玩家不是观察者模式，或者不是真实的观察者，则跳过
            if (player.getGameMode() != GameMode.SPECTATOR || !plugin.isRealSpectator(player.getUniqueId())) {
                continue;
            }

            // 如果观察者正在观察（附身）某个实体，则不强制传送
            Player spectatorTarget = getSpectatorTarget(player);
            if (spectatorTarget != null && spectatorTarget.isOnline()) {
                continue;
            }

            // 执行距离检查和传送
            teleportBackIfTooFar(player, maxDistance);
        }
    }

    /**
     * 将观察者随机传送到一名正在参与游戏的玩家（追捕者或逃脱者）身边。
     *
     * @param spectator 观察者玩家
     */
    public void teleportToRandomPlayer(Player spectator) {
        List<Player> availablePlayers = new ArrayList<>();
        addActivePlayers(availablePlayers, plugin.getHunters());
        addActivePlayers(availablePlayers, plugin.getEscapers());

        // 如果存在可用玩家，则随机选取一个进行传送
        if (!availablePlayers.isEmpty()) {
            Player target = availablePlayers.get(random.nextInt(availablePlayers.size()));
            spectator.teleport(target.getLocation());
        }
    }

    /**
     * 寻找距离追捕者最近的逃脱者。
     *
     * @param hunter 追捕者玩家
     * @return 最近的逃脱者玩家，若不存在则返回 null
     */
    private Player findNearestEscaper(Player hunter) {
        Player nearestEscaper = null;
        double minDistance = Double.MAX_VALUE;

        for (Player escaper : plugin.getEscapers()) {
            // 确保双方在同一个世界且逃脱者不在观察者模式中
            if (!isActiveInSameWorld(escaper, hunter)) {
                continue;
            }

            double distance = hunter.getLocation().distance(escaper.getLocation());
            if (distance < minDistance) {
                minDistance = distance;
                nearestEscaper = escaper;
            }
        }

        return nearestEscaper;
    }

    /**
     * 当观察者距离最近的玩家过远时，将其传送回来。
     *
     * @param spectator   观察者玩家
     * @param maxDistance 允许的最大距离
     */
    private void teleportBackIfTooFar(Player spectator, int maxDistance) {
        Player nearestPlayer = findNearestPlayer(spectator);
        if (nearestPlayer == null) {
            return;
        }

        // 如果不在同一个世界，直接传送
        if (!spectator.getWorld().equals(nearestPlayer.getWorld())) {
            teleportNearPlayer(spectator, nearestPlayer);
            return;
        }

        try {
            // 计算距离并在超过最大距离时进行传送
            double distance = spectator.getLocation().distance(nearestPlayer.getLocation());
            if (distance > maxDistance) {
                teleportNearPlayer(spectator, nearestPlayer);
            }
        } catch (IllegalArgumentException ignored) {
            // 若距离计算异常，兜底处理，直接传送
            teleportNearPlayer(spectator, nearestPlayer);
        }
    }

    /**
     * 寻找适合作为观察者传送锚点的最近玩家。
     *
     * @param spectator 观察者玩家
     * @return 最近的有效目标玩家，若无可用目标则返回 null
     */
    private Player findNearestPlayer(Player spectator) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        // 首先在同世界中寻找最近玩家
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!isTeleportAnchor(spectator, target)) {
                continue;
            }

            if (target.getWorld().equals(spectator.getWorld())) {
                try {
                    double distance = spectator.getLocation().distance(target.getLocation());
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearest = target;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 忽略跨世界计算的异常
                }
            }
        }

        // 如果找到了同世界的锚点玩家，直接返回
        if (nearest != null) {
            return nearest;
        }

        // 如果同世界没有，则跨世界在所有在线有效玩家中寻找一个
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (isTeleportAnchor(spectator, target)) {
                return target;
            }
        }

        return null;
    }

    /**
     * 将观察者传送至目标玩家附近，并发送提示消息。
     *
     * @param spectator 观察者玩家
     * @param target    目标玩家
     */
    private void teleportNearPlayer(Player spectator, Player target) {
        spectator.teleport(target.getLocation());
        spectator.sendMessage(plugin.getMessage("spectator_stay_near_target", "&7不可以跑远了，已传送到 %player% 附近")
                .replace("%player%", target.getName()));
    }

    /**
     * 获取观察者当前正在观察（附身）的玩家对象。
     *
     * @param spectator 观察者玩家
     * @return 观察的目标玩家，如果不是观察玩家，则返回 null
     */
    private Player getSpectatorTarget(Player spectator) {
        Entity target = spectator.getSpectatorTarget();
        return target instanceof Player ? (Player) target : null;
    }

    /**
     * 将在线且非观察者状态的玩家添加到列表中。
     *
     * @param availablePlayers 目标列表
     * @param players          原玩家列表
     */
    private void addActivePlayers(List<Player> availablePlayers, List<Player> players) {
        for (Player player : players) {
            if (player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                availablePlayers.add(player);
            }
        }
    }

    /**
     * 检查目标玩家是否在线、在游戏中且与参考对象处于同一世界。
     *
     * @param player    要检查的玩家
     * @param reference 参考玩家
     * @return true 如果有效且在同一世界，否则为 false
     */
    private boolean isActiveInSameWorld(Player player, Player reference) {
        return player != null
                && player.isOnline()
                && player.getGameMode() != GameMode.SPECTATOR
                && player.getWorld().equals(reference.getWorld());
    }

    /**
     * 检查目标玩家是否可以作为观察者传送的锚点。
     *
     * @param spectator 观察者玩家
     * @param target    要检查的目标玩家
     * @return true 如果可以作为锚点，否则为 false
     */
    private boolean isTeleportAnchor(Player spectator, Player target) {
        return !target.equals(spectator) && target.getGameMode() != GameMode.SPECTATOR;
    }
}
