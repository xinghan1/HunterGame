package com.huntergame.listener;

import com.huntergame.HunterGame;
import com.huntergame.combat.LastDamageTracker;
import com.huntergame.role.RoleSelectionHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class DeathMessageListener implements Listener {
    private final HunterGame plugin;
    private final Random rand = new Random();

    public DeathMessageListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String deathMessage = resolveRole(player) == PlayerRole.ESCAPER
                ? escaperDeathMessage(player)
                : hunterDeathMessage(player);

        event.deathMessage(null);
        Bukkit.broadcastMessage(deathMessage);
    }

    private PlayerRole resolveRole(Player player) {
        UUID playerId = player.getUniqueId();
        boolean hunter = plugin.isHunter(playerId);
        boolean escaper = plugin.isEscaper(playerId) || plugin.isDeathescapers(playerId);

        if (hunter) {
            return PlayerRole.HUNTER;
        }
        if (escaper) {
            return PlayerRole.ESCAPER;
        }

        PersistentDataContainer container = player.getPersistentDataContainer();
        boolean hunterTag = Boolean.TRUE.equals(container.get(RoleSelectionHandler.IS_HUNTER, PersistentDataType.BOOLEAN));
        boolean escaperTag = Boolean.TRUE.equals(container.get(RoleSelectionHandler.IS_ESCAPER, PersistentDataType.BOOLEAN));
        if (hunterTag) {
            return PlayerRole.HUNTER;
        }
        if (escaperTag) {
            return PlayerRole.ESCAPER;
        }

        return PlayerRole.HUNTER;
    }

    private String hunterDeathMessage(Player player) {
        return deathMessage(player, PlayerRole.HUNTER);
    }

    private String escaperDeathMessage(Player player) {
        return deathMessage(player, PlayerRole.ESCAPER);
    }

    private String deathMessage(Player player, PlayerRole role) {
        String roleKey = role.configKey;
        String prefix = getPrefix(role);
        String killerPrefix = getKillerPrefix(role);

        LastDamageTracker.DamageCredit creditedKiller = getNonPlayerDeathCredit(player);
        if (creditedKiller != null) {
            return randomMessage("death." + roleKey + ".credited_player", creditedPlayerDefaults(), player, prefix, killerPrefix, creditedKiller.playerName(), 0, 0);
        }

        EntityDamageEvent damageEvent = player.getLastDamageCause();
        if (damageEvent == null) {
            String fallback = role == PlayerRole.HUNTER
                    ? "%prefix%%victim% &7在狩猎中神秘失踪"
                    : "%prefix%%victim% &7死翘翘了";
            return message("death." + roleKey + ".no_cause", fallback, player, prefix, killerPrefix, "", 0, 0);
        }

        Entity damager = resolveDamager(damageEvent);
        EntityDamageEvent.DamageCause cause = damageEvent.getCause();

        switch (cause) {
            case ENTITY_ATTACK:
                return entityAttackMessage(player, role, prefix, killerPrefix, damager);
            case ENTITY_EXPLOSION:
                return explosionMessage(player, prefix, killerPrefix, damager);
            case PROJECTILE:
                return projectileMessage(player, role, prefix, killerPrefix, damager);
            case FALL:
                return message("death.common.fall", "%prefix%%victim% &7从 &6%height%格 &7高空摔成肉饼",
                        player, prefix, killerPrefix, "", 0, (int) player.getFallDistance());
            case VOID:
                return message("death.common.void", "%prefix%%victim% &7坠入了 &8虚空 &7，被黑暗永远吞噬",
                        player, prefix, killerPrefix, "", 0, 0);
            case DROWNING:
                return message("death.common.drowning", "%prefix%%victim% &7试图 &3潜水逃脱 &7却没能浮出水面",
                        player, prefix, killerPrefix, "", 0, 0);
            case FIRE:
                return message("death.common.fire", "%prefix%%victim% &7被 &c火焰 &7吞噬成灰",
                        player, prefix, killerPrefix, "", 0, 0);
            case LAVA:
                return message("death.common.lava", "%prefix%%victim% &7挑战 &6岩浆洗礼 &7失败",
                        player, prefix, killerPrefix, "", 0, 0);
            case SUFFOCATION:
                return message("death.common.suffocation", "%prefix%%victim% &7被 &8墙体 &7缓慢碾碎",
                        player, prefix, killerPrefix, "", 0, 0);
            case POISON:
                return message("death.common.poison", "%prefix%%victim% &7被 &2剧毒 &7折磨至死",
                        player, prefix, killerPrefix, "", 0, 0);
            case HOT_FLOOR:
                if (damager instanceof Blaze) {
                    return message("death.common.blaze", "%prefix%%victim% &7被 &6烈焰人 &7的火焰风暴吞噬",
                            player, prefix, killerPrefix, "", 0, 0);
                }
                return message("death.common.hot_floor", "%prefix%%victim% &7在 &c烈焰 &7中涅槃失败",
                        player, prefix, killerPrefix, "", 0, 0);
            default:
                return message("death.common.default", "%prefix%%victim% &7死翘翘了",
                        player, prefix, killerPrefix, "", 0, 0);
        }
    }

    private String entityAttackMessage(Player player, PlayerRole role, String prefix, String killerPrefix, Entity damager) {
        if (damager instanceof Player killer) {
            return randomMessage("death." + role.configKey + ".player_melee", playerMeleeDefaults(role),
                    player, prefix, killerPrefix, killer.getName(), 0, 0);
        }
        if (damager instanceof Enderman) {
            return message("death.common.enderman", "%prefix%%victim% &7的装备被 &5末影人 &7缴械", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Spider || damager instanceof CaveSpider) {
            return message("death.common.spider", "%prefix%%victim% &7的陷阱被 &7毒蛛 &7反制", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof WitherSkeleton) {
            return message("death.common.wither_skeleton", "%prefix%%victim% &7的护甲被 &7凋零骷髅 &7腐蚀击破", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Zombie) {
            return message("death.common.zombie", "%prefix%%victim% &7被 &7僵尸 &7爆操了", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof IronGolem) {
            return message("death.common.iron_golem", "%prefix%%victim% &7被 &f铁傀儡 &7击飞致死了", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof PigZombie) {
            return message("death.common.pig_zombie", "%prefix%%victim% &7被 &6僵尸猪人 &7击杀了", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof MagmaCube) {
            return message("death.common.magma_cube", "%prefix%%victim% &7被 &6岩浆怪 &7压成了肉饼", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Vindicator) {
            return message("death.common.vindicator", "%prefix%%victim% &7被 &6卫道士 &7砍成五马分尸", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof PiglinBrute) {
            return message("death.common.piglin_brute", "%prefix%%victim% &7被 &6猪灵蛮兵 &7砍成五马分尸", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Wolf) {
            return message("death.common.wolf", "%prefix%%victim% &7被 &6狼 &7咬死了", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Drowned) {
            return message("death.common.drowned", "%prefix%%victim% &7被 &3溺尸 &7拖入了水下深渊", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Husk) {
            return message("death.common.husk", "%prefix%%victim% &7被 &6尸壳 &7吞噬在沙漠风暴中", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Warden) {
            return message("death.common.warden", "%prefix%%victim% &7被 &1守卫者 &7的愤怒撕成碎片", player, prefix, killerPrefix, "", 0, 0);
        }
        return message("death.common.entity_attack", "%prefix%%victim% &7在近战交锋中陨落", player, prefix, killerPrefix, "", 0, 0);
    }

    private String explosionMessage(Player player, String prefix, String killerPrefix, Entity damager) {
        if (damager instanceof Wither) {
            return message("death.common.wither_explosion", "%prefix%%victim% &7被 &8凋零 &7的毁灭冲击波粉碎", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Creeper) {
            return message("death.common.creeper", "%prefix%%victim% &7被 &a苦力怕 &7的爱心爆炸融化", player, prefix, killerPrefix, "", 0, 0);
        }
        return message("death.common.explosion", "%prefix%%victim% &7被爆炸炸得四分五裂", player, prefix, killerPrefix, "", 0, 0);
    }

    private String projectileMessage(Player player, PlayerRole role, String prefix, String killerPrefix, Entity damager) {
        if (damager instanceof Player shooter) {
            int roundedDistance = (int) Math.round(shooter.getLocation().distance(player.getLocation()));
            return message("death." + role.configKey + ".player_projectile",
                    "%prefix%%victim% &7被 %killer_prefix%%killer% 精准命中 &8(&f%distance%米&8)",
                    player, prefix, killerPrefix, shooter.getName(), roundedDistance, 0);
        }
        if (damager instanceof Skeleton) {
            return message("death.common.skeleton", "%prefix%%victim% &7被 &8Donk骷髅弓手 &7颗秒了", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Pillager) {
            return message("death.common.pillager", "%prefix%%victim% &7被 &8掠夺者 &7的弩箭钉在墙上", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Piglin) {
            return message("death.common.piglin", "%prefix%%victim% &7被 &6猪灵 &7的弩箭钉在墙上", player, prefix, killerPrefix, "", 0, 0);
        }
        if (damager instanceof Blaze) {
            return message("death.common.blaze", "%prefix%%victim% &7被 &6烈焰人 &7的火焰风暴吞噬", player, prefix, killerPrefix, "", 0, 0);
        }
        return message("death.common.projectile", "%prefix%%victim% &7被远程武器猎杀", player, prefix, killerPrefix, "", 0, 0);
    }

    private LastDamageTracker.DamageCredit getNonPlayerDeathCredit(Player player) {
        LastDamageTracker.DamageCredit credit = plugin.getLastDamageTracker().getCreditedKiller(player);
        if (credit == null) {
            return null;
        }

        EntityDamageEvent damageEvent = player.getLastDamageCause();
        if (damageEvent instanceof EntityDamageByEntityEvent entityEvent) {
            Player damager = plugin.getLastDamageTracker().getPlayerDamager(entityEvent.getDamager());
            if (damager != null && damager.getUniqueId().equals(credit.playerId())) {
                return null;
            }
        }

        return credit;
    }

    private Entity resolveDamager(EntityDamageEvent damageEvent) {
        if (!(damageEvent instanceof EntityDamageByEntityEvent entityEvent)) {
            return null;
        }

        Entity originalDamager = entityEvent.getDamager();
        if (originalDamager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return originalDamager;
    }

    private String randomMessage(String key, List<String> defaults, Player victim, String prefix, String killerPrefix,
                                 String killer, int distance, int height) {
        List<String> messages = plugin.getMessageList(key, defaults);
        String template = messages.isEmpty() ? "" : messages.get(rand.nextInt(messages.size()));
        return applyPlaceholders(template, victim, prefix, killerPrefix, killer, distance, height);
    }

    private String message(String key, String fallback, Player victim, String prefix, String killerPrefix,
                           String killer, int distance, int height) {
        return applyPlaceholders(plugin.getMessage(key, fallback), victim, prefix, killerPrefix, killer, distance, height);
    }

    private String applyPlaceholders(String template, Player victim, String prefix, String killerPrefix,
                                     String killer, int distance, int height) {
        return template
                .replace("%prefix%", prefix)
                .replace("%victim%", victim.getName())
                .replace("%killer_prefix%", killerPrefix)
                .replace("%killer%", killer == null ? "" : killer)
                .replace("%distance%", String.valueOf(distance))
                .replace("%height%", String.valueOf(height));
    }

    private String getPrefix(PlayerRole role) {
        return plugin.getMessage("death." + role.configKey + ".prefix", role == PlayerRole.HUNTER ? "&f[☠] &c🏹" : "&f[☠] &b⚔");
    }

    private String getKillerPrefix(PlayerRole victimRole) {
        return plugin.getMessage("death." + victimRole.configKey + ".killer_prefix", victimRole == PlayerRole.HUNTER ? "&b⚔" : "&c🏹");
    }

    private List<String> playerMeleeDefaults(PlayerRole role) {
        if (role == PlayerRole.HUNTER) {
            return Arrays.asList(
                    "%prefix%%victim% &7被 %killer_prefix%%killer% &7近战击败",
                    "%prefix%%victim% &7与 %killer_prefix%%killer% &7肉搏后落败",
                    "%prefix%%victim% &7被 %killer_prefix%%killer% &7打成重伤倒地",
                    "%prefix%%victim% &7的防线被 %killer_prefix%%killer% &7彻底击破",
                    "%prefix%%victim% &7在与 %killer_prefix%%killer% &7的死斗中战败",
                    "%prefix%%victim% &7被 %killer_prefix%%killer% &7给嘎掉了"
            );
        }
        return Arrays.asList(
                "%prefix%%victim% &7被 %killer_prefix%%killer% &7终结了",
                "%prefix%%victim% &7与 %killer_prefix%%killer% &7肉搏后落败",
                "%prefix%%victim% &7被 %killer_prefix%%killer% &7打成重伤倒地",
                "%prefix%%victim% &7的防线被 %killer_prefix%%killer% &7彻底击破",
                "%prefix%%victim% &7在与 %killer_prefix%%killer% &7的死斗中战败",
                "%prefix%%victim% &7被 %killer_prefix%%killer% &7给嘎掉了"
        );
    }

    private List<String> creditedPlayerDefaults() {
        return Arrays.asList(
                "%prefix%%victim% &7被 %killer_prefix%%killer% &7击败",
                "%prefix%%victim% &7被 %killer_prefix%%killer% &7打入绝境后死亡",
                "%prefix%%victim% &7没能逃出 %killer_prefix%%killer% &7的追杀",
                "%prefix%%victim% &7被 %killer_prefix%%killer% &7终结"
        );
    }

    private enum PlayerRole {
        HUNTER("hunter"),
        ESCAPER("escaper");

        private final String configKey;

        PlayerRole(String configKey) {
            this.configKey = configKey;
        }
    }
}
