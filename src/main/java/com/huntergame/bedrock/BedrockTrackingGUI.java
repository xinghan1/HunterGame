package com.huntergame.bedrock;

import com.huntergame.HunterGame;
import com.huntergame.tracking.HunterTracker;
import com.huntergame.util.FloodgateSupport;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.List;

public class BedrockTrackingGUI {

    public static void openBedrockTrackingMenu(HunterGame plugin, HunterTracker tracker, Player player) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(plugin.getMessage("bedrock_tracking_title", "猎人追踪器"))
                .content(plugin.getMessage("bedrock_tracking_content", "请选择操作："))
                .button(plugin.getMessage("bedrock_tracking_teleport_button", "&a传送到队友\n&7消耗生命值快速支援"))
                .button(plugin.getMessage("bedrock_tracking_switch_button", "&e切换指南针目标\n&7追踪最近逃生者/队友"))
                .button(plugin.getMessage("bedrock_close_button", "&c关闭菜单"));

        builder.validResultHandler(response -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    switch (response.clickedButtonId()) {
                        case 0: // 传送
                            if (tracker.isEscaperNearby(player, tracker.DETECTION_DISTANCE)) {
                                player.sendMessage(plugin.getMessage("nearby_escape", "&c附近有逃生者，无法传送！"));
                                return;
                            }
                            openBedrockTeammateList(plugin, tracker, player);
                            break;
                        case 1: // 切换追踪
                            tracker.switchToHunterTrackingTarget(player);
                            break;
                        case 2: // 关闭
                            break;
                    }
                }
            }.runTask(plugin);
        });
        FloodgateSupport.sendForm(player, builder);
    }

    // 基岩版：队友列表
    public static void openBedrockTeammateList(HunterGame plugin, HunterTracker tracker, Player player) {
        List<Player> teammates = plugin.getHunters();
        teammates.remove(player);

        if (teammates.isEmpty()) {
            player.sendMessage(plugin.getMessage("no_teammates_to_teleport", "&c没有可传送的队友！"));
            return;
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(plugin.getMessage("bedrock_teammate_list_title", "选择传送目标"))
                .content(plugin.getMessage("bedrock_teammate_list_content", "点击队友头像进行传送（消耗 %health% 血量）：")
                        .replace("%health%", String.valueOf(tracker.DEDUCT_HEALTH)));

        // 存储队友列表顺序，以便回调时对应
        List<Player> validTeammates = new ArrayList<>();

        for (Player teammate : teammates) {
            if (tracker.isTeleportableHunterTeammate(player, teammate)) {
                builder.button(plugin.getMessage("bedrock_teammate_button", "&e%player%\n&7点击传送")
                        .replace("%player%", teammate.getName()));
                validTeammates.add(teammate);
            }
        }

        builder.button(plugin.getMessage("bedrock_cancel_button", "&c取消"));

        builder.validResultHandler(response -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    int id = response.clickedButtonId();

                    // 检查是否点了取消 (ID 等于列表长度时为最后一个按钮)
                    if (id >= validTeammates.size()) return;

                    Player target = validTeammates.get(id);

                    // 执行传送逻辑
                    if (tracker.isOnCooldown(player)) {
                        long timeLeft = tracker.getCooldownTimeLeft(player);
                        player.sendMessage(plugin.getMessage("waiting_countdown_teleport", "&c冷却中: " + timeLeft + "s"));
                        return;
                    }

                    if (tracker.isTeleportableHunterTeammate(player, target)) {
                        // 调用 Tracker 的公共传送方法，统一逻辑
                        tracker.performTeleport(player, target);
                    } else {
                        tracker.sendTeammateUnavailableMessage(player, target);
                    }
                }
            }.runTask(plugin);
        });

        FloodgateSupport.sendForm(player, builder);
    }
}
