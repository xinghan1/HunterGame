package com.huntergame.command;

import com.huntergame.HunterGame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class HunterGameCommand implements CommandExecutor, TabCompleter {
   private static final List<String> SUB_COMMANDS = Arrays.asList("help", "setlobby", "startgame", "reload", "newseason", "refreshtier", "saveprofession", "editprofession");
   private final HunterGame plugin;

   public HunterGameCommand(HunterGame plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (command.getName().equalsIgnoreCase("huntergame") || command.getName().equalsIgnoreCase("hg")) {
         if (args.length <= 0) {
            this.sendCommandHelp(sender);
            return true;
         } else if (args[0].equalsIgnoreCase("setlobby")) {
            if (!sender.hasPermission("huntergame.setlobby")) {
               sender.sendMessage(this.plugin.getMessage("no_permission", "&c\u4f60\u6ca1\u6709\u6743\u9650\u6267\u884c\u6b64\u547d\u4ee4\uff01"));
               return true;
            } else {
               (new SetLobbyCommand(this.plugin)).setLobby(sender);
               this.plugin.loadConfig();
               return true;
            }
         } else if (args[0].equalsIgnoreCase("startgame")) {
            if (!sender.hasPermission("huntergame.startgame")) {
               sender.sendMessage(this.plugin.getMessage("no_permission", "&c\u4f60\u6ca1\u6709\u6743\u9650\u6267\u884c\u6b64\u547d\u4ee4\uff01"));
               return true;
            } else {
               this.plugin.getStartGameCommand().startGame();
               sender.sendMessage(this.plugin.getMessage("game_started", "&c\u6e38\u620f\u5df2\u5f00\u59cb\uff01"));
               this.plugin.getStartGameCommand().cancelCountdown();
               return true;
            }
         } else if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("huntergame.reload")) {
               sender.sendMessage(this.plugin.getMessage("no_permission", "&c\u4f60\u6ca1\u6709\u6743\u9650\u6267\u884c\u6b64\u547d\u4ee4\uff01"));
               return true;
            } else {
               this.plugin.reloadPluginConfig();
               sender.sendMessage(this.plugin.getMessage("reload", "&aHunterGame \u914d\u7f6e\u6587\u4ef6\u5df2\u6210\u529f\u91cd\u8f7d\uff01"));
               return true;
            }
         } else if (!args[0].equalsIgnoreCase("saveprofession") && !args[0].equalsIgnoreCase("savejob")) {
            if (!args[0].equalsIgnoreCase("editprofession") && !args[0].equalsIgnoreCase("editjob")) {
               if (args[0].equalsIgnoreCase("help")) {
                  if (!sender.hasPermission("huntergame.help")) {
                     sender.sendMessage(this.plugin.getMessage("version", "&cHunterGame &7%version%").replace("%version%", this.plugin.getDescription().getVersion()));
                     return true;
                  } else {
                     this.sendCommandHelp(sender);
                     return true;
                  }
               } else if (args[0].equalsIgnoreCase("refreshtier")) {
                  if (!sender.hasPermission("huntergame.refreshtier")) {
                     sender.sendMessage(this.plugin.getMessage("no_permission", "&c\u4f60\u6ca1\u6709\u6743\u9650\u6267\u884c\u6b64\u547d\u4ee4\uff01"));
                     return true;
                  } else if (this.plugin.getHunterGamePlaceholder() == null) {
                     sender.sendMessage(this.plugin.getMessage("placeholder_unavailable", "&cPlaceholderAPI \u6269\u5c55\u672a\u52a0\u8f7d\uff0c\u65e0\u6cd5\u5237\u65b0\u6392\u540d\uff01"));
                     return true;
                  } else {
                     Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                        this.plugin.getHunterGamePlaceholder().refreshAllTiers();
                        sender.sendMessage(this.plugin.getMessage("tier_refresh_success", "&a\u5168\u670d\u719f\u7ec3\u5ea6\u6392\u540d\u5df2\u5237\u65b0\uff01"));
                     });
                     return true;
                  }
               } else if (args[0].equalsIgnoreCase("newseason")) {
                  if (!sender.hasPermission("huntergame.newseason")) {
                     sender.sendMessage(this.plugin.getMessage("no_permission", "&c\u4f60\u6ca1\u6709\u6743\u9650\u6267\u884c\u6b64\u547d\u4ee4\uff01"));
                     return true;
                  } else if (args.length < 2) {
                     sender.sendMessage(this.plugin.getMessage("newseason_usage", "&c\u7528\u6cd5: /hg newseason <\u8d5b\u5b63ID>"));
                     sender.sendMessage(this.plugin.getMessage("newseason_example", "&c\u4f8b\u5982: /hg newseason S2 \u6216 /hg newseason 2024_\u590f\u5b63"));
                     return true;
                  } else {
                     String newSeasonId = args[1];
                     boolean success = this.plugin.getSeasonManager().startNewSeason(newSeasonId);
                     if (success) {
                        sender.sendMessage(this.plugin.getMessage("new_season_started", "&a\u65b0\u8d5b\u5b63 '%season%' \u5df2\u6210\u529f\u5f00\u59cb\uff01").replace("%season%", newSeasonId));
                     } else {
                        sender.sendMessage(this.plugin.getMessage("new_season_error", "&c\u65b0\u8d5b\u5b63\u521b\u5efa\u5931\u8d25\uff01\u8bf7\u68c0\u67e5\u63a7\u5236\u53f0\u65e5\u5fd7\u6216\u786e\u4fdd\u8d5b\u5b63ID\u672a\u88ab\u4f7f\u7528\u3002"));
                     }

                     return true;
                  }
               } else {
                  return false;
               }
            } else if (!sender.hasPermission("huntergame.editprofession")) {
               sender.sendMessage(this.plugin.getMessage("no_permission", "&c\u4f60\u6ca1\u6709\u6743\u9650\u6267\u884c\u6b64\u547d\u4ee4\uff01"));
               return true;
            } else if (!(sender instanceof Player)) {
               sender.sendMessage(this.plugin.getMessage("player_only_command", "&c\u8be5\u547d\u4ee4\u53ea\u80fd\u7531\u73a9\u5bb6\u6267\u884c\u3002"));
               return true;
            } else if (args.length < 2) {
               sender.sendMessage(this.plugin.getMessage("editprofession_usage", "&c\u7528\u6cd5: /hg editprofession <\u804c\u4e1aID>"));
               sender.sendMessage(this.plugin.getMessage("editprofession_example", "&7\u793a\u4f8b: /hg editprofession pilot"));
               return true;
            } else {
               Player player = (Player)sender;
               this.plugin.getFinalBattleProfessionManager().openProfessionItemEditor(player, args[1]);
               return true;
            }
         } else if (!sender.hasPermission("huntergame.saveprofession")) {
            sender.sendMessage(this.plugin.getMessage("no_permission", "&c\u4f60\u6ca1\u6709\u6743\u9650\u6267\u884c\u6b64\u547d\u4ee4\uff01"));
            return true;
         } else if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.getMessage("player_only_command", "&c\u8be5\u547d\u4ee4\u53ea\u80fd\u7531\u73a9\u5bb6\u6267\u884c\u3002"));
            return true;
         } else if (args.length < 3) {
            sender.sendMessage(this.plugin.getMessage("saveprofession_usage", "&c\u7528\u6cd5: /hg saveprofession <\u804c\u4e1aID> <\u663e\u793a\u540d>"));
            sender.sendMessage(this.plugin.getMessage("saveprofession_example", "&7\u793a\u4f8b: /hg saveprofession pilot &b\u98de\u884c\u5bb6"));
            return true;
         } else {
            Player player = (Player)sender;
            this.plugin.getFinalBattleProfessionManager().saveProfessionFromInventory(player, args[1], this.joinArgs(args, 2));
            return true;
         }
      } else {
         return false;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!command.getName().equalsIgnoreCase("huntergame") && !command.getName().equalsIgnoreCase("hg")) {
         return Collections.emptyList();
      } else if (args.length == 1) {
         return this.filterByPermissionAndPrefix(sender, SUB_COMMANDS, args[0]);
      } else {
         String subCommand = args[0].toLowerCase(Locale.ROOT);
         if (args.length == 2) {
            if ("newseason".equals(subCommand)) {
               return this.filterByPrefix(Collections.singletonList("S2"), args[1]);
            }

            if ("saveprofession".equals(subCommand) || "savejob".equals(subCommand)) {
               return this.filterByPrefix(Collections.singletonList("profession_id"), args[1]);
            }

            if ("editprofession".equals(subCommand) || "editjob".equals(subCommand)) {
               return this.filterByPrefix(this.plugin.getFinalBattleProfessionManager().getProfessionIds(), args[1]);
            }
         }

         if (args.length != 3 || !"saveprofession".equals(subCommand) && !"savejob".equals(subCommand)) {
            return Collections.emptyList();
         } else {
            return this.filterByPrefix(Collections.singletonList("&b\u804c\u4e1a\u663e\u793a\u540d"), args[2]);
         }
      }
   }

   private List<String> filterByPermissionAndPrefix(CommandSender sender, List<String> candidates, String prefix) {
      List<String> permitted = new ArrayList();

      for(String candidate : candidates) {
         if (this.canUseSubCommand(sender, candidate)) {
            permitted.add(candidate);
         }
      }

      return this.filterByPrefix(permitted, prefix);
   }

   private boolean canUseSubCommand(CommandSender sender, String subCommand) {
      switch (subCommand) {
         case "setlobby" -> {
            return sender.hasPermission("huntergame.setlobby");
         }
         case "startgame" -> {
            return sender.hasPermission("huntergame.startgame");
         }
         case "reload" -> {
            return sender.hasPermission("huntergame.reload");
         }
         case "newseason" -> {
            return sender.hasPermission("huntergame.newseason");
         }
         case "refreshtier" -> {
            return sender.hasPermission("huntergame.refreshtier");
         }
         case "saveprofession" -> {
            return sender.hasPermission("huntergame.saveprofession");
         }
         case "editprofession" -> {
            return sender.hasPermission("huntergame.editprofession");
         }
         case "help" -> {
            return sender.hasPermission("huntergame.help");
         }
         default -> {
            return false;
         }
      }
   }

   private List<String> filterByPrefix(List<String> candidates, String prefix) {
      String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
      List<String> result = new ArrayList();

      for(String candidate : candidates) {
         if (candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
            result.add(candidate);
         }
      }

      return result;
   }

   private void sendCommandHelp(CommandSender sender) {
      String version = this.plugin.getDescription().getVersion();

      for(String line : this.plugin.getMessageList("command_help", this.getDefaultCommandHelp())) {
         sender.sendMessage(line.replace("%version%", version));
      }

   }

   private List<String> getDefaultCommandHelp() {
      return Arrays.asList("&cHunterGame &7%version%", "&e========================", "&a/hg setlobby &c\u8bbe\u7f6e\u6e38\u620f\u5927\u5385", "&a/hg startgame &c\u5f3a\u5236\u5f00\u59cb\u6e38\u620f", "&a/hg reload &b\u91cd\u8f7d\u914d\u7f6e", "&a/hg newseason &b\u5f00\u542f\u65b0\u7684\u8d5b\u5b63", "&a/hg refreshtier &b\u7acb\u5373\u5237\u65b0\u5168\u670d\u6392\u540d", "&b/hg saveprofession <\u804c\u4e1aID> <\u663e\u793a\u540d> &f\u4fdd\u5b58\u7ec8\u7ae0\u804c\u4e1a", "&b/hg editprofession <\u804c\u4e1aID> &f\u7f16\u8f91\u7ec8\u7ae0\u804c\u4e1a\u7269\u54c1", "&e========================");
   }

   private String joinArgs(String[] args, int startIndex) {
      StringBuilder builder = new StringBuilder();

      for(int i = startIndex; i < args.length; ++i) {
         if (builder.length() > 0) {
            builder.append(' ');
         }

         builder.append(args[i]);
      }

      return builder.toString();
   }
}
