package com.huntergame.command;

import com.huntergame.HunterGame;
import com.huntergame.SetLobbyCommand;
import com.huntergame.game.StartGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class SetCommand implements CommandExecutor {

    private final HunterGame plugin;

    public SetCommand(HunterGame plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String usageMessage = (String) plugin.getDescription().getCommands().get("huntergame").get("usage");
        usageMessage = ChatColor.translateAlternateColorCodes('&', usageMessage);  // 处理 & 转义为颜色代码
        sender.sendMessage(usageMessage);

        if (command.getName().equalsIgnoreCase("huntergame") || command.getName().equalsIgnoreCase("hg")) {
            // 检查是否有子命令
            if (args.length > 0) {
                // 子命令：setlobby
                if (args[0].equalsIgnoreCase("setlobby")) {
                    if (!sender.hasPermission("huntergame.setlobby")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }
                    // 执行设置大厅位置的逻辑
                    new SetLobbyCommand(plugin).setLobby(sender);
                    plugin.loadConfig();
                    return true;
                }

                // 子命令：startgame
                if (args[0].equalsIgnoreCase("startgame")) {
                    if (!sender.hasPermission("huntergame.startgame")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }
                    // 执行启动游戏的逻辑
                    new StartGame(plugin).startGame();
                    sender.sendMessage(plugin.getMessage("game_started", "&c游戏已开始！"));
                    plugin.getStartGameCommand().cancelCountdown();
                    return true;
                }

                // 子命令：reload
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("huntergame.reload")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }
                    // 执行重载配置的逻辑
                    plugin.reloadPluginConfig();
                    sender.sendMessage(plugin.getMessage("reload", "&aHunterGame 配置文件已成功重载！"));
                    return true;
                }

                // 子命令：reload
                if (args[0].equalsIgnoreCase("help")) {
                    if (!sender.hasPermission("huntergame.help")) {
                        sender.sendMessage(plugin.getMessage("version", "&cHunterGame &7%version%")
                                .replace("%version%", plugin.getDescription().getVersion())
                        );
                        return true;
                    }
                    sender.sendMessage(plugin.getMessage("version", "&cHunterGame &7%version%")
                            .replace("%version%", plugin.getDescription().getVersion())
                    );
                    sender.sendMessage(plugin.getMessage("command_help_0", "&e========================"));
                    sender.sendMessage(plugin.getMessage("command_help_1", "&a/hg setlobby &b设置游戏大厅"));
                    sender.sendMessage(plugin.getMessage("command_help_2", "&a/hg startgame &b强制开始游戏"));
                    sender.sendMessage(plugin.getMessage("command_help_3", "&a/hg reload &b重载配置"));
                    sender.sendMessage(plugin.getMessage("command_help_4", "&a/hg newseason &b开启新的赛季"));
                    sender.sendMessage(plugin.getMessage("command_help_5", "&a/hg refreshtier &b立即刷新全服排名"));
                    sender.sendMessage(plugin.getMessage("command_help_0", "&e========================"));
                    return true;
                }

                // 子命令：refreshtier
                if (args[0].equalsIgnoreCase("refreshtier")) {
                    if (!sender.hasPermission("huntergame.refreshtier")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }
                    if (plugin.getHunterGamePlaceholder() == null) {
                        sender.sendMessage(ChatColor.RED + "PlaceholderAPI 扩展未加载，无法刷新排名！");
                        return true;
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getHunterGamePlaceholder().refreshAllTiers();
                        sender.sendMessage(ChatColor.GREEN + "全服熟练度排名已刷新！");
                    });
                    return true;
                }

                if (args[0].equalsIgnoreCase("newseason")) {
                    if (!sender.hasPermission("huntergame.newseason")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }

                    // 检查是否提供了赛季ID
                    if (args.length < 2) {
                        sender.sendMessage("&c用法: /hg newseason <赛季ID>");
                        sender.sendMessage("&c例如: /hg newseason S2 或 /hg newseason 2024_夏季");
                        return true;
                    }

                    String newSeasonId = args[1];

                    // 执行开始新赛季的方法
                    boolean success = plugin.getSeasonManager().startNewSeason(newSeasonId);

                    if (success) {
                        sender.sendMessage(plugin.getMessage("new_season_started", "&a新赛季 '" + newSeasonId + "' 已成功开始！"));

                    } else {
                        sender.sendMessage(plugin.getMessage("new_season_error", "&c新赛季创建失败！请检查控制台日志或确保赛季ID未被使用。"));
                    }

                    return true;

                }
            } else {
                // 如果没有子命令，发送使用帮助信息
                sender.sendMessage(plugin.getMessage("command_help_0", "&e========================"));
                sender.sendMessage(plugin.getMessage("command_help_1", "&a/hg setlobby &c设置游戏大厅"));
                sender.sendMessage(plugin.getMessage("command_help_2", "&a/hg startgame &c强制开始游戏"));
                sender.sendMessage(plugin.getMessage("command_help_3", "&a/hg reload &b重载配置"));
                sender.sendMessage(plugin.getMessage("command_help_4", "&a/hg newseason &b开启新的赛季"));
                sender.sendMessage(plugin.getMessage("command_help_5", "&a/hg refreshtier &b立即刷新全服排名"));
                sender.sendMessage(plugin.getMessage("command_help_0", "&e========================"));
                return false;  // 返回 false 会让 Bukkit 自动显示命令的用法
            }
        }


        return false;
    }


}
