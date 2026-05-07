package com.huntergame.scoreboard;

import com.huntergame.HunterGame;
import com.xigua.baseAPI.BaseAPI;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

public class HunterScoreboardManager {

    private final HunterGame plugin;

    // 终局之战数据
    private long finalBattleStartTime;
    private double dragonHealth;

    // 传统 Bukkit 计分板缓存
    private final Map<UUID, Scoreboard> waitingBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> gameBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> finalBattleBoards = new HashMap<>();

    private final boolean useBaseAPI;
    private final BaseAPI baseAPI;
    private String waitingTitle;
    private String gameTitle;
    private String finalBattleTitle;
    private List<String> waitingLines;
    private List<String> gameLines;
    private List<String> finalBattleLines;


    public HunterScoreboardManager(HunterGame plugin) {
        this.plugin = plugin;
        this.baseAPI = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
        this.useBaseAPI = baseAPI != null;
        reload();
    }

    public void reload() {
        waitingTitle = plugin.getConfig().getString("scoreboard.waiting.title", "");
        gameTitle = plugin.getConfig().getString("scoreboard.game.title", "");
        finalBattleTitle = plugin.getConfig().getString("scoreboard.final_battle.title", "");
        waitingLines = plugin.getConfig().getStringList("scoreboard.waiting.lines");
        gameLines = plugin.getConfig().getStringList("scoreboard.game.lines");
        finalBattleLines = plugin.getConfig().getStringList("scoreboard.final_battle.lines");
    }


    /* -----------------------------------
     *   通用占位符替换工具
     * ----------------------------------- */
    private String replaceCommon(Player player, String text, ScoreboardContext context) {
        text = ChatColor.translateAlternateColorCodes('&', text)
                .replace("%players%", String.valueOf(context.players))
                .replace("%hunters%", String.valueOf(context.hunters))
                .replace("%escapers%", String.valueOf(context.escapers))
                .replace("%time%", String.valueOf(plugin.getGameTime()))
                .replace("%dragon_health%", String.valueOf(dragonHealth))
                .replace("%finalbattle_time%", getFinalBattleTime())
                .replace("%finalbattle_remaining%", getFinalBattleRemainingTime());

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }


    private String getFinalBattleTime() {
        if (finalBattleStartTime == 0) return "00:00";
        long elapsed = (System.currentTimeMillis() - finalBattleStartTime) / 1000;
        return String.format("%02d:%02d", elapsed / 60, elapsed % 60);
    }

    private String getFinalBattleRemainingTime() {
        if (finalBattleStartTime == 0) return "30:00";
        long elapsed = (System.currentTimeMillis() - finalBattleStartTime) / 1000;
        long remain = Math.max(0, 30 * 60 - elapsed);
        return String.format("%02d:%02d", remain / 60, remain % 60);
    }

    private void sendViaBaseAPI(Player player, String title, List<String> lines, ScoreboardContext context) {
        if (baseAPI == null) {
            return;
        }
        Map<String, Object> eventData = new HashMap<>();

        // 标题
        eventData.put("title", ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', title)));

        List<String> order = new ArrayList<>();
        Map<String, String> text_dict = new LinkedHashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String key = String.valueOf(i + 1);
            order.add(key);
            text_dict.put(key, replaceCommon(player, lines.get(i), context));
        }
        eventData.put("order", order);
        eventData.put("text_dict", text_dict);

        // 发包
        baseAPI.notifyToClient(player, "Xigua_common", "main", "SetScoreboard", eventData);
    }


    /* ===============================================
     *  ② Bukkit 传统渲染器
     * =============================================== */
    private void sendViaBukkit(Player player, String sbName, String title, List<String> lines, Map<UUID, Scoreboard> cache, ScoreboardContext context) {

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        UUID playerId = player.getUniqueId();
        Scoreboard board = cache.getOrDefault(playerId, manager.getNewScoreboard());
        Objective obj = board.getObjective(sbName);

        if (obj == null) {
            obj = board.registerNewObjective(sbName, Criteria.DUMMY,
                    ChatColor.translateAlternateColorCodes('&', title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            board.getEntries().forEach(board::resetScores);
        }

        for (int i = 0; i < lines.size(); i++) {
            String text = replaceCommon(player, lines.get(i), context);
            obj.getScore(text).setScore(lines.size() - i);
        }

        player.setScoreboard(board);
        cache.put(playerId, board);
    }


    /* ===============================================
     *       更新等待中的计分板
     * =============================================== */

    public void updateWaitingBoard(Player p) {
        ScoreboardContext context = createContext();
        if (useBaseAPI)
            sendViaBaseAPI(p, waitingTitle, waitingLines, context);
        else
            sendViaBukkit(p, "waiting", waitingTitle, waitingLines, waitingBoards, context);
    }

    public void updateGameBoard(Player p) {
        ScoreboardContext context = createContext();
        if (useBaseAPI)
            sendViaBaseAPI(p, gameTitle, gameLines, context);
        else
            sendViaBukkit(p, "game", gameTitle, gameLines, gameBoards, context);
    }

    public void updateFinalBattleBoard(Player p) {
        ScoreboardContext context = createContext();
        if (useBaseAPI)
            sendViaBaseAPI(p, finalBattleTitle, finalBattleLines, context);
        else
            sendViaBukkit(p, "final_battle", finalBattleTitle, finalBattleLines, finalBattleBoards, context);
    }

    private ScoreboardContext createContext() {
        return new ScoreboardContext(
                Bukkit.getOnlinePlayers().size(),
                plugin.getHunters().size(),
                plugin.getEscapers().size()
        );
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


    public void setDragonHealth(double dragonHealth) {
        this.dragonHealth = dragonHealth;
    }

    public void setFinalBattleStartTime(long time) {
        this.finalBattleStartTime = time;
    }
}

