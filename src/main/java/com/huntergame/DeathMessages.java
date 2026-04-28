package com.huntergame;

import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Random;
import java.util.UUID;

public class DeathMessages implements Listener {
    private final HunterGame plugin;
    private final Random rand = new Random();
    public DeathMessages(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        String deathMessage;

        if (plugin.isEscaper(playerId)) {
            deathMessage = EscaperDeathMessage(player);
        } else {
            deathMessage = HunterDeathMessage(player);
        }

        event.deathMessage(null);
        Bukkit.broadcastMessage(deathMessage);
    }

    private String HunterDeathMessage(Player player) {
        final String PREFIX = "§f[☠] §c\uD83C\uDFF9"; // 弓箭符号
        if (player.getLastDamageCause() == null) {
            return PREFIX + player.getName() + " §7在狩猎中神秘失踪";
        }

        EntityDamageEvent damageEvent = player.getLastDamageCause();
        Entity damager = null;
        Projectile projectile = null;

        // 获取伤害来源
        if (damageEvent instanceof EntityDamageByEntityEvent entityEvent) {
            Entity originalDamager = entityEvent.getDamager();

            // 处理抛射物
            if (originalDamager instanceof Projectile) {
                projectile = (Projectile) originalDamager;
                // 获取抛射物发射者
                if (projectile.getShooter() instanceof Entity) {
                    damager = (Entity) projectile.getShooter();
                }
            } else {
                damager = originalDamager;
            }
        }

        EntityDamageEvent.DamageCause cause = damageEvent.getCause();

        switch (cause) {
            case ENTITY_ATTACK:
                if (damager != null) {
                    if (damager instanceof Enderman) {
                        return PREFIX + player.getName() + " §7的装备被 §5末影人 §7缴械";
                    } else if (damager instanceof Spider) {
                        return PREFIX + player.getName() + " §7的陷阱被 §7毒蛛 §7反制";
                    } else if (damager instanceof CaveSpider) {
                        return PREFIX + player.getName() + " §7的陷阱被 §7毒蛛 §7反制";
                    } else if (damager instanceof Player) {
                        Player killer = (Player) damager;
                        String[] messages = {
                                PREFIX + player.getName() + " §7被 §b⚔" + killer.getName() + " §7近战击败",
                                PREFIX + player.getName() + " §7与 §b⚔" + killer.getName() + " §7肉搏后落败",
                                PREFIX + player.getName() + " §7被 §b⚔" + killer.getName() + " §7打成重伤倒地",
                                PREFIX + player.getName() +  " §7的防线被 §b⚔" + killer.getName() + " §7彻底击破",
                                PREFIX + player.getName() + " §7在与 §b⚔" + killer.getName() + " §7的死斗中战败",
                                PREFIX + player.getName() + " §7被 §b⚔" + killer.getName() + " §7给嘎掉了"
                        };

                        // 增加 逃生者 击杀熟练度
                        if (plugin.isFinalBattleMode()) { // 终章
                            plugin.getDataStorageManager().addProficiency(killer, plugin.getRankManager().getFinalBattle_EscaperKillReward());
                        } else if (plugin.isVanillaHunterMode()) { // 原版
                            plugin.getDataStorageManager().addProficiency(killer, plugin.getRankManager().getOrdinaryBattle_EscaperKillReward());
                        } else if (plugin.isSkillHunterMode()) { // 技能
                            plugin.getDataStorageManager().addProficiency(killer, plugin.getRankManager().getSkillBattle_EscaperKillReward());
                        }

                        return messages[rand.nextInt(messages.length)];
                    } else if (damager instanceof WitherSkeleton) {
                        return PREFIX + player.getName() + " §7的护甲被 §7凋零骷髅 §7腐蚀击破";
                    } else if (damager instanceof Zombie) {
                        return PREFIX + player.getName() + " §7被 §7僵尸 §7爆操了";
                    } else if (damager instanceof IronGolem) {
                        return  PREFIX + player.getName() + "§7被 §f铁傀儡 §7击飞致死了";
                    } else if (damager instanceof PigZombie) {
                        return PREFIX + player.getName() + "§7被 §6僵尸诸人 §7击杀了";
                    } else if (damager instanceof MagmaCube) {
                        return PREFIX + player.getName() + "§7被 §6岩浆怪 §7压成了肉饼";
                    } else if (damager instanceof Vindicator) {
                        return PREFIX + player.getName() + "§7被 §6卫道士 §7砍成五马分尸";
                    } else if (damager instanceof PiglinBrute) {
                        return PREFIX + player.getName() + "§7被 §6猪灵蛮兵 §7砍成五马分尸";
                    } else if (damager instanceof PiglinBrute) {
                        return PREFIX + player.getName() + "§7被 §6僵尸疣猪兽 §7撞成狗了";
                    } else if (damager instanceof Wolf) {
                        return PREFIX + player.getName() + "§7被 §6狼 §7咬死了";
                    } else if (damager instanceof Drowned) {
                        return PREFIX + player.getName() + " §7的陷阱被 §7毒蛛 §7反制";
                    } else if (damager instanceof Drowned) {
                        return PREFIX + player.getName() + " §7被 §3溺尸 §7拖入了水下深渊";
                    } else if (damager instanceof Husk) {
                        return PREFIX + player.getName() + " §7被 §6尸壳 §7吞噬在沙漠风暴中";
                    } else if (damager instanceof Warden) {
                        return PREFIX + player.getName() + " §7被 §1守卫者 §7的愤怒撕成碎片";
                    }


                }
                return PREFIX + player.getName() + " §7在近战交锋中陨落";
            case ENTITY_EXPLOSION:
                if (damager instanceof Wither) {
                    return PREFIX + player.getName() + " §7被 §8凋零 §7的毁灭冲击波粉碎";
                } else if (damager instanceof Creeper) {
                    return PREFIX + player.getName() + " §7被 §a苦力怕 §7的爱心爆炸融化";
                }
                return PREFIX + player.getName() + " §7被爆炸炸得四分五裂";
            case PROJECTILE:
                if (damager != null) {
                    if (damager instanceof Player) {
                        Player shooter = (Player) damager;

                        double distance = shooter.getLocation().distance(player.getLocation());
                        int roundedDistance = (int) Math.round(distance);

                        return PREFIX + player.getName() + " §7被 §b" + shooter.getName() + " 精准命中 §8(§f" + roundedDistance + "米§8)";
                    } else if (damager instanceof Skeleton) {
                        return PREFIX + player.getName() + " §7被 §8Donk骷髅弓手 §7颗秒了";
                    } else if (damager instanceof Pillager) {
                        return PREFIX + player.getName() + " §7被 §8掠夺者 §7的弩箭钉在墙上";
                    } else if (damager instanceof Piglin) {
                        return PREFIX + player.getName() + " §7被 §6猪灵 §7的弩箭钉在墙上";
                    } else if (damager instanceof Blaze) {
                        return PREFIX + player.getName() + " §7被 §6烈焰人 §7的火焰风暴吞噬";
                    }
                }
                return PREFIX + player.getName() + " §7被远程武器猎杀";
            case FALL:
                int fallHeight = (int) player.getFallDistance();
                return PREFIX + player.getName() + " §7从 §6" + fallHeight + "格 §7高空摔成肉饼";
            case VOID:
                return PREFIX + player.getName() + " §7坠入了 §8虚空 §7，被黑暗永远吞噬";
            case DROWNING:
                return PREFIX + player.getName() + " §7试图 §3潜水逃脱 §7却没能浮出水面";
            case FIRE:
                return PREFIX + player.getName() + " §7被 §c火焰 §7吞噬成灰";
            case LAVA:
                return PREFIX + player.getName() + " §7挑战 §6岩浆洗礼 §7失败";
            case SUFFOCATION:
                return PREFIX + player.getName() + " §7被 §8墙体 §7缓慢碾碎";
            case POISON:
                return PREFIX + player.getName() + " §7被 §2剧毒 §7折磨至死";
            case HOT_FLOOR:
                if (damager instanceof Blaze) {
                    return PREFIX + player.getName() + " §7被 §6烈焰人 §7的火焰风暴吞噬";
                }
                return PREFIX + player.getName() + " §7在 §c烈焰 §7中涅槃失败";
            default:
                return PREFIX + player.getName() + " §7死翘翘了";
        }
    }

    private String EscaperDeathMessage(Player player) {
        final String PREFIX = "§f[☠] §b⚔";
        if (player.getLastDamageCause() == null) {
            return PREFIX + player.getName() + " 死翘翘了";
        }

        EntityDamageEvent damageEvent = player.getLastDamageCause();
        Entity damager = null;
        Projectile projectile = null;

        // 获取伤害来源
        if (damageEvent instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) damageEvent;
            Entity originalDamager = entityEvent.getDamager();

            // 处理抛射物
            if (originalDamager instanceof Projectile) {
                projectile = (Projectile) originalDamager;
                // 获取抛射物发射者
                if (projectile.getShooter() instanceof Entity) {
                    damager = (Entity) projectile.getShooter();
                }
            } else {
                damager = originalDamager;
            }
        }

        EntityDamageEvent.DamageCause cause = damageEvent.getCause();
        switch (cause) {
            case ENTITY_ATTACK:
                if (damager != null) {
                    if (damager instanceof Enderman) {
                        return PREFIX + player.getName() + " §7的装备被 §5末影人 §7缴械";
                    } else if (damager instanceof Spider) {
                        return PREFIX + player.getName() + " §7的陷阱被 §7毒蛛 §7反制";
                    } else if (damager instanceof CaveSpider) {
                        return PREFIX + player.getName() + " §7的陷阱被 §7毒蛛 §7反制";
                    } else if (damager instanceof Player) {
                        Player killer = (Player) damager;
                        String[] messages = {
                                PREFIX + player.getName() + " §7被 §c\uD83C\uDFF9" + killer.getName() + " §7终结了",
                                PREFIX + player.getName() + " §7与 §c\uD83C\uDFF9" + killer.getName() + " §7肉搏后落败",
                                PREFIX + player.getName() + " §7被 §c\uD83C\uDFF9" + killer.getName() + " §7打成重伤倒地",
                                PREFIX + player.getName() +  " §7的防线被 §c\uD83C\uDFF9" + killer.getName() + " §7彻底击破",
                                PREFIX + player.getName() + " §7在与 §c\uD83C\uDFF9" + killer.getName() + " §7的死斗中战败",
                                PREFIX + player.getName() + " §7被 §c\uD83C\uDFF9" + killer.getName() + " §7给嘎掉了"
                        };

                        // 增加 猎人 击杀熟练度
                        if (plugin.isFinalBattleMode()) { // 终章
                            plugin.getDataStorageManager().addProficiency(killer, plugin.getRankManager().getFinalBattle_HunterKillReward());
                        } else if (plugin.isVanillaHunterMode()) { // 原版
                            plugin.getDataStorageManager().addProficiency(killer, plugin.getRankManager().getOrdinaryBattle_HunterKillReward());
                        } else if (plugin.isSkillHunterMode()) { // 技能
                            plugin.getDataStorageManager().addProficiency(killer, plugin.getRankManager().getSkillBattle_HunterKillReward());
                        }

                        return messages[rand.nextInt(messages.length)];
                    } else if (damager instanceof WitherSkeleton) {
                        return PREFIX + player.getName() + " §7的护甲被 §7凋零骷髅 §7腐蚀击破";
                    } else if (damager instanceof Zombie) {
                        return PREFIX + player.getName() + " §7被 §7僵尸 §7爆操了";
                    } else if (damager instanceof IronGolem) {
                        return  PREFIX + player.getName() + "§7被 §f铁傀儡 §7击飞致死了";
                    } else if (damager instanceof PigZombie) {
                        return PREFIX + player.getName() + "§7被 §6僵尸诸人 §7击杀了";
                    } else if (damager instanceof MagmaCube) {
                        return PREFIX + player.getName() + "§7被 §6岩浆怪 §7压成了肉饼";
                    } else if (damager instanceof Vindicator) {
                        return PREFIX + player.getName() + "§7被 §6卫道士 §7砍成五马分尸";
                    } else if (damager instanceof PiglinBrute) {
                        return PREFIX + player.getName() + "§7被 §6猪灵蛮兵 §7砍成五马分尸";
                    } else if (damager instanceof PiglinBrute) {
                        return PREFIX + player.getName() + "§7被 §6疣猪兽 §7撞成狗了";
                    } else if (damager instanceof Wolf) {
                        return PREFIX + player.getName() + "§7被 §6狼 §7咬死了";
                    } else if (damager instanceof Drowned) {
                        return PREFIX + player.getName() + " §7的陷阱被 §7毒蛛 §7反制";
                    } else if (damager instanceof Drowned) {
                        return PREFIX + player.getName() + " §7被 §3溺尸 §7拖入了水下深渊";
                    } else if (damager instanceof Husk) {
                        return PREFIX + player.getName() + " §7被 §6尸壳 §7吞噬在沙漠风暴中";
                    } else if (damager instanceof Warden) {
                        return PREFIX + player.getName() + " §7被 §1守卫者 §7的愤怒撕成碎片";
                    }

                }
                return PREFIX + player.getName() + " §7在近战交锋中陨落";
            case ENTITY_EXPLOSION:
                if (damager instanceof Wither) {
                    return PREFIX + player.getName() + " §7被 §8凋零 §7的毁灭冲击波粉碎";
                } else if (damager instanceof Creeper) {
                    return PREFIX + player.getName() + " §7被 §a苦力怕 §7的爱心爆炸融化";
                }
                return PREFIX + player.getName() + " §7被爆炸炸得四分五裂";
            case PROJECTILE:
                if (damager != null) {
                    if (damager instanceof Player) {
                        Player shooter = (Player) damager;

                        double distance = shooter.getLocation().distance(player.getLocation());
                        int roundedDistance = (int) Math.round(distance);

                        return PREFIX + player.getName() + " §7被 §b" + shooter.getName() + " 精准命中 §8(§f" + roundedDistance + "米§8)";
                    } else if (damager instanceof Skeleton) {
                        return PREFIX + player.getName() + " §7被 §8Donk骷髅弓手 §7颗秒了";
                    } else if (damager instanceof Pillager) {
                        return PREFIX + player.getName() + " §7被 §8掠夺者 §7的弩箭钉在墙上";
                    } else if (damager instanceof Piglin) {
                        return PREFIX + player.getName() + " §7被 §6猪灵 §7的弩箭钉在墙上";
                    } else if (damager instanceof Blaze) {
                        return PREFIX + player.getName() + " §7被 §6烈焰人 §7的火焰风暴吞噬";
                    }
                }
                return PREFIX + player.getName() + " §7被远程武器猎杀";
            case FALL:
                int fallHeight = (int) player.getFallDistance();
                return PREFIX + player.getName() + " §7从 §6" + fallHeight + "格 §7高空摔成肉饼";
            case VOID:
                return PREFIX + player.getName() + " §7坠入了 §8虚空 §7，被黑暗永远吞噬";
            case DROWNING:
                return PREFIX + player.getName() + " §7试图 §3潜水逃脱 §7却没能浮出水面";
            case FIRE:
                return PREFIX + player.getName() + " §7被 §c火焰 §7吞噬成灰";
            case LAVA:
                return PREFIX + player.getName() + " §7挑战 §6岩浆洗礼 §7失败";
            case SUFFOCATION:
                return PREFIX + player.getName() + " §7被 §8墙体 §7缓慢碾碎";
            case POISON:
                return PREFIX + player.getName() + " §7被 §2剧毒 §7折磨至死";
            case HOT_FLOOR:
                if (damager instanceof Blaze) {
                    return PREFIX + player.getName() + " §7被 §6烈焰人 §7的火焰风暴吞噬";
                }
                return PREFIX + player.getName() + " §7在 §c烈焰 §7中涅槃失败";
            default:
                return PREFIX + player.getName() + " §7死翘翘了";
        }
    }

}
