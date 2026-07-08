package com.huntergame.scoreboard;

import com.huntergame.HunterGame;
import com.xigua.baseAPI.BaseAPI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public class HunterScoreboardManager {
   private final HunterGame plugin;
   private long finalBattleStartTime;
   private double dragonHealth;
   private final Map<UUID, Scoreboard> waitingBoards = new HashMap();
   private final Map<UUID, Scoreboard> gameBoards = new HashMap();
   private final Map<UUID, Scoreboard> finalBattleBoards = new HashMap();
   private final boolean useBaseAPI;
   private final boolean placeholderApiEnabled;
   private final BaseAPI baseAPI;
   private String waitingTitle;
   private String gameTitle;
   private String finalBattleTitle;
   private List<String> waitingLines;
   private List<String> gameLines;
   private List<String> finalBattleLines;

   public HunterScoreboardManager(HunterGame plugin) {
      this.plugin = plugin;
      this.baseAPI = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
      this.useBaseAPI = this.baseAPI != null;
      this.placeholderApiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
      this.reload();
   }

   public void reload() {
      this.waitingTitle = this.plugin.getConfig().getString("scoreboard.waiting.title", "");
      this.gameTitle = this.plugin.getConfig().getString("scoreboard.game.title", "");
      this.finalBattleTitle = this.plugin.getConfig().getString("scoreboard.final_battle.title", "");
      this.waitingLines = this.plugin.getConfig().getStringList("scoreboard.waiting.lines");
      this.gameLines = this.plugin.getConfig().getStringList("scoreboard.game.lines");
      this.finalBattleLines = this.plugin.getConfig().getStringList("scoreboard.final_battle.lines");
   }

   private String replaceCommon(Player player, String text, ScoreboardContext context) {
      text = ChatColor.translateAlternateColorCodes('&', text).replace("%players%", String.valueOf(context.players)).replace("%hunters%", String.valueOf(context.hunters)).replace("%escapers%", String.valueOf(context.escapers)).replace("%time%", String.valueOf(this.plugin.getGameTime())).replace("%dragon_health%", String.valueOf(this.dragonHealth)).replace("%finalbattle_time%", this.getFinalBattleTime()).replace("%finalbattle_remaining%", this.getFinalBattleRemainingTime());
      if (this.placeholderApiEnabled) {
         text = PlaceholderAPI.setPlaceholders(player, text);
      }

      return text;
   }

   private String getFinalBattleTime() {
      if (this.finalBattleStartTime == 0L) {
         return "00:00";
      } else {
         long elapsed = (System.currentTimeMillis() - this.finalBattleStartTime) / 1000L;
         return String.format("%02d:%02d", elapsed / 60L, elapsed % 60L);
      }
   }

   private String getFinalBattleRemainingTime() {
      if (this.finalBattleStartTime == 0L) {
         return "30:00";
      } else {
         long elapsed = (System.currentTimeMillis() - this.finalBattleStartTime) / 1000L;
         long remain = Math.max(0L, 1800L - elapsed);
         return String.format("%02d:%02d", remain / 60L, remain % 60L);
      }
   }

   private void sendViaBaseAPI(Player player, String title, List<String> lines, ScoreboardContext context) {
      if (this.baseAPI != null) {
         Map<String, Object> eventData = new HashMap<>();
         eventData.put("title", ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', title)));
         List<String> order = new ArrayList<>();
         Map<String, String> text_dict = new LinkedHashMap<>();

         for(int i = 0; i < lines.size(); ++i) {
            String key = String.valueOf(i + 1);
            order.add(key);
            text_dict.put(key, this.replaceCommon(player, lines.get(i), context));
         }

         eventData.put("order", order);
         eventData.put("text_dict", text_dict);
         this.baseAPI.notifyToClient(player, "Xigua_common", "main", "SetScoreboard", eventData);
      }
   }

   private void sendViaBukkit(Player player, String sbName, String title, List<String> lines, Map<UUID, Scoreboard> cache, ScoreboardContext context) {
      ScoreboardManager manager = Bukkit.getScoreboardManager();
      if (manager != null) {
         UUID playerId = player.getUniqueId();
         Scoreboard board = cache.getOrDefault(playerId, manager.getNewScoreboard());
         Objective obj = board.getObjective(sbName);
         if (obj == null) {
            obj = board.registerNewObjective(sbName, Criteria.DUMMY, ChatColor.translateAlternateColorCodes('&', title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
         } else {
            for (String entry : board.getEntries()) {
               board.resetScores(entry);
            }
         }

         for(int i = 0; i < lines.size(); ++i) {
            String text = this.replaceCommon(player, lines.get(i), context);
            obj.getScore(text).setScore(lines.size() - i);
         }

         player.setScoreboard(board);
         cache.put(playerId, board);
      }
   }

   public void updateWaitingBoard(Player p) {
      ScoreboardContext context = this.createContext();
      if (this.useBaseAPI) {
         this.sendViaBaseAPI(p, this.waitingTitle, this.waitingLines, context);
      } else {
         this.sendViaBukkit(p, "waiting", this.waitingTitle, this.waitingLines, this.waitingBoards, context);
      }

   }

   public void updateGameBoard(Player p) {
      ScoreboardContext context = this.createContext();
      if (this.useBaseAPI) {
         this.sendViaBaseAPI(p, this.gameTitle, this.gameLines, context);
      } else {
         this.sendViaBukkit(p, "game", this.gameTitle, this.gameLines, this.gameBoards, context);
      }

   }

   public void updateFinalBattleBoard(Player p) {
      ScoreboardContext context = this.createContext();
      if (this.useBaseAPI) {
         this.sendViaBaseAPI(p, this.finalBattleTitle, this.finalBattleLines, context);
      } else {
         this.sendViaBukkit(p, "final_battle", this.finalBattleTitle, this.finalBattleLines, this.finalBattleBoards, context);
      }

   }

   private ScoreboardContext createContext() {
      return new ScoreboardContext(Bukkit.getOnlinePlayers().size(), this.plugin.getHunters().size(), this.plugin.getEscapers().size());
   }

   public void setDragonHealth(double dragonHealth) {
      this.dragonHealth = dragonHealth;
   }

   public void setFinalBattleStartTime(long time) {
      this.finalBattleStartTime = time;
   }

   private static final class ScoreboardContext {
      private final int players;
      private final int hunters;
      private final int escapers;

      private ScoreboardContext(int players, int hunters, int escapers) {
         this.players = players;
         this.hunters = hunters;
         this.escapers = escapers;
      }
   }
}
