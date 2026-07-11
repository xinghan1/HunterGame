package com.huntergame.command;

import com.huntergame.HunterGame;
import com.huntergame.command.SetLobbyCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HunterGameCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "help",
            "setlobby",
            "startgame",
            "reload",
            "newseason",
            "refreshtier",
            "saveprofession",
            "editprofession",
            "recipe"
    );

    private final HunterGame plugin;

    public HunterGameCommand(HunterGame plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
                    plugin.getStartGameCommand().startGame();
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

                if (args[0].equalsIgnoreCase("saveprofession") || args[0].equalsIgnoreCase("savejob")) {
                    if (!sender.hasPermission("huntergame.saveprofession")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(plugin.getMessage("player_only_command", "&c该命令只能由玩家执行。"));
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(plugin.getMessage("saveprofession_usage", "&c用法: /hg saveprofession <职业ID> <显示名>"));
                        sender.sendMessage(plugin.getMessage("saveprofession_example", "&7示例: /hg saveprofession pilot &b飞行家"));
                        return true;
                    }

                    Player player = (Player) sender;
                    plugin.getFinalBattleProfessionManager().saveProfessionFromInventory(player, args[1], joinArgs(args, 2));
                    return true;
                }

                if (args[0].equalsIgnoreCase("editprofession") || args[0].equalsIgnoreCase("editjob")) {
                    if (!sender.hasPermission("huntergame.editprofession")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(plugin.getMessage("player_only_command", "&c该命令只能由玩家执行。"));
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(plugin.getMessage("editprofession_usage", "&c用法: /hg editprofession <职业ID>"));
                        sender.sendMessage(plugin.getMessage("editprofession_example", "&7示例: /hg editprofession pilot"));
                        return true;
                    }

                    Player player = (Player) sender;
                    plugin.getFinalBattleProfessionManager().openProfessionItemEditor(player, args[1]);
                    return true;
                }

                if (args[0].equalsIgnoreCase("recipe")) {
                    if (!sender.hasPermission("huntergame.recipeadmin")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(plugin.getMessage("player_only_command", "&c该命令只能由玩家执行。"));
                        return true;
                    }

                    Player player = (Player) sender;
                    String recipeId = args.length >= 2 ? args[1] : null;
                    plugin.getPermissionRecipeManager().openRecipeEditor(player, recipeId);
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
                    sendCommandHelp(sender);
                    return true;
                }

                // 子命令：refreshtier
                if (args[0].equalsIgnoreCase("refreshtier")) {
                    if (!sender.hasPermission("huntergame.refreshtier")) {
                        sender.sendMessage(plugin.getMessage("no_permission", "&c你没有权限执行此命令！"));
                        return true;
                    }
                    if (plugin.getHunterGamePlaceholder() == null) {
                        sender.sendMessage(plugin.getMessage("placeholder_unavailable", "&cPlaceholderAPI 扩展未加载，无法刷新排名！"));
                        return true;
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getHunterGamePlaceholder().refreshAllTiers();
                        sender.sendMessage(plugin.getMessage("tier_refresh_success", "&a全服熟练度排名已刷新！"));
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
                        sender.sendMessage(plugin.getMessage("newseason_usage", "&c用法: /hg newseason <赛季ID>"));
                        sender.sendMessage(plugin.getMessage("newseason_example", "&c例如: /hg newseason S2 或 /hg newseason 2024_夏季"));
                        return true;
                    }

                    String newSeasonId = args[1];

                    plugin.getSeasonManager().startNewSeasonAsync(newSeasonId).thenAccept(success ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (success) {
                                    sender.sendMessage(plugin.getMessage("new_season_started", "&a新赛季 '%season%' 已成功开始！")
                                            .replace("%season%", newSeasonId));
                                } else {
                                    sender.sendMessage(plugin.getMessage("new_season_error", "&c新赛季创建失败！请检查控制台日志或确保赛季ID未被使用。"));
                                }
                            })
                    );

                    return true;

                }
            } else {
                // 如果没有子命令，发送使用帮助信息
                sendCommandHelp(sender);
                return true;
            }
        }


        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("huntergame") && !command.getName().equalsIgnoreCase("hg")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterByPermissionAndPrefix(sender, SUB_COMMANDS, args[0]);
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if ("newseason".equals(subCommand)) {
                return filterByPrefix(Collections.singletonList("S2"), args[1]);
            }
            if ("saveprofession".equals(subCommand) || "savejob".equals(subCommand)) {
                return filterByPrefix(Collections.singletonList("profession_id"), args[1]);
            }
            if ("editprofession".equals(subCommand) || "editjob".equals(subCommand)) {
                return filterByPrefix(plugin.getFinalBattleProfessionManager().getProfessionIds(), args[1]);
            }
            if ("recipe".equals(subCommand)) {
                List<String> ids = new ArrayList<>(plugin.getPermissionRecipeManager().getRecipeIds());
                ids.add("hand_item_id");
                return filterByPrefix(ids, args[1]);
            }
        }

        if (args.length == 3 && ("saveprofession".equals(subCommand) || "savejob".equals(subCommand))) {
            return filterByPrefix(Collections.singletonList("&b职业显示名"), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterByPermissionAndPrefix(CommandSender sender, List<String> candidates, String prefix) {
        List<String> permitted = new ArrayList<>();
        for (String candidate : candidates) {
            if (canUseSubCommand(sender, candidate)) {
                permitted.add(candidate);
            }
        }
        return filterByPrefix(permitted, prefix);
    }

    private boolean canUseSubCommand(CommandSender sender, String subCommand) {
        switch (subCommand) {
            case "setlobby":
                return sender.hasPermission("huntergame.setlobby");
            case "startgame":
                return sender.hasPermission("huntergame.startgame");
            case "reload":
                return sender.hasPermission("huntergame.reload");
            case "newseason":
                return sender.hasPermission("huntergame.newseason");
            case "refreshtier":
                return sender.hasPermission("huntergame.refreshtier");
            case "saveprofession":
                return sender.hasPermission("huntergame.saveprofession");
            case "editprofession":
                return sender.hasPermission("huntergame.editprofession");
            case "recipe":
                return sender.hasPermission("huntergame.recipeadmin");
            case "help":
                return sender.hasPermission("huntergame.help");
            default:
                return false;
        }
    }

    private List<String> filterByPrefix(List<String> candidates, String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private void sendCommandHelp(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        for (String line : plugin.getMessageList("command_help", getDefaultCommandHelp())) {
            sender.sendMessage(line.replace("%version%", version));
        }
    }

    private List<String> getDefaultCommandHelp() {
        return Arrays.asList(
                "&cHunterGame &7%version%",
                "&e========================",
                "&a/hg setlobby &c设置游戏大厅",
                "&a/hg startgame &c强制开始游戏",
                "&a/hg reload &b重载配置",
                "&a/hg newseason &b开启新的赛季",
                "&a/hg refreshtier &b立即刷新全服排名",
                "&b/hg saveprofession <职业ID> <显示名> &f保存终章职业",
                "&b/hg editprofession <职业ID> &f编辑终章职业物品",
                "&b/hg recipe [配方ID] &f编辑权限合成配方",
                "&e========================"
        );
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

}

