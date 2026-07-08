package com.huntergame.reward;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SettlementRewardService {
    private static final String ROOT = "settlement_rewards";

    private final HunterGame plugin;
    private final Set<UUID> rewardedPlayers = new HashSet<>();

    public SettlementRewardService(HunterGame plugin) {
        this.plugin = plugin;
    }
    public void resetForGame() {
        rewardedPlayers.clear();
    }
    public boolean rewardHunter(Player player, boolean win) {
        return reward(player, "hunter", win);
    }

    public boolean rewardEscaper(Player player, boolean win) {
        return reward(player, "escaper", win);
    }

    public boolean hasRewarded(UUID uuid) {
        return rewardedPlayers.contains(uuid);
    }

    private boolean reward(Player player, String role, boolean win) {
        if (player == null || !player.isOnline() || !plugin.getConfig().getBoolean(ROOT + ".enabled", true)) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (!isEligible(uuid, role) || !rewardedPlayers.add(uuid)) {
            return false;
        }

        RewardStats stats = readStats(uuid);
        RewardValues current = calculate(role, stats, win);
        double proficiency = calculateProficiency(player, role, stats, win);
        Map<String, String> placeholders = buildPlaceholders(player, role, win, stats, current, proficiency);

        dispatchConfiguredCommands(player, role, win, placeholders);
        sendRewardMessage(player, placeholders, current);
        addGamePlayed(player);
        applyProficiency(player, proficiency);
        return true;
    }

    private boolean isEligible(UUID uuid, String role) {
        if ("hunter".equals(role)) {
            return plugin.isHunter(uuid);
        }
        return plugin.isEscaper(uuid) || plugin.isDeathescapers(uuid);
    }

    private RewardStats readStats(UUID uuid) {
        RewardStats stats = new RewardStats();
        if (plugin.getGameSettlement() == null) {
            return stats;
        }

        stats.kills = plugin.getGameSettlement().getKillsMap().getOrDefault(uuid, 0);
        stats.damage = plugin.getGameSettlement().getDamageMap().getOrDefault(uuid, 0.0);
        stats.deaths = plugin.getGameSettlement().getDeathsMap().getOrDefault(uuid, 0);
        stats.minutes = Math.max(0.0, plugin.getElapsedSeconds() / 60.0);
        return stats;
    }

    private RewardValues calculate(String role, RewardStats stats, boolean win) {
        String base = ROOT + ".formulas." + role;
        double money = stats.kills * getDouble(base + ".money.kill", 0.0)
                + stats.damage * getDouble(base + ".money.damage", 0.0)
                + stats.deaths * getDouble(base + ".money.death", 0.0)
                + stats.minutes * getDouble(base + ".money.playtime_minute", 0.0)
                + getDouble(base + ".money." + (win ? "win" : "fail"), 0.0);

        double exp = stats.kills * getDouble(base + ".exp.kill", 0.0)
                + stats.damage * getDouble(base + ".exp.damage", 0.0)
                + stats.deaths * getDouble(base + ".exp.death", 0.0)
                + stats.minutes * getDouble(base + ".exp.playtime_minute", 0.0)
                + getDouble(base + ".exp." + (win ? "win" : "fail"), 0.0);

        return new RewardValues(
                Math.max(0L, Math.round(money)),
                Math.max(0L, Math.round(exp))
        );
    }

    private double calculateProficiency(Player player, String role, RewardStats stats, boolean win) {
        if (plugin.getRankManager() == null) {
            return 0.0;
        }
        return plugin.getRankManager().calculateSettlementProficiency(
                player,
                role,
                stats.kills,
                stats.damage,
                stats.deaths,
                win
        );
    }

    private Map<String, String> buildPlaceholders(Player player, String role, boolean win, RewardStats stats, RewardValues current, double proficiency) {
        Map<String, String> placeholders = new HashMap<>();
        putPlaceholder(placeholders, "player", player.getName());
        putPlaceholder(placeholders, "role", role);
        putPlaceholder(placeholders, "result", win ? "胜利" : "失败");
        putPlaceholder(placeholders, "kills", String.valueOf(stats.kills));
        putPlaceholder(placeholders, "damage", formatDecimal(stats.damage));
        putPlaceholder(placeholders, "deaths", String.valueOf(stats.deaths));
        putPlaceholder(placeholders, "minutes", formatDecimal(stats.minutes));
        putPlaceholder(placeholders, "money", String.valueOf(current.money));
        putPlaceholder(placeholders, "exp", String.valueOf(current.exp));
        putPlaceholder(placeholders, "proficiency", formatDecimal(proficiency));
        putPlaceholder(placeholders, "hunter_money", "hunter".equals(role) ? String.valueOf(current.money) : "0");
        putPlaceholder(placeholders, "hunter_exp", "hunter".equals(role) ? String.valueOf(current.exp) : "0");
        putPlaceholder(placeholders, "escaper_money", "escaper".equals(role) ? String.valueOf(current.money) : "0");
        putPlaceholder(placeholders, "escaper_exp", "escaper".equals(role) ? String.valueOf(current.exp) : "0");
        return placeholders;
    }

    private void dispatchConfiguredCommands(Player player, String role, boolean win, Map<String, String> placeholders) {
        List<String> commands = plugin.getConfig().getStringList(ROOT + ".commands." + role + "_" + (win ? "win" : "fail"));
        for (String rawCommand : commands) {
            String command = applyPlaceholders(rawCommand, placeholders).trim();
            if (command.isEmpty()) {
                continue;
            }
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private void sendRewardMessage(Player player, Map<String, String> placeholders, RewardValues current) {
        if (!plugin.getConfig().getBoolean(ROOT + ".message.enabled", true)) {
            return;
        }

        String rewards = "游戏币 " + current.money + "，大厅经验 " + current.exp + "，熟练度 " + placeholders.getOrDefault("%proficiency%", "0");
        putPlaceholder(placeholders, "rewards", rewards);

        for (String line : getRewardMessageLines()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', applyPlaceholders(line, placeholders)));
        }
    }

    private List<String> getRewardMessageLines() {
        String path = ROOT + ".message.text";
        if (plugin.getConfig().isList(path)) {
            return plugin.getConfig().getStringList(path);
        }

        String text = plugin.getConfig().getString(path, "&a结算奖励: &f%rewards%");
        return Collections.singletonList(text);
    }

    private void applyProficiency(Player player, double proficiency) {
        if (plugin.getDataStorageManager() == null) {
            return;
        }
        if (Math.abs(proficiency) < 0.0001) {
            return;
        }
        plugin.getDataStorageManager().addProficiency(player, proficiency);
    }

    private void addGamePlayed(Player player) {
        if (plugin.getDataStorageManager() == null) {
            return;
        }
        plugin.getDataStorageManager().addGamePlayed(player.getUniqueId(), player);
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private void putPlaceholder(Map<String, String> placeholders, String key, String value) {
        placeholders.put("%" + key + "%", value);
        placeholders.put("{" + key + "}", value);
    }

    private double getDouble(String path, double fallback) {
        return plugin.getConfig().getDouble(path, fallback);
    }

    private String formatDecimal(double value) {
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class RewardStats {
        int kills;
        double damage;
        int deaths;
        double minutes;
    }

    private static final class RewardValues {
        final long money;
        final long exp;

        RewardValues(long money, long exp) {
            this.money = money;
            this.exp = exp;
        }
    }
}

