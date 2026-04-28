package com.huntergame.listener;

import com.huntergame.HunterGame;
import com.huntergame.InactivityDetection;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class ChatActivityListener implements Listener {

    private static final String[] CHAT_FORMAT_PRIORITY = {"admin", "mvp", "vip", "default"};

    private final HunterGame plugin;
    private final InactivityDetection inactivityDetection;

    public ChatActivityListener(HunterGame plugin, InactivityDetection inactivityDetection) {
        this.plugin = plugin;
        this.inactivityDetection = inactivityDetection;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("formats.enable", false)) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        inactivityDetection.updateActivity(player);

        String format = resolveChatFormat(player);
        format = format.replace("%player%", player.getDisplayName()).replace("%message%", event.getMessage());

        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        format = ChatColor.translateAlternateColorCodes('&', format);
        event.setCancelled(true);

        if (!plugin.isGameRunning()) {
            broadcastToAll(format);
            return;
        }

        sendTeamChat(player, playerId, format);
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

    private void sendTeamChat(Player sender, UUID senderId, String message) {
        boolean senderIsHunter = plugin.isHunter(senderId);
        boolean senderIsEscaper = plugin.isEscaper(senderId);
        boolean senderIsSpectator = sender.getGameMode() == GameMode.SPECTATOR;

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            UUID recipientId = recipient.getUniqueId();
            boolean recipientIsSpectator = recipient.getGameMode() == GameMode.SPECTATOR;

            if (senderIsSpectator && recipientIsSpectator) {
                recipient.sendMessage(message);
            } else if (senderIsHunter && (plugin.isHunter(recipientId) || recipientIsSpectator)) {
                recipient.sendMessage(message);
            } else if (senderIsEscaper && (plugin.isEscaper(recipientId) || recipientIsSpectator)) {
                recipient.sendMessage(message);
            }
        }
    }
}
