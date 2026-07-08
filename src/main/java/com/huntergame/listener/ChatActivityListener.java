package com.huntergame.listener;

import com.huntergame.HunterGame;
import com.huntergame.listener.InactivityMonitor;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.IllegalFormatException;
import java.util.UUID;

public class ChatActivityListener implements Listener {

    private static final String[] CHAT_FORMAT_PRIORITY = {"admin", "mvp", "vip", "default"};

    private final HunterGame plugin;
    private final InactivityMonitor inactivityDetection;

    public ChatActivityListener(HunterGame plugin, InactivityMonitor inactivityDetection) {
        this.plugin = plugin;
        this.inactivityDetection = inactivityDetection;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        inactivityDetection.updateActivity(player);

        boolean formatsEnabled = plugin.getConfig().getBoolean("formats.enable", false);
        if (!formatsEnabled && !plugin.isGameRunning()) {
            return;
        }

        String message = formatsEnabled
                ? formatConfiguredMessage(player, event.getMessage())
                : formatVanillaMessage(event, player);

        event.setCancelled(true);
        if (!plugin.isGameRunning()) {
            broadcastToAll(message);
            return;
        }

        sendTeamChat(playerId, message);
    }

    private String formatConfiguredMessage(Player player, String rawMessage) {
        String format = resolveChatFormat(player);
        format = format.replace("%player%", player.getDisplayName()).replace("%message%", rawMessage);

        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        return ChatColor.translateAlternateColorCodes('&', format);
    }

    private String formatVanillaMessage(AsyncPlayerChatEvent event, Player player) {
        try {
            return String.format(event.getFormat(), player.getDisplayName(), event.getMessage());
        } catch (IllegalFormatException e) {
            return "<" + player.getDisplayName() + "> " + event.getMessage();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || (event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ())) {
            return;
        }
        inactivityDetection.updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        inactivityDetection.updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        inactivityDetection.updateActivity(event.getPlayer());
    }

    private String resolveChatFormat(Player player) {
        for (String key : CHAT_FORMAT_PRIORITY) {
            String path = "formats." + key;
            if (!plugin.getConfig().contains(path)) {
                continue;
            }

            String permission = plugin.getConfig().getString(path + ".permission");
            if (permission == null || permission.isEmpty() || player.hasPermission(permission)) {
                String format = plugin.getConfig().getString(path + ".format");
                if (format != null && !format.isEmpty()) {
                    return format;
                }
            }
        }
        return "&7[%huntergame_proficiency%★]%huntergame_role% &f%player%: %message%";
    }

    private void broadcastToAll(String message) {
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(message);
        }
    }

    private void sendTeamChat(UUID senderId, String message) {
        ChatGroup senderGroup = getChatGroup(senderId);

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            UUID recipientId = recipient.getUniqueId();
            if (getChatGroup(recipientId) == senderGroup) {
                recipient.sendMessage(message);
            }
        }
    }

    private ChatGroup getChatGroup(UUID playerId) {
        if (plugin.isHunter(playerId)) {
            return ChatGroup.HUNTER;
        }
        if (plugin.isEscaper(playerId)) {
            return ChatGroup.ESCAPER;
        }
        return ChatGroup.SPECTATOR;
    }

    private enum ChatGroup {
        HUNTER,
        ESCAPER,
        SPECTATOR
    }
}

